package mpi.aida.preparation.mentionrecognition;

import java.io.Serializable;

import mpi.aida.config.settings.PreparationSettings;
import mpi.aida.config.settings.PreparationSettings.LANGUAGE;
import mpi.aida.data.Mentions;
import mpi.ner.NERManager;
import mpi.tokenizer.data.Tokenizer;
import mpi.tokenizer.data.TokenizerManager;
import mpi.tokenizer.data.Tokens;
import mpi.tools.javatools.datatypes.Pair;

public class FilterMentions implements Serializable {
 
  private static final long serialVersionUID = 6260499966421708963L;

  private ManualFilter manualFilter = null;

  private HybridFilter hybridFilter = null;
  
  public FilterMentions() {
    manualFilter = new ManualFilter();
    hybridFilter = new HybridFilter();
  }

  /** which type of tokens to get*/
  public static enum FilterType {
    STANFORD_NER, Manual, DICTIONARY, Manual_NER, ManualPOS, CASELESS_STANFORD_NER;
  };
  
  public Pair<Tokens, Mentions> filter(String docId, String text, FilterType by, boolean isHybrid, PreparationSettings.LANGUAGE language) {
    Mentions mentions = null;
    Mentions manualMentions = null;
    Tokens tokens = null;

    //manual case handled separately
    if (by.equals(FilterType.Manual) || by.equals(FilterType.ManualPOS) || by.equals(FilterType.Manual_NER)) {
      Pair<String, Mentions> filteredTextMentions = manualFilter.filter(text, by, language);
      mentions = filteredTextMentions.second();
      tokens = manualFilter.tokenize(filteredTextMentions.first(), by, mentions, language);
      return new Pair<Tokens, Mentions>(tokens, mentions);
    }
    
    //if hybrid mention detection, use manual filter to get the tokens
    //and then pass them the appropriate ner 
    if(isHybrid) {
      Pair<String, Mentions> filteredTextMentions = manualFilter.filter(text, by, language);
      tokens = manualFilter.tokenize(filteredTextMentions.first(), by, filteredTextMentions.second(), language);
      //Pair<Tokens, Mentions> tokensMentions = manualFilter.filter(text, by, language);
      manualMentions = filteredTextMentions.second();
      text = filteredTextMentions.first();
      //tokens = tokensMentions.first();
    } else { //otherwise tokenize normally
      Tokenizer.type type = buildTokenizerType(by, language);
      tokens = TokenizerManager.tokenize(text, type, false);
    }
    
    mentions = NERManager.singleton().findMentions(docId, text, tokens);
    
//    switch (by) {
//      case STANFORD_NER:
//      case CASELESS_STANFORD_NER:
//        mentions = namedEntityFilter.filter(tokens);
//        break;
//      case DICTIONARY:
//        try {
//          mentions = dictionaryFilter.filter(tokens);
//        } catch (IOException e) {
//          logger_.error("Could not use dictionary for NER.", e);
//        }
//        break;
//      default:
//        break;
//    }
    
    //if hybrid mention detection, merge both types mentions
    if(isHybrid) {
      mentions = hybridFilter.parse(manualMentions, mentions);
    }
   
    return new Pair<Tokens, Mentions>(tokens, mentions);
  }

  private mpi.tokenizer.data.Tokenizer.type buildTokenizerType(FilterType by, LANGUAGE language) {
    if (by == FilterType.STANFORD_NER) {
      switch (language) {
        case de:
          return Tokenizer.type.GERMAN_NER;
        case en:
          return Tokenizer.type.NER;
        default:
          break;
      }
    } else if (by == FilterType.DICTIONARY) {
      switch (language) {
        case de:
          return Tokenizer.type.GERMAN_TOKENS;
        case en:
          return Tokenizer.type.TOKENS;
        default:
          break;
      }
    } else if (by == FilterType.CASELESS_STANFORD_NER) {
      //only for English
      switch (language) {
        case en:
          return Tokenizer.type.ENGLISH_CASELESS_NER;
        default:
          break;
      }
    }
    return Tokenizer.type.NER;
  }
}