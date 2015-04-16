package mpi.aida.config.settings.disambiguation;

import java.util.Arrays;
import java.util.List;

import mpi.aida.config.settings.DisambiguationSettings;
import mpi.aida.config.settings.Settings.TECHNIQUE;
import mpi.aida.graph.similarity.exception.MissingSettingException;
import mpi.aida.graph.similarity.util.SimilaritySettings;

/**
 * Preconfigured settings for the {@see Disambiguator} using only the 
 * mention-entity prior and the keyphrase based similarity.
 */
public class LocalKeyphraseBasedDisambiguationSettings extends DisambiguationSettings {

  private static final long serialVersionUID = -1943862223862927646L;

  public LocalKeyphraseBasedDisambiguationSettings() throws MissingSettingException, NoSuchMethodException, ClassNotFoundException {
    setDisambiguationTechnique(TECHNIQUE.LOCAL);
   
    SimilaritySettings switchedKPsettings = new SimilaritySettings(getKeyphraseSimConfigsWithPrior(), null, getKeyphraseSimPriorWeight());
    switchedKPsettings.setIdentifier("SwitchedKP");
    switchedKPsettings.setPriorThreshold(0.9);
    switchedKPsettings.setMentionEntitySimilaritiesNoPrior(getKeyphraseSimConfigsNoPrior());
    setSimilaritySettings(switchedKPsettings);
  }
  
  public static List<SimilaritySettings.MentionEntitySimilarityRaw> getKeyphraseSimConfigsWithPrior() throws NoSuchMethodException, ClassNotFoundException {
    return Arrays.asList(new SimilaritySettings.MentionEntitySimilarityRaw("UnnormalizedKeyphrasesBasedMISimilarity", "KeyphrasesContext", 0.10123683065016278, false),
      new SimilaritySettings.MentionEntitySimilarityRaw("UnnormalizedKeyphrasesBasedIDFSimilarity", "KeyphrasesContext", 0.33359024871241655, false));
  }
  
  public static List<SimilaritySettings.MentionEntitySimilarityRaw> getKeyphraseSimConfigsNoPrior() throws NoSuchMethodException, ClassNotFoundException {
    return Arrays.asList(new SimilaritySettings.MentionEntitySimilarityRaw("UnnormalizedKeyphrasesBasedMISimilarity", "KeyphrasesContext", 0.5813210333782227, false),
      new SimilaritySettings.MentionEntitySimilarityRaw("UnnormalizedKeyphrasesBasedIDFSimilarity", "KeyphrasesContext", 0.4186789666217773, false));
  }

  public static Double getKeyphraseSimPriorWeight() {
    return 0.5651729206374206;
  }
}
