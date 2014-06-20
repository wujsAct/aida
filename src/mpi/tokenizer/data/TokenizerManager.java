package mpi.tokenizer.data;

import mpi.tokenizer.data.Tokenizer.type;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TokenizerManager {
  private Logger logger_ = LoggerFactory.getLogger(TokenizerManager.class);
  
  private static TokenizerManager manager = null;

  public static void init() {
    TokenizerManager.getInstance();
  }

  public static Tokens tokenize(String text, Tokenizer.type type, boolean lemmatize) {
    return TokenizerManager.getInstance().parseText(text, type, lemmatize);
  }

  private static TokenizerManager getInstance() {
    if (manager == null) {
      manager = new TokenizerManager();
    }
    return manager;
  }

  private Tokenizer tokenizer = null;
  
  private Tokenizer tokenizerNER = null;

  private Tokenizer tokenizerPOS = null;

  private Tokenizer tokenizerParse = null;

  private TokenizerManager() {

  }

  private Tokens parseText(String text, Tokenizer.type type, boolean lemmatize) {
    synchronized (manager) {      
      loadTokenizerForType(type);
    }
    switch (type) {
      case TOKENS:
      case GERMAN_TOKENS:
        return tokenizer.parse(text, lemmatize);
      case POS:
      case GERMAN_POS:
      case ENGLISH_CASELESS_POS:
        return tokenizerPOS.parse(text, lemmatize);
      case NER:
      case GERMAN_NER:
      case ENGLISH_CASELESS_NER:
        return tokenizerNER.parse(text, lemmatize);
      case PARSE:
      case ENGLISH_CASELESS_PARSE:
        return tokenizerParse.parse(text, lemmatize);
      default:
        return null;
    }
  }

  private void loadTokenizerForType(type type) {

    switch (type) {
      case TOKENS:
      case GERMAN_TOKENS:
        if (tokenizer == null) {
          logger_.info("Loading tokenizer of type: " + type.name());
          tokenizer = new Tokenizer(type);
        }
        break;
      case POS:
      case GERMAN_POS:
      case ENGLISH_CASELESS_POS:
        if (tokenizerPOS == null) {
          logger_.info("Loading tokenizer of type: " + type.name());
          tokenizerPOS = new Tokenizer(type);
        }
        break;
      case NER:
      case GERMAN_NER:
      case ENGLISH_CASELESS_NER:
        if (tokenizerNER == null) {
          logger_.info("Loading tokenizer of type: " + type.name());
          tokenizerNER = new Tokenizer(type);
        }
        break;
      case PARSE:
      case ENGLISH_CASELESS_PARSE:
        if (tokenizerParse == null) {
          logger_.info("Loading tokenizer of type: " + type.name());
          tokenizerParse = new Tokenizer(type);
        }
        break;
      default:
        break;
    }
  }

}
