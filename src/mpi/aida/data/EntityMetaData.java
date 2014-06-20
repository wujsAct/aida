package mpi.aida.data;

import java.io.Serializable;

public class EntityMetaData implements Serializable{

  private static final long serialVersionUID = -5254220574529910760L;

  private int id;

  private String humanReadableRepresentation;

  private String url;
  
  private String knowledgebase;
  
  private String depictionurl;
  
  private String depictionthumbnailurl;

  public EntityMetaData(int id, String humanReadableRepresentation, String url, 
      String knowledgebase, String depictionurl, String depictionthumbnailurl) {
    super();
    this.id = id;
    this.humanReadableRepresentation = humanReadableRepresentation;
    this.url = url;
    this.knowledgebase = knowledgebase;
    this.depictionurl = depictionurl;
    this.depictionthumbnailurl = depictionthumbnailurl;
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public String getHumanReadableRepresentation() {
    return humanReadableRepresentation;
  }

  public void setHumanReadableRepresentation(String humanReadableRepresentation) {
    this.humanReadableRepresentation = humanReadableRepresentation;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  
  public String getKnowledgebase() {
    return knowledgebase;
  }

  
  public void setKnowledgebase(String knowledgebase) {
    this.knowledgebase = knowledgebase;
  }

  
  public String getDepictionurl() {
    return depictionurl;
  }

  
  public void setDepictionurl(String depictionurl) {
    this.depictionurl = depictionurl;
  }

  
  public String getDepictionthumbnailurl() {
    return depictionthumbnailurl;
  }

  
  public void setDepictionthumbnailurl(String depictionthumbnailurl) {
    this.depictionthumbnailurl = depictionthumbnailurl;
  }

}
