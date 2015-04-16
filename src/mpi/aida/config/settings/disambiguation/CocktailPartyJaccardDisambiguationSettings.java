package mpi.aida.config.settings.disambiguation;

import java.util.LinkedList;
import java.util.List;

import mpi.aida.graph.similarity.exception.MissingSettingException;
import mpi.aida.graph.similarity.util.SimilaritySettings;


public class CocktailPartyJaccardDisambiguationSettings extends
    CocktailPartyDisambiguationSettings {

  private static final long serialVersionUID = 6766852737067667775L;

  public CocktailPartyJaccardDisambiguationSettings()
    throws MissingSettingException, NoSuchMethodException, ClassNotFoundException {
    super();
    List<String[]> cohConfigs = new LinkedList<String[]>();
    cohConfigs.add(new String[] { "InlinkOverlapEntityEntitySimilarity", "1.0" });

    SimilaritySettings switchedKPsettings = 
        new SimilaritySettings(
            LocalKeyphraseBasedDisambiguationSettings.getKeyphraseSimConfigsWithPrior(),
            cohConfigs, LocalKeyphraseBasedDisambiguationSettings.getKeyphraseSimPriorWeight());
    switchedKPsettings.setMentionEntitySimilaritiesNoPrior(LocalKeyphraseBasedDisambiguationSettings.getKeyphraseSimConfigsNoPrior());
    switchedKPsettings.setIdentifier("SwitchedKP");
    switchedKPsettings.setPriorThreshold(0.9);
    setSimilaritySettings(switchedKPsettings);
  }  
}
