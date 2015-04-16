package mpi.aida.config.settings.disambiguation;

import mpi.aida.graph.similarity.exception.MissingSettingException;

/**
 * Preconfigured settings for the {@see Disambiguator} using only the 
 * keyphrase based similarity based only on idf counts.
 * 
 * Also does thresholding to determine out-of-knowledge-base / null entities.
 */
public class LocalKeyphraseIDFBasedDisambiguationIDFWithNullSettings extends LocalKeyphraseIDFBasedDisambiguationIDFSettings {

  private static final long serialVersionUID = -8458216249693970790L;

  public LocalKeyphraseIDFBasedDisambiguationIDFWithNullSettings() throws MissingSettingException, NoSuchMethodException, ClassNotFoundException {
    super();
    setComputeConfidence(true);
    setNullMappingThreshold(0.05);
  }
}
