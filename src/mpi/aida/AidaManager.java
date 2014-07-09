package mpi.aida;

import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.iterator.TObjectIntIterator;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import javax.sql.DataSource;

import mpi.aida.access.DataAccess;
import mpi.aida.config.AidaConfig;
import mpi.aida.data.Entities;
import mpi.aida.data.Entity;
import mpi.aida.data.KBIdentifiedEntity;
import mpi.aida.data.Mention;
import mpi.aida.data.Mentions;
import mpi.aida.data.OokbEntity;
import mpi.aida.data.Type;
import mpi.aida.util.ClassPathUtils;
import mpi.aida.util.RunningTimer;
import mpi.aida.util.YagoUtil.Gender;
import mpi.tokenizer.data.Tokenizer;
import mpi.tokenizer.data.TokenizerManager;
import mpi.tokenizer.data.Tokens;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class AidaManager {

  static {
    // Always need to do this.
    init();
  }
  
  private static Logger slogger_ = LoggerFactory.getLogger(AidaManager.class);

  // This is more couple to SQL than it should be. Works for now.
  public static final String DB_AIDA = "DatabaseAida";
  
  public static final String DB_YAGO2 = "DatabaseYago";

  public static final String DB_YAGO2_FULL = "DatabaseYago2Full";

  public static final String DB_YAGO2_SPOTLX = "DatabaseYago2SPOTLX";

  public static final String DB_RMI_LOGGER = "DatabaseRMILogger";

  public static final String DB_HYENA = "DatabaseHYENA";

  public static final String DB_WEBSERVICE_LOGGER = "DatabaseWSLogger";
  
  public static String databaseAidaConfig = "database_aida.properties";
  
  private static String databaseYago2Config = "database_yago2.properties";

  private static String databaseYAGO2SPOTLXConfig = "database_yago2spotlx.properties";

  private static String databaseRMILoggerConfig = "databaseRmiLogger.properties";

  private static String databaseWSLoggerConfig = "databaseWsLogger.properties";
  
  private static String databaseHYENAConfig = "database_hyena.properties";

  public static final String WIKIPEDIA_PREFIX = "http://en.wikipedia.org/wiki/";

  public static final String YAGO_PREFIX = "http://yago-knowledge.org/resource/";

  private static Map<String, String> dbIdToConfig = new HashMap<String, String>();
  
  private static Map<String, Properties> dbIdToProperties = new HashMap<String, Properties>();
  
  private static Map<String, DataSource> dbIdToDataSource = new HashMap<>();

  static {
    dbIdToConfig.put(DB_AIDA, databaseAidaConfig);
    dbIdToConfig.put(DB_YAGO2, databaseYago2Config);
    dbIdToConfig.put(DB_YAGO2_SPOTLX, databaseYAGO2SPOTLXConfig);
    dbIdToConfig.put(DB_RMI_LOGGER, databaseRMILoggerConfig);
    dbIdToConfig.put(DB_HYENA, databaseHYENAConfig);
    dbIdToConfig.put(DB_WEBSERVICE_LOGGER, databaseWSLoggerConfig);
  }

  private static AidaManager tasks = null;

  public static enum language {
    english, german
  }

  private static final Set<String> malePronouns = new HashSet<String>() {

    private static final long serialVersionUID = 2L;
    {
      add("He");
      add("he");
      add("Him");
      add("him");
      add("His");
      add("his");
    }
  };

  private static final Set<String> femalePronouns = new HashSet<String>() {

    private static final long serialVersionUID = 3L;
    {
      add("she");
      add("she");
      add("Her");
      add("her");
      add("Hers");
      add("hers");
    }
  };

  public static void init() {
    getTasksInstance();
  }

  private static synchronized AidaManager getTasksInstance() {
    if (tasks == null) {
      tasks = new AidaManager();
    }
    return tasks;
  }

 

  /**
   * tokenizes only the text,
   * 
   * @param docId
   * @param text
   * @return
   */
  public static Tokens tokenize(String text, boolean lemmatize) {
    return AidaManager.getTasksInstance().tokenize(text, Tokenizer.type.TOKENS, lemmatize);
  }

  public static Tokens tokenize(String text) {
    return AidaManager.getTasksInstance().tokenize(text, Tokenizer.type.TOKENS, false);
  }

  /**
   * tokenizes the text with POS and NER
   * 
   * @param docId
   * @param text
   * @return
   */
  public static Tokens tokenizeNER( String text, boolean lemmatize) {
    return AidaManager.getTasksInstance().tokenize(text, Tokenizer.type.NER, lemmatize);
  }

  /**
   * tokenizes the text with POS
   * 
   * @param docId
   * @param text
   * @return
   */
  public static Tokens tokenizePOS(String text, boolean lemmatize) {
    return AidaManager.getTasksInstance().tokenize(text, Tokenizer.type.POS, lemmatize);
  }

  /**
   * tokenizes the text with PARSE
   * 
   * @param docId
   * @param text
   * @return
   */
  public static Tokens tokenizePARSE(String text, boolean lemmatize) {
    return AidaManager.getTasksInstance().tokenize(text, Tokenizer.type.PARSE, lemmatize);
  }

  /**
   * tokenizes the text with PARSE
   * 
   * @param docId
   * @param text
   * @return
   */
  public static Tokens tokenizePARSE(String text, Tokenizer.type type, boolean lemmatize) {
    return AidaManager.getTasksInstance().tokenize(text, type, lemmatize);
  }

  public static Connection getConnectionForDatabase(String dbId) throws SQLException {
    Properties prop = dbIdToProperties.get(dbId);
    if (prop == null) {
      try {
        prop = ClassPathUtils.getPropertiesFromClasspath(dbIdToConfig.get(dbId));
        dbIdToProperties.put(dbId, prop);
      } catch (IOException e) {
        throw new SQLException(e);
      }
    }
    return getConnectionForNameAndProperties(dbId, prop);
  }
  
  public static Connection getConnectionForNameAndProperties(String dbId, Properties prop) throws SQLException {
    DataSource ds = null;
    synchronized (dbIdToDataSource) {
      ds = dbIdToDataSource.get(dbId);
      if (ds == null) {
        try {
          String serverName = prop.getProperty("dataSource.serverName");
          String database = prop.getProperty("dataSource.databaseName");
          String username = prop.getProperty("dataSource.user");
          String port = prop.getProperty("dataSource.portNumber");
          slogger_.info("Connecting to database " + username + "@" + serverName + ":" + port + "/" + database);

          HikariConfig config = new HikariConfig(prop);
          ds = new HikariDataSource(config);
          dbIdToDataSource.put(dbId, ds);
        } catch (Exception e) {
          slogger_.error("Error connecting to the database: " + e.getLocalizedMessage());
          e.printStackTrace();
        }
      }
    }
    if (ds == null) {
      slogger_.error("Could not connect to the database. " + 
          "Please check the settings in '" + dbIdToConfig.get(dbId) +
          "' and make sure the Postgres server is up and running.");
      return null;
    }
    Integer id = RunningTimer.recordStartTime("getConnection");
    Connection connection = ds.getConnection();
    RunningTimer.recordEndTime("getConnection", id);
    return connection;
  }

  public static Properties getDatabaseProperties(
      String hostname, Integer port, String username, String password,
      Integer maxCon, String database) {
    Properties prop = new Properties();
    prop.put("dataSourceClassName", "org.postgresql.ds.PGSimpleDataSource");
    prop.put("maximumPoolSize", maxCon);    
    prop.put("dataSource.user", username);
    prop.put("dataSource.password", password);
    prop.put("dataSource.databaseName", database);
    prop.put("dataSource.serverName", hostname);
    prop.put("dataSource.portNumber", port);
    return prop;
  }

  public static void releaseConnection(Connection con) {
    try {
      con.close();
    } catch (SQLException e) {
      slogger_.error("Could not release connection: " + e.getLocalizedMessage());
    }
  }

  /**
   * Gets an AIDA entity for the given entity id.
   * This is slow, as it accesses the DB for each call.
   * Do in batch using DataAccess directly for a larger number 
   * of entities.
   * 
   * @return              AIDA Entity
   */
  public static Entity getEntity(KBIdentifiedEntity entity) {
    int id = DataAccess.getInternalIdForKBEntity(entity);
    return new Entity(entity, id);
  }

  public static Entities getEntities(Set<KBIdentifiedEntity> kbEntities) {
    TObjectIntHashMap<KBIdentifiedEntity> ids = 
        DataAccess.getInternalIdsForKBEntities(kbEntities);
    Entities entities = new Entities();
    for(TObjectIntIterator<KBIdentifiedEntity> itr = ids.iterator(); itr.hasNext(); ) {
      itr.advance();
      entities.add(new Entity(itr.key(), itr.value()));
    }
    return entities;
  }

  /**
   * Gets an AIDA entity for the given AIDA entity id.
   * This is slow, as it accesses the DB for each call.
   * Do in batch using DataAccess directly for a larger number 
   * of entities.
   * 
   * @param entityId  Internal AIDA int ID 
   * @return          AIDA Entity
   */
  public static Entity getEntity(int entityId) {
    KBIdentifiedEntity kbEntity = DataAccess.getKnowlegebaseEntityForInternalId(entityId);
    return new Entity(kbEntity, entityId);
  }

  /**
   * Returns the potential entity candidates for a mention (from AIDA dictionary)
   * 
   * @param mention
   *            Mention to get entity candidates for
   * @return Candidate entities for this mention.
   * 
   */
  public static Entities getEntitiesForMention(Mention mention) {
    return getEntitiesForMention(mention, 1.0);
  }

  /**
   * Returns the potential entity candidates for a mention (from AIDA dictionary)
   * 
   * @param mention
   *            Mention to get entity candidates for
   * @param maxEntityRank Retrieve entities up to a global rank, where rank is 
   * between 0.0 (best) and 1.0 (worst). Setting to 1.0 will retrieve all entities.
   * @return Candidate entities for this mention.
   * 
   */
  public static Entities getEntitiesForMention(Mention mention, double maxEntityRank) {
    Set<String> normalizedMentions = mention.getNormalizedMention();
    Entities entities = new Entities();
    Map<String, Entities> entitiesMap = DataAccess.getEntitiesForMentions(normalizedMentions, maxEntityRank);
    for(Entry<String, Entities> entry : entitiesMap.entrySet()) {
      entities.addAll(entry.getValue());
    }
    return entities;
  }

  /**
   * Returns the potential entity candidates for a mention (via the candidates 
   * dictionary) and filters those candidates against the given list of types
   * 
   * @param mention
   *            Mention to get entity candidates for
   * @return Candidate entities for this mention (in YAGO2 encoding) including
   *         their prior probability
   * @throws SQLException
   */
  public static Entities getEntitiesForMention(Mention mention, Set<Type> filteringTypes, double maxEntityRank) throws SQLException {
    Entities entities = getEntitiesForMention(mention, maxEntityRank);
    Entities filteredEntities = new Entities();
    TIntObjectHashMap<Set<Type>> entitiesTypes = DataAccess.getTypes(entities);
    for (TIntObjectIterator<Set<Type>> itr = entitiesTypes.iterator(); 
        itr.hasNext(); ) {
      itr.advance();
      int id = itr.key();
      Set<Type> entityTypes = itr.value();
      for (Type t : entityTypes) {
        if (filteringTypes.contains(t)) {
          filteredEntities.add(entities.getEntityById(id));
          break;
        }
      }
    }
    return filteredEntities;
  }

  public static TIntObjectHashMap<Gender> getGenderForEntities(Entities entities) {
    return DataAccess.getGenderForEntities(entities);
  }

  public static void fillInCandidateEntities(Mentions mentions) throws SQLException {
    fillInCandidateEntities(mentions, false, false, 1.0);
  }

  /**
   * Retrieves all the candidate entities for the given mentions.
   * 
   * @param mentions  All mentions in the input doc.
   * @param includeNullEntityCandidates Set to true to include mentions flagged
   * as NME in the ground-truth data.
   * @param includeContextMentions  Include mentions as context.
   * @param maxEntityRank Fraction of entities to include. Between 0.0 (none)
   * and 1.0 (all). The ranks are taken from the entity_rank table.
   * @throws SQLException
   */
  public static void fillInCandidateEntities(Mentions mentions, boolean includeNullEntityCandidates, boolean includeContextMentions,
      double maxEntityRank) throws SQLException {
    //flag to be used when having entities from different knowledge bases
    //and some of them are linked by a sameAs relation
    //currently applicable only for the configuration GND_PLUS_YAGO
    Integer id = RunningTimer.recordStartTime("AidaManager:fillInCandidates");
    
    Set<Type> filteringTypes = mentions.getEntitiesTypes();
    //TODO This method shouldn't be doing one DB call per mention!
    for (int i = 0; i < mentions.getMentions().size(); i++) {
      Mention m = mentions.getMentions().get(i);
      Entities mentionCandidateEntities;
      if (malePronouns.contains(m.getMention()) || femalePronouns.contains(m.getMention())) {
        setCandiatesFromPreviousMentions(mentions, i);
      } else {
        if (filteringTypes != null) {
          mentionCandidateEntities = AidaManager.getEntitiesForMention(m, filteringTypes, maxEntityRank);
        } else {
          mentionCandidateEntities = AidaManager.getEntitiesForMention(m, maxEntityRank);
        }
        
        // Check for fallback options when no candidate was found using direct lookup.
        if(mentionCandidateEntities.size() == 0) {
          slogger_.debug("No candidates found for " + m);
          
          boolean doDictionaryFuzzyMatching = AidaConfig.getBoolean(AidaConfig.DICTIONARY_FUZZY_MATCHING);
          if (doDictionaryFuzzyMatching) {
            double minSim = AidaConfig.getDouble(AidaConfig.DICTIONARY_FUZZY_MATCHING_MIN_SIM);
            mentionCandidateEntities = DataAccess.getEntitiesForMentionByFuzzyMatcyhing(m.getMention(), minSim);
          }
        }          

        if (includeNullEntityCandidates) {
          Entity nmeEntity = new OokbEntity(m.getMention()); 

          // add surrounding mentions as context
          if (includeContextMentions) {
            List<String> surroundingMentionsNames = new LinkedList<String>();
            int begin = Math.max(i - 2, 0);
            int end = Math.min(i + 3, mentions.getMentions().size());

            for (int s = begin; s < end; s++) {
              if (s == i) continue; // skip mention itself
              surroundingMentionsNames.add(mentions.getMentions().get(s).getMention());
            }
            nmeEntity.setSurroundingMentionNames(surroundingMentionsNames);
          }

          mentionCandidateEntities.add(nmeEntity);
        }      
        m.setCandidateEntities(mentionCandidateEntities);
      }
    }
    RunningTimer.recordEndTime("AidaManager:fillInCandidates", id);
  }

  private static void setCandiatesFromPreviousMentions(Mentions mentions, int mentionIndex) {
    Mention mention = mentions.getMentions().get(mentionIndex);
    Entities allPrevCandidates = new Entities();
    if (mentionIndex == 0) {
      mention.setCandidateEntities(allPrevCandidates);
      return;
    }

    for (int i = 0; i < mentionIndex; i++) {
      Mention m = mentions.getMentions().get(i);
      for (Entity e : m.getCandidateEntities()) {
        allPrevCandidates.add(e);
      }
    }

    TIntObjectHashMap<Gender> entitiesGenders = AidaManager.getGenderForEntities(allPrevCandidates);

    Gender targetGender = null;
    if (malePronouns.contains(mention.getMention())) targetGender = Gender.MALE;
    else if (femalePronouns.contains(mention.getMention())) targetGender = Gender.FEMALE;

    Entities filteredCandidates = new Entities();
    for (Entity e : allPrevCandidates) {
      if (entitiesGenders != null && entitiesGenders.containsKey(e.getId()) 
          && entitiesGenders.get(e.getId()) == targetGender) filteredCandidates
          .add(e);
    }
    mention.setCandidateEntities(filteredCandidates);
  }

  private AidaManager() {
    TokenizerManager.init();
  }

  private Tokens tokenize(String text, Tokenizer.type type, boolean lemmatize) {
    return TokenizerManager.tokenize(text, type, lemmatize);
  }
}
