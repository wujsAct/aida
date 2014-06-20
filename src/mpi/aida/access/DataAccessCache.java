package mpi.aida.access;

import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import mpi.aida.AidaManager;
import mpi.aida.config.AidaConfig;
import mpi.aida.util.ClassPathUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


class DataAccessCache {
  private static final Logger logger = 
      LoggerFactory.getLogger(DataAccessCache.class);
  
  private static final String DATABASE_AIDA_CONFIG_CACHE = "database_aida.cache";

  private DataAccessIntIntCacheTarget wordExpansion;  
  private DataAccessIntIntCacheTarget keywordCount;
  private DataAccessKeyphraseTokensCacheTarget keyphraseTokens;
  private DataAccessKeyphraseSourcesCacheTarget keyphraseSources;
  
  private static class DataAccessCacheHolder {
    public static DataAccessCache cache = new DataAccessCache();
  }
  
  public static DataAccessCache singleton() {
    return DataAccessCacheHolder.cache;
  }
  
  private DataAccessCache() {
    List<DataAccessCacheTarget> cacheTargets = new ArrayList<>();
    wordExpansion = new DataAccessWordExpansionCacheTarget();
    cacheTargets.add(wordExpansion);
    keywordCount = new DataAccessKeywordCountCacheTarget();
    cacheTargets.add(keywordCount);
    keyphraseTokens = new DataAccessKeyphraseTokensCacheTarget();
    cacheTargets.add(keyphraseTokens);
    keyphraseSources = new DataAccessKeyphraseSourcesCacheTarget();
    cacheTargets.add(keyphraseSources);
    
    logger.info("Loading word caches.");
        
    if (AidaConfig.getBoolean(AidaConfig.CACHE_WORD_DATA)) {
      // Determine cache state.
      boolean needsCacheCreation = true; 
      try {
        needsCacheCreation = determineCacheCreation();
      } catch (FileNotFoundException e1) {
        logger.error("Did not find file: " + e1.getLocalizedMessage());
        e1.printStackTrace();
      } catch (IOException e1) {
        logger.error("Exception reading file: " + e1.getLocalizedMessage());
        e1.printStackTrace();
      }
      for (DataAccessCacheTarget target : cacheTargets) {
        try {          
          target.createAndLoadCache(needsCacheCreation);
        } catch (IOException e) {
          target.loadFromDb();
          logger.warn("Could not read cache file, reading from DB.", e);
        }        
      }
      if (needsCacheCreation) {
        try {
          Properties currentAIDADBConfig = ClassPathUtils.getPropertiesFromClasspath(AidaManager.databaseAidaConfig);
          File cachedDBConfigFile = new File(DATABASE_AIDA_CONFIG_CACHE);
          currentAIDADBConfig.store(new BufferedOutputStream(new FileOutputStream(cachedDBConfigFile)), "cached aida DB config");
        } catch (IOException e ) {
          logger.error("Could not write config: " + e.getLocalizedMessage());
          e.printStackTrace();
        }
      }
    } else {
      logger.info("Loading data from DB.");
      for (DataAccessCacheTarget target : cacheTargets) {
        target.loadFromDb();
      }
    }
    logger.info("Done loading caches.");
  }

  private boolean determineCacheCreation() throws FileNotFoundException, IOException {
    boolean needsCacheCreation = false;
    File cachedDBConfigFile = new File(DATABASE_AIDA_CONFIG_CACHE);
    if (!cachedDBConfigFile.exists()) {
      return true;
    }
    Properties currentAIDADBConfig = ClassPathUtils.getPropertiesFromClasspath(AidaManager.databaseAidaConfig);
    Properties cachedAIDADBConfig = new Properties();
    cachedAIDADBConfig.load(new BufferedInputStream(new FileInputStream(cachedDBConfigFile)));
    if (!currentAIDADBConfig.equals(cachedAIDADBConfig)) {
      logger.info("Cache files exist, but DB config has been changed since it was created; DB access is unavoidable!");
      //there is a change in the DB config
      // do a clean up and require a DB access
      cachedDBConfigFile.delete();
      needsCacheCreation = true;
    }
    return needsCacheCreation;
  }

  public int expandTerm(int wordId) {
    return wordExpansion.getData(wordId);
  }
  
  public int getKeywordCount(int wordId) {
    return keywordCount.getData(wordId);
  }
  
  public int[] getKeyphraseTokens(int wordId) {
    return keyphraseTokens.getData(wordId);
  }
  
  public int getKeyphraseSourceId(String source) {
    return keyphraseSources.getData(source);
  }
  
  public TIntObjectHashMap<int[]> getAllKeyphraseTokens() {
    return keyphraseTokens.getAllData();
  }
  
  public TObjectIntHashMap<String> getAllKeyphraseSources() {
    return keyphraseSources.getAllData();
  }
}
