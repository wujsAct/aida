package mpi.aida;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import mpi.aida.access.DataAccess;
import mpi.aida.config.settings.DisambiguationSettings;
import mpi.aida.config.settings.JsonSettings.JSONTYPE;
import mpi.aida.config.settings.PreparationSettings;
import mpi.aida.config.settings.disambiguation.CocktailPartyDisambiguationWithNullSettings;
import mpi.aida.config.settings.disambiguation.CocktailPartyKOREDisambiguationWithNullSettings;
import mpi.aida.config.settings.disambiguation.LocalDisambiguationIDFWithNullSettings;
import mpi.aida.config.settings.disambiguation.LocalDisambiguationWithNullSettings;
import mpi.aida.config.settings.disambiguation.PriorOnlyDisambiguationSettings;
import mpi.aida.config.settings.preparation.StanfordHybridPreparationSettings;
import mpi.aida.data.DisambiguationResults;
import mpi.aida.data.Entities;
import mpi.aida.data.EntityMetaData;
import mpi.aida.data.KBIdentifiedEntity;
import mpi.aida.data.PreparedInput;
import mpi.aida.data.ResultMention;
import mpi.aida.data.ResultProcessor;
import mpi.aida.util.htmloutput.HtmlGenerator;
import mpi.aida.util.timing.RunningTimer;
import mpi.tools.javatools.util.FileUtils;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

/**
 * Disambiguates a document from the command line.
 *
 */
public class CommandLineDisambiguator {
  
  private Options commandLineOptions;
  
  public static void main(String[] args) throws Exception {
    new CommandLineDisambiguator().run(args);
  }

