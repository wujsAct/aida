package mpi.aida.config.settings.disambiguation;

import java.util.LinkedList;
import java.util.List;

import mpi.aida.access.DataAccess;
import mpi.aida.graph.similarity.exception.MissingSettingException;
import mpi.aida.graph.similarity.util.SimilaritySettings;
import mpi.aida.graph.similarity.util.SimilaritySettings.ImportanceAggregationStrategy;

/**
 * Preconfigured settings for the {@see Disambiguator} using the mention-entity
 * the keyphrase based similarity using idf scores only, and the KORE keyphrase based
 * entity coherence.
 */
public class CocktailPartyKOREIDFDisambiguationSettings extends CocktailPartyDisambiguationSettings {
    
  private static final long serialVersionUID = 5867674989478781057L;
 
  public CocktailPartyKOREIDFDisambiguationSettings() throws MissingSettingException {
    super();
    getGraphSettings().setUseCoherenceRobustnessTest(false);
    
    List<String[]> simConfigs = new LinkedList<String[]>();
    simConfigs.add(new String[] { "UnnormalizedKeyphrasesBasedIDFSimilarity", "KeyphrasesContext", "0.5" });  
    List<String[]> cohConfigs = new LinkedList<String[]>();
    cohConfigs.add(new String[] { "KOREEntityEntitySimilarity", "1.0" });
    List<String[]> eisConfigs = new LinkedList<String[]>();
    //eisConfigs.add(new String[] { "AidaEntityImportance", "0.5" });
    eisConfigs.add(new String[] { "GNDTitleDataCountBasedImportance", "0.16" });
    eisConfigs.add(new String[] { "GNDTripleCountBasedImportance", "0.17" });
    eisConfigs.add(new String[] { "YagoOutlinkCountBasedImportance", "0.17" });
        
    SimilaritySettings settings = new SimilaritySettings(simConfigs, cohConfigs, eisConfigs, 0.0);
    settings.setIdentifier("idf-sims");
    settings.setEntityCohKeyphraseAlpha(0.0);
    settings.setEntityCohKeywordAlpha(0.0);
    settings.setShouldNormalizeCoherenceWeights(true);
    settings.setImportanceAggregationStrategy(ImportanceAggregationStrategy.AVERGAE);
    List<String[]> sourceWeightConfigs = new LinkedList<String[]>();
    sourceWeightConfigs.add(new String[] { DataAccess.KPSOURCE_INLINKTITLE, "0.0" });
    settings.setEntityEntityKeyphraseSourceWeights(sourceWeightConfigs);
    setSimilaritySettings(settings);
  }
}