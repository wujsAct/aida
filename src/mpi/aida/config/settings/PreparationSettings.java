package mpi.aida.config.settings;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import mpi.aida.config.AidaConfig;
import mpi.aida.data.Type;
import mpi.aida.preparation.documentchunking.DocumentChunker;
import mpi.aida.preparation.documentchunking.FixedLengthDocumentChunker;
import mpi.aida.preparation.documentchunking.PageBasedDocumentChunker;
import mpi.aida.preparation.documentchunking.SingleChunkDocumentChunker;
import mpi.aida.preparation.mentionrecognition.FilterMentions.FilterType;

/**
 * Settings for the preparator. Predefined settings are available in
 * {@see mpi.aida.config.settings.preparation}.
 */
public class PreparationSettings implements Serializable {

  private static final long serialVersionUID = -2825720730925914648L;

  private FilterType mentionsFilter = FilterType.STANFORD_NER;
  
  private boolean useHybridMentionDetection = true;
  
  /** 
   * Minimum number of mention occurrence to be considered in disambiguation.
   * Default is to consider all mentions.
   */
  private int minMentionOccurrenceCount = 1;

  private Type[] filteringTypes = AidaConfig.getFilteringTypes();
  
  //default to the language in AIDA configuration
  private LANGUAGE language = AidaConfig.getLanguage();
  
  private DOCUMENT_CHUNK_STRATEGY docChunkStrategy = AidaConfig.getDocumentChunkStrategy();
  
  public static enum DOCUMENT_CHUNK_STRATEGY {
    SINGLE, PAGEBASED, MULTIPLE_FIXEDLENGTH
  }
  
  public static enum LANGUAGE {
    en, de
  }

  public FilterType getMentionsFilter() {
    return mentionsFilter;
  }

  public void setMentionsFilter(FilterType mentionsFilter) {
    this.mentionsFilter = mentionsFilter;
  }

  public Type[] getFilteringTypes() {
    return filteringTypes;
  }

  public void setFilteringTypes(Type[] filteringTypes) {
    this.filteringTypes = filteringTypes;
  }
  
  public LANGUAGE getLanguage() {
    return language;
  }
  
  public void setLanguage(LANGUAGE language) {
    this.language = language;
  }
  
  public boolean isUseHybridMentionDetection() {
    return useHybridMentionDetection;
  }
  
  public void setUseHybridMentionDetection(boolean useHybridMentionDetection) {
    this.useHybridMentionDetection = useHybridMentionDetection;
  }

  public int getMinMentionOccurrenceCount() {
    return minMentionOccurrenceCount;
  }

  public void setMinMentionOccurrenceCount(int minMentionOccurrenceCount) {
    this.minMentionOccurrenceCount = minMentionOccurrenceCount;
  }

  public Map<String, Object> getAsMap() {
    Map<String, Object> s = new HashMap<String, Object>();
    s.put("mentionsFilter", mentionsFilter.toString());
    s.put("language", language.toString());
    s.put("minMentionOccurrenceCounts", String.valueOf(minMentionOccurrenceCount));
    s.put("useHybridMentionDetection", String.valueOf(useHybridMentionDetection));
    s.put("docChunkStrategy", docChunkStrategy.toString());
    return s;
  }
  
  public DocumentChunker getDocumentChunker() {
    DocumentChunker chunker = null;
    switch (docChunkStrategy) {
      case SINGLE:
        chunker = new SingleChunkDocumentChunker();
        break;
      case PAGEBASED:
        chunker = new PageBasedDocumentChunker();
        break;
      case MULTIPLE_FIXEDLENGTH:
        chunker = new FixedLengthDocumentChunker();
        break;
    }
    return chunker;
  }
}