  public void run(String args[]) throws Exception {
    commandLineOptions = buildCommandLineOptions();
    CommandLineParser parser = new PosixParser();
    CommandLine cmd = null;
    try {
      cmd = parser.parse(commandLineOptions, args); 
    } catch (MissingOptionException e) {
      System.out.println("\n\n" + e + "\n\n");
      printHelp(commandLineOptions);
    }
    if (cmd.hasOption("h")) {
      printHelp(commandLineOptions);
    }
    
    String disambiguationTechniqueSetting = "GRAPH";
    if (cmd.hasOption("t")) {
      disambiguationTechniqueSetting = cmd.getOptionValue("t");
    }
    String input = cmd.getOptionValue("i");
    File inputFile = new File(input); 
    List<File> files = new ArrayList<File>();
    if (cmd.hasOption("d")) {
      if (!inputFile.isDirectory()) {
        System.out.println("\n\nError: expected " + input + " to be a directory.");
        printHelp(commandLineOptions);
      }
      for (File f : FileUtils.getAllFiles(inputFile)) {
        // Ignore .html/.json files (assumed to be output files).
        if (!(f.getName().endsWith(".json") || f.getName().endsWith(".html"))) {
          files.add(f);          
        }
      }
    } else if (cmd.hasOption("s")) {
      String text = cmd.getOptionValue("i");
      int end = Math.min(text.length(), 8);
      File tmpFile = File.createTempFile(text.substring(0, end), ".txt", new File(System.getProperty("user.dir")));
      FileUtils.writeFileContent(tmpFile, text);
      tmpFile.deleteOnExit();
      files.add(tmpFile);
      
    } else {
      if (inputFile.isDirectory()) {
        System.out.println("\n\nError: expected " + input + " to be a file.");
        printHelp(commandLineOptions);
      }
      files.add(inputFile);
    }
    String outputFormat = "HTML";
    if (cmd.hasOption("o")) {
      outputFormat = cmd.getOptionValue("o");
    }
    String inputFormat = "PLAIN";
    if (cmd.hasOption("f")){
      inputFormat = cmd.getOptionValue("f");
    }
    PreparationSettings prepSettings = new StanfordHybridPreparationSettings();
    if (cmd.hasOption('m')) {
      int minCount = Integer.parseInt(cmd.getOptionValue('m'));
      prepSettings.setMinMentionOccurrenceCount(minCount);
      System.out.println("Dropping mentions with less than " + minCount + " occurrences in the text.");
    }

    int threadCount = 1;
    if (cmd.hasOption("c")) {
      threadCount = Integer.parseInt(cmd.getOptionValue("c"));
    }
    
    int chunkThreadCount = 1;
    if(cmd.hasOption("x")) {
      chunkThreadCount = Integer.parseInt(cmd.getOptionValue("x"));
    }
    
    int resultCount = 10;
    if (cmd.hasOption("e")) {
      resultCount = Integer.parseInt(cmd.getOptionValue("e"));
    }
    Double threshold = 0.0;
    if (cmd.hasOption("r")) {
      threshold = Double.parseDouble(cmd.getOptionValue("r"));
    }
    
    boolean isTimed = cmd.hasOption('z');        
    boolean runDummyDoc = cmd.hasOption('g');
    boolean writeTimingInfo = false;
    
    String timingDir = "";
    if(cmd.hasOption('w')) {
      writeTimingInfo = true;
      timingDir = cmd.getOptionValue("w");
      File f = new File(timingDir);
      if(!f.exists() || !f.isDirectory()) {
       System.out.println("Timing directory doesnt exists or not a directory!");
       printHelp(commandLineOptions);
      }
    }
        
    ExecutorService es = Executors.newFixedThreadPool(threadCount);        
    Preparator p = new Preparator();
    
    
    if(runDummyDoc) {
      // To eliminate the cache loading time effect on overall running timer results.      
      System.out.println("Executing disambiguation on dummy document..");

      // execute dummy disambiguation
      runDummyDisambiguation("A dummy text to disambigute.", 
          disambiguationTechniqueSetting, p, prepSettings, inputFormat, outputFormat, resultCount,
          false, false);
      
      RunningTimer.clear();
    }
     
    System.out.println("Processing " + files.size() + " documents with " +
        threadCount + " threads, ignoring existing .html and .json files.");        
    
    for (File f : files) {
      Processor proc = new Processor(f.getAbsolutePath(), 
          disambiguationTechniqueSetting, p, prepSettings, inputFormat, outputFormat, resultCount,
          !inputFile.isDirectory(), isTimed);   
      // pass the threshold
      proc.setThreshold(threshold);      
      proc.setChunkThreadCount(chunkThreadCount);
      es.execute(proc);
    }
    
    es.shutdown();
    es.awaitTermination(1, TimeUnit.DAYS);
    if(writeTimingInfo) {      
      if(es.isTerminated()) {           
        String content = RunningTimer.getDetailedOverview();
        FileUtils.writeFileContent(new File(timingDir + File.separator + "overall_timing_" + new SimpleDateFormat("yyyy_MM_dd_hh_mm_ss'.txt'").format(new Date())), content);
        content = RunningTimer.getTrackedDocumentTime();
        FileUtils.writeFileContent(new File(timingDir + File.separator + "document_timing_" + new SimpleDateFormat("yyyy_MM_dd_hh_mm_ss'.txt'").format(new Date())), content);
      }
    }
  } 

  
  private void runDummyDisambiguation(String content, String disambiguationTechniqueSetting, Preparator p, PreparationSettings prepSettings,
      String inputFormat, String outputFormat, int resultCount, boolean logResults, boolean isTimed) {
    PreparedInput input = null;
    String fName = "dummyText_" + System.currentTimeMillis() + ".txt";
    try {
      input = p.prepare(fName, content.toString(), prepSettings);
      
      DisambiguationSettings disSettings = null;
      if (disambiguationTechniqueSetting.equals("PRIOR")) {
        disSettings = new PriorOnlyDisambiguationSettings();
      } else if (disambiguationTechniqueSetting.equals("LOCAL")) {
        disSettings = new LocalDisambiguationWithNullSettings();
      } else if (disambiguationTechniqueSetting.equals("LOCAL-IDF")) {
        disSettings = new LocalDisambiguationIDFWithNullSettings();
      } else if (disambiguationTechniqueSetting.equals("GRAPH")) {
        disSettings = new CocktailPartyDisambiguationWithNullSettings();    
      } else if (disambiguationTechniqueSetting.equals("GRAPH-KORE")) {
        disSettings = new CocktailPartyKOREDisambiguationWithNullSettings();       
      } else {
        System.err
            .println("disambiguation-technique can be either: "
                + "'PRIOR', 'LOCAL', 'LOCAL-IDF', 'GRAPH', 'GRAPH-IDF' or 'GRAPH-KORE");
        System.exit(2);
      }

      disSettings.setNumChunkThreads(1);        
      Disambiguator d = new Disambiguator(input, disSettings);
      // ignoring the results
      d.disambiguate();
    }catch(Exception e) {
      System.err.println("Error while processing '" + fName + "': " + 
          e.getLocalizedMessage());
      e.printStackTrace();
    }
  }

