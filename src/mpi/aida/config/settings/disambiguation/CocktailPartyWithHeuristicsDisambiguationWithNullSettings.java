package mpi.aida.config.settings.disambiguation;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import mpi.aida.graph.similarity.exception.MissingSettingException;
import mpi.aida.graph.similarity.util.SimilaritySettings;
import mpi.aida.util.ClassPathUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Preconfigured settings for the {@see Disambiguator} using the mention-entity
 * prior, the keyphrase based similarity, and the MilneWitten Wikipedia link
 * based entity coherence.
 * 
 * It applies additional heuristics to fix entities before the graph algorithm
 * is run, and a threshold to assign Entity.OOKBE to null mentions.
 * 
 * Use this for running on "real world" documents to get the best results. 
 */
public class CocktailPartyWithHeuristicsDisambiguationWithNullSettings extends CocktailPartyDisambiguationWithNullSettings {
    
  private static final long serialVersionUID = 5867674989478781057L;
  
  private Logger logger_ = LoggerFactory.getLogger(CocktailPartyWithHeuristicsDisambiguationWithNullSettings.class);
  
  public CocktailPartyWithHeuristicsDisambiguationWithNullSettings() throws MissingSettingException {
    super();
    setComputeConfidence(true);
    
    //HACK FOR ERD CHALLENGE
    String erdSettingsFile = "erd.properties";
    Properties prop = null;
    try {
      prop = ClassPathUtils.getPropertiesFromClasspath(erdSettingsFile);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
    
    getGraphSettings().setUseCoherenceRobustnessTest(Boolean.parseBoolean(prop.getProperty("UseCoherenceRobustnessTest", "true")));
    getGraphSettings().setCohRobustnessThreshold(Double.parseDouble(prop.getProperty("CohRobustnessThreshold", "1.15")));
    getGraphSettings().setUseEasyMentionsTest(Boolean.parseBoolean(prop.getProperty("UseEasyMentionsTest", "true")));
    getGraphSettings().setEasyMentionsTestThreshold(Integer.parseInt(prop.getProperty("EasyMentionsTestThreshold", "5")));
    getGraphSettings().setUseConfidenceThresholdTest(Boolean.parseBoolean(prop.getProperty("UseConfidenceThresholdTest", "true")));
    getGraphSettings().setConfidenceTestThreshold(Double.parseDouble(prop.getProperty("ConfidenceTestThreshold", "0.9")));
    getGraphSettings().setPruneCandidateEntities(Boolean.parseBoolean(prop.getProperty("PruneCandidateEntities", "true")));
    getGraphSettings().setPruneCandidateThreshold(Integer.parseInt(prop.getProperty("PruneCandidateThreshold", "25")));
    
    setNullMappingThreshold(Double.parseDouble(prop.getProperty("NullMappingThreshold", "0.075")));
    
    String simSettingFile =  prop.getProperty("SimilaritySettingFilePath", null);
    if(simSettingFile != null) {
      logger_.info("Overriding simSettings from file: " + simSettingFile);
      setSimilaritySettings(new SimilaritySettings(new File(simSettingFile)));
    }
    
  }
}