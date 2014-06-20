package mpi.ner;

import mpi.ner.normalizers.WhiteSpaceNormalizer;
import mpi.ner.taggers.StanfordNER;

public class NERFactory {

  public static NER createNER(NERTaggerName taggerName) {
    switch (taggerName) {
      case StanfordNER:
        return new StanfordNER();
      default:
        throw new UnsupportedOperationException("Unknown NER: " + taggerName);

    }
  }

  public static MentionNormalizer createMentionNormalizer(MentionNormalizerName normalizerName) {
    switch (normalizerName) {
      case WhiteSpaceNormalizer:
        return new WhiteSpaceNormalizer();
      default:
        throw new UnsupportedOperationException("Unknown Normalizer: " + normalizerName);
    }
  }

}