  @SuppressWarnings("static-access")
  private Options buildCommandLineOptions() throws ParseException {
    Options options = new Options();
    options
        .addOption(OptionBuilder
            .withLongOpt("input")
            .withDescription(
                "Input, assumed to be a UTF-8 encoded text file. "
                + "Set -d to treat  the parameter as directory.")
            .hasArg()
            .withArgName("FILE")
            .isRequired()
            .create("i"));
    options
    .addOption(OptionBuilder
        .withLongOpt("string")
        .withDescription(
            "Set to treat the -i input as a string. Will directly disambiguate.")
        .create("s"));
    options
    .addOption(OptionBuilder
        .withLongOpt("directory")
        .withDescription(
            "Set to treat the -i input as directory. Will recursively process"
            + "all files in the directory.")
        .create("d"));
    options
        .addOption(OptionBuilder
            .withLongOpt("technique")
            .withDescription(
                "Set the disambiguation-technique to be used: PRIOR, LOCAL, LOCAL-IDF, GRAPH, GRAPH-IDF, or GRAPH-KORE. Default is GRAPH.")
            .hasArg()
            .withArgName("TECHNIQUE")
            .create("t"));
    options
    .addOption(OptionBuilder
        .withLongOpt("outputformat")
        .withDescription(
            "Set the output-format to be used: HTML, JSON, ALL. Default is HTML.")
        .hasArg()
        .withArgName("FORMAT")
        .create("o"));
    options
    .addOption(OptionBuilder
        .withLongOpt("inputformat")
        .withDescription(
            "Set the input-format to be used: PLAIN, XML-ALTO, XML-TEI. Default is PLAIN.")
        .hasArg()
        .withArgName("INPUTFORMAT")
        .create("f"));
    options
    .addOption(OptionBuilder
        .withLongOpt("threadcount")
        .withDescription(
            "Set the number of documents to be processed in parallel.")
        .hasArg()
        .withArgName("COUNT")
        .create("c"));    
    options
    .addOption(OptionBuilder
        .withLongOpt("numChunkThread")
        .withDescription("Set the maximum number of chunk disambiguation threads to be created.")
        .hasArg()
        .withArgName("NUMCHUNKTHREAD")
        .create("x"));
    options
    .addOption(OptionBuilder
        .withLongOpt("minmentioncount")
        .withDescription(
            "Set the minimum occurrence count of a mention to be considered for disambiguation. Default is 1.")
        .hasArg()
        .withArgName("COUNT")
        .create("m"));  
    options
    .addOption(OptionBuilder
        .withLongOpt("maximumresultspermention")
        .withDescription(
            "Set the number of entities returned per mention in the output. Default is 10.")
        .hasArg()
        .withArgName("COUNT")
        .create("e"));  
    options
    .addOption(OptionBuilder
        .withLongOpt("confidencethreshold")
        .withDescription(
            "Sets the confidence threshold below which to drop entities. Default is given by the technique.")
        .hasArg()
        .withArgName("THRESHOLD")
        .create("r"));
    options
    .addOption(OptionBuilder
        .withLongOpt("timing")
        .withDescription(
            "To Retrieve RunningTimer overview.")
        .create("z"));
    options
    .addOption(OptionBuilder
        .withLongOpt("ignore-cacheloadtime")
        .withDescription(
            "Run disambiguation on dummy document to ignore the cache loading time. Disabled by default")
        .create("g"));
    options
    .addOption(OptionBuilder
        .withLongOpt("writetime")
        .withDescription(
            "To write RunningTimer overview details to file.")
        .hasArg()
        .withArgName("DIRECTORYNAME")
        .create("w"));
    options.addOption(OptionBuilder.withLongOpt("help").create('h'));
    return options;
  }
  
  private void printHelp(Options commandLineOptions) {
    String header = "\n\nRun AIDA on a UTF-8 encoded input:\n\n";
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp("CommandLineDisambiguator", header, 
        commandLineOptions, "", true);
    System.exit(0);
  }
  
  class Processor implements Runnable {
    private String inputFile; 
    private String disambiguationTechniqueSetting; 
    private Preparator p; 
    private PreparationSettings prepSettings;
    private String inputFormat;
    private String outputFormat;
    private int resultCount;
    private int numChunkThread;
    private boolean logResults;
    private double threshold;
    private boolean isTimed;
    
    public Processor(String inputFile, String disambiguationTechniqueSetting,
        Preparator p, PreparationSettings prepSettings, String inputFormat, String outputFormat,
        int resultCount, boolean logResults, boolean isTimed) {
      super();
      this.inputFile = inputFile;
      this.disambiguationTechniqueSetting = disambiguationTechniqueSetting;
      this.p = p;
      this.prepSettings = prepSettings;
      this.inputFormat = inputFormat;
      this.outputFormat = outputFormat;
      this.resultCount = resultCount;
      this.logResults = logResults;
      this.isTimed = isTimed;
    }    
    
    public void setChunkThreadCount(int numChunkThread) {
      this.numChunkThread = numChunkThread;
    }

    public void setThreshold(double threshold) {
      this.threshold = threshold;
    }
    
