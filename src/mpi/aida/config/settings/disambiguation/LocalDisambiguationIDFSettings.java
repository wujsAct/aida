package mpi.aida.config.settings.disambiguation;

import java.util.LinkedList;
import java.util.List;

import mpi.aida.config.settings.DisambiguationSettings;
import mpi.aida.config.settings.Settings.TECHNIQUE;
import mpi.aida.graph.similarity.exception.MissingSettingException;
import mpi.aida.graph.similarity.util.SimilaritySettings;
import mpi.aida.graph.similarity.util.SimilaritySettings.ImportanceAggregationStrategy;

/**
 * Preconfigured settings for the {@see Disambiguator} using only the 
 * keyphrase based similarity based only on idf counts.
 */
public class LocalDisambiguationIDFSettings extends DisambiguationSettings {

  private static final long serialVersionUID = -6391627336407534940L;

  public LocalDisambiguationIDFSettings() throws MissingSettingException {
    setDisambiguationTechnique(TECHNIQUE.LOCAL);  
    
    List<String[]> simConfigs = new LinkedList<String[]>();
    simConfigs.add(new String[] { "UnnormalizedKeyphrasesBasedIDFSimilarity", "KeyphrasesContext", "0.5" });   
    
    List<String[]> eisConfigs = new LinkedList<String[]>();
    //eisConfigs.add(new String[] { "AidaEntityImportance", "0.5" });
    eisConfigs.add(new String[] { "GNDTitleDataCountBasedImportance", "0.16" });
    eisConfigs.add(new String[] { "GNDTripleCountBasedImportance", "0.17" });
    eisConfigs.add(new String[] { "YagoOutlinkCountBasedImportance", "0.17" });
    
    
    SimilaritySettings localIDFPsettings = new SimilaritySettings(simConfigs, null, eisConfigs, 0);
    localIDFPsettings.setIdentifier("LocalIDF");
    localIDFPsettings.setImportanceAggregationStrategy(ImportanceAggregationStrategy.AVERGAE);
    setSimilaritySettings(localIDFPsettings);
  }
}
