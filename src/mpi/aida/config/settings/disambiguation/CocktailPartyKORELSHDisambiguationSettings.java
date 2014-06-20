package mpi.aida.config.settings.disambiguation;

import java.util.LinkedList;
import java.util.List;

import mpi.aida.access.DataAccess;
import mpi.aida.access.DataAccessSQL;
import mpi.aida.graph.similarity.exception.MissingSettingException;
import mpi.aida.graph.similarity.util.SimilaritySettings;

/**
 * Preconfigured settings for the {@see Disambiguator} using the mention-entity
 * prior, the keyphrase based similarity, and the KORE keyphrase based
 * entity coherence.
 */
public class CocktailPartyKORELSHDisambiguationSettings extends CocktailPartyDisambiguationSettings {
    
  private static final long serialVersionUID = 5867674989478781057L;

  public CocktailPartyKORELSHDisambiguationSettings() throws MissingSettingException {
    List<String[]> cohConfigs = new LinkedList<String[]>();
    cohConfigs.add(new String[] { "KORELSHEntityEntitySimilarity", "1.0" });

    SimilaritySettings switchedKPsettings = new SimilaritySettings(
        LocalDisambiguationSettings.getSimConfigs(), cohConfigs, 
        LocalDisambiguationSettings.priorWeight);
    switchedKPsettings.setIdentifier("SwitchedKP");
    switchedKPsettings.setPriorThreshold(0.9);
    switchedKPsettings.setEntityCohKeyphraseAlpha(1.0);
    switchedKPsettings.setEntityCohKeywordAlpha(0.0);
    switchedKPsettings.setShouldNormalizeCoherenceWeights(true);
    List<String[]> sourceWeightConfigs = new LinkedList<String[]>();
    sourceWeightConfigs.add(new String[] { DataAccess.KPSOURCE_INLINKTITLE, "0.0" });
    switchedKPsettings.setEntityEntityKeyphraseSourceWeights(sourceWeightConfigs);
    switchedKPsettings.setLshBandSize(2);
    switchedKPsettings.setLshBandCount(100);
    switchedKPsettings.setLshDatabaseTable(DataAccessSQL.ENTITY_LSH_SIGNATURES);
    setSimilaritySettings(switchedKPsettings);      
  }
}