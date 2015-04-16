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
public class LocalKeyphraseIDFBasedDisambiguationIDFSettings extends DisambiguationSettings {

  private static final long serialVersionUID = -6391627336407534940L;

  public LocalKeyphraseIDFBasedDisambiguationIDFSettings() throws MissingSettingException, NoSuchMethodException, ClassNotFoundException {
    setDisambiguationTechnique(TECHNIQUE.LOCAL);  
    
    List<SimilaritySettings.MentionEntitySimilarityRaw> simConfigs = new LinkedList<>();
    simConfigs.add(new SimilaritySettings.MentionEntitySimilarityRaw("UnnormalizedKeyphrasesBasedIDFSimilarity", "KeyphrasesContext", 0.5, false));   
    
    List<SimilaritySettings.EntityImportancesRaw> eisConfigs = new LinkedList<>();
//    eisConfigs.add(new SimilaritySettings.EntityImportancesRaw("AidaEntityImportance", 0.5));
    eisConfigs.add(new SimilaritySettings.EntityImportancesRaw("GNDTitleDataCountBasedImportance", 0.16));
    eisConfigs.add(new SimilaritySettings.EntityImportancesRaw("GNDTripleCountBasedImportance", 0.17));
    eisConfigs.add(new SimilaritySettings.EntityImportancesRaw("YagoOutlinkCountBasedImportance", 0.17));
    
    
    SimilaritySettings localIDFPsettings = new SimilaritySettings(simConfigs, null, eisConfigs, 0);
    localIDFPsettings.setIdentifier("LocalIDF");
    localIDFPsettings.setImportanceAggregationStrategy(ImportanceAggregationStrategy.AVERGAE);
    setSimilaritySettings(localIDFPsettings);
  }
}