    @Override
    public void run() {
      try {
        
        /*
         * if timing is enabled, then use real time tracker for detailed module level info
         * else use No op tracker for getting only the total time
         */
        if(isTimed) {
          System.out.println("Timing info requested. Enabling Real Time Tracker.");
          RunningTimer.enableRealTimeTracker();
        }
        
        if (outputFormat.equals("JSON") || outputFormat.equals("ALL")) {
          File resultFile = new File(inputFile + ".json");
          if (resultFile.exists()) {
            System.out.println("JSON output for " + inputFile + " exists, skipping.");
          return;
          }
        }
        if (outputFormat.equals("HTML") || outputFormat.equals("ALL")) {
          File resultFile = new File(inputFile + ".html");
          if (resultFile.exists()) {
            System.out.println("HTML output for " + inputFile + " exists, skipping.");
          return;
          }
        }
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(
            new FileInputStream(inputFile), "UTF-8"));
        StringBuilder content = new StringBuilder();

        for (String line = reader.readLine(); line != null; line = reader
            .readLine()) {
          content.append(line).append('\n');
        }
        reader.close();
        
        PreparedInput input = null;
        if(inputFormat.startsWith("XML")){
          XmlPreparator xmlPrep = new XmlPreparator();
          if(inputFormat.endsWith("ALTO")){
            input = xmlPrep.prepareAltoXml(content.toString(), inputFile, prepSettings);
          }else{
            input = xmlPrep.prepareTeiXml(content.toString(), inputFile, prepSettings);
          }
          
        }else{
          input = p.prepare(inputFile, content.toString(), prepSettings);
        }
        DisambiguationSettings disSettings = null;
        if (disambiguationTechniqueSetting.equals("PRIOR")) {
          disSettings = new PriorOnlyDisambiguationSettings();
        } else if (disambiguationTechniqueSetting.equals("LOCAL")) {
          disSettings = new LocalDisambiguationWithNullSettings();
        } else if (disambiguationTechniqueSetting.equals("LOCAL-IDF")) {
          disSettings = new LocalDisambiguationIDFWithNullSettings();
        } else if (disambiguationTechniqueSetting.equals("GRAPH")) {
          disSettings = new CocktailPartyDisambiguationWithNullSettings();
        } else if (disambiguationTechniqueSetting.equals("GRAPH-KORE")) {
          disSettings = new CocktailPartyKOREDisambiguationWithNullSettings();       
        } else {
          System.err
              .println("disambiguation-technique can be either: "
                  + "'PRIOR', 'LOCAL', 'LOCAL-IDF', 'GRAPH', 'GRAPH-IDF' or 'GRAPH-KORE");
          System.exit(2);
        }
       
        if (threshold > 0.0) {
          disSettings.setNullMappingThreshold(threshold);
        }
        
        disSettings.setNumChunkThreads(numChunkThread);        
        Disambiguator d = new Disambiguator(input, disSettings);
        DisambiguationResults results = d.disambiguate();
        if (logResults) {
          System.out.println("Disambiguation for '" + inputFile + "' done.");
        }
        
        // retrieve JSON representation of Disambiguated results
        ResultProcessor rp = new ResultProcessor(content.toString(), results,
            inputFile, input, resultCount);
        //String jsonStr = rp.process(false);
        String jsonStr = rp.process(JSONTYPE.EXTENDED).toJSONString();
        if (!(outputFormat.equals("JSON") || outputFormat.equals("HTML") || outputFormat.equals("ALL"))) {
          System.out.println("Unrecognized output format.");
          printHelp(commandLineOptions);
        }
        
        if (outputFormat.equals("JSON") || outputFormat.equals("ALL")) {
          File resultFile = new File(inputFile + ".json");
          FileUtils.writeFileContent(resultFile, jsonStr);
          if (logResults) {
            System.out.println("Result written to '" + resultFile + "' in " + outputFormat);
          }
        }
        
        if (outputFormat.equals("HTML") || outputFormat.equals("ALL")) {
          File resultFile = new File(inputFile + ".html");
          // generate HTML from Disambiguated Results
          HtmlGenerator gen = new HtmlGenerator(jsonStr, inputFile);
          FileUtils.writeFileContent(resultFile, gen.constructFromJson(jsonStr));
          if (logResults) {
            System.out.println("Result written to '" + resultFile + "' in " + outputFormat);
          }
        }
        
        if (logResults) {         
          System.out.println("Mentions and Entities found:");
          System.out.println("\tMention\tEntity_id\tEntity\tEntity Name\tURL");
  
          Set<KBIdentifiedEntity> entities = new HashSet<KBIdentifiedEntity>();
          for (ResultMention rm : results.getResultMentions()) {
            entities.add(results.getBestEntity(rm).getKbEntity());
          }
          Map<KBIdentifiedEntity, EntityMetaData> entitiesMetaData = DataAccess
              .getEntitiesMetaData(entities);
          
          for (ResultMention rm : results.getResultMentions()) {
            KBIdentifiedEntity entity = results.getBestEntity(rm).getKbEntity();
            EntityMetaData entityMetaData = entitiesMetaData.get(entity);
  
            if (Entities.isOokbEntity(entity)) {
              System.out.println("\t" + rm + "\t NO MATCHING ENTITY");
            } else {
              System.out.println("\t" + rm + "\t" + entityMetaData.getId() + "\t"
                + entity + "\t" + entityMetaData.getHumanReadableRepresentation()
                + "\t" + entityMetaData.getUrl());
            }
          }  
        }

        // Prints Total time for default No-op tracker and detailed info for real time tracker
        System.out.println(RunningTimer.getDetailedOverview());        
      } catch (Exception e) {
        System.err.println("Error while processing '" + inputFile + "': " + 
            e.getLocalizedMessage());
        e.printStackTrace();
      }
    }    
  }
}
