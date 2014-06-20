package mpi.aida.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Captures the running time of modules and their stages. The timing is not
 * perfectly accurate due to synchronization, however the contention should
 * be very low. Don't use this class to do high-frequency profiling, this 
 * is useful for high-level performance measuring. 
 * 
 * A module is usually a class and a stage is usually one logical
 * step inside the class.
 * 
 *
 */
public class RunningTimer {
  /**
   * Map is moduleId -> uniqueId -> timestamp.
   * 
   * UniqueId is an id assigned each time start(moduleId) is called. The map
   * is only accessed by start() (which is synchronized), no need to be 
   * concurrency safe. 
   * 
   */
  private static Map<String, Map<Integer, Long>> moduleStart = 
      new HashMap<String, Map<Integer, Long>>();
  private static Map<String, Map<Integer, Long>> moduleEnd = 
      new ConcurrentHashMap<String, Map<Integer, Long>>();
  private static Map<String, List<String>> moduleCaller = 
      new ConcurrentHashMap<String, List<String>>();
    
  private static Map<String, Integer> threadSpecificLevelCounter = new HashMap<String, Integer>();  
  private static Map<String, List<String>> threadSpecificModules = new HashMap<String, List<String>>();
  private static Map<String, List<Integer>> threadSpecificModuleLevels = new HashMap<String, List<Integer>>();
  
  public static String getOverview() {
    StringBuilder sb = new StringBuilder();
    sb.append("Module Overview\n---\n");
    sb.append("Module\t\t\t#Docs\t\t\tAvg. Time\t\t\tMax. Time\n");
    sb.append(getDataOverview(moduleStart, moduleEnd, moduleCaller)).append("\n");
    
    return sb.toString();
  }
  
  private static String getDataOverview(
      Map<String, Map<Integer, Long>> start, 
      Map<String, Map<Integer, Long>> end,
      Map<String, List<String>> callers) {
    StringBuilder sb = new StringBuilder();
        
    for(String tName : threadSpecificModules.keySet()){
      sb.append(tName).append("\n").append("--------------------").append("\n");
      List<String> modules = threadSpecificModules.get(tName);
      for (int i=0;i<modules.size();i++) {
        String id = modules.get(i);
        int level = threadSpecificModuleLevels.get(tName).get(i);
        for(int sp=1;sp<level;sp++){
           sb.append("\t");
        }

        sb.append(id).append("\t\t\t");
        sb.append(end.get(id).size()).append("\t\t\t");
        
        double totalTime = 0.0;
        double maxTime = 0.0;
        for (Integer uniqueId : end.get(id).keySet()) {
          Long finish = end.get(id).get(uniqueId);
          assert start.containsKey(id) : "No start for end.";
          Long begin = start.get(id).get(uniqueId);
          Long dur = finish - begin;
          totalTime += dur;
          if (dur > maxTime) { maxTime = dur; }
        }
        double avgTime = totalTime / end.get(id).size();
        
        sb.append(NiceTime.convert(avgTime)).append("\t\t\t");
        sb.append(NiceTime.convert(maxTime)).append("\n");
//        for(String caller : callers.get(id)){
//          sb.append("   * ").append(caller).append("\n");
//        }      
      }
    }
    
    return sb.toString();
  }
  
  public static synchronized Integer recordStartTime(String moduleId){
    Integer uniqueId = start(moduleId);
    String tName = Thread.currentThread().getName();
    int level = 0;
    if(!threadSpecificLevelCounter.containsKey(tName)){
      level = 1;      
    }else{
      level = threadSpecificLevelCounter.get(tName) + 1;
    }
    threadSpecificLevelCounter.put(tName, level);
    
    List<String> modules = null;
    List<Integer> moduleLevels = null;
    if(!threadSpecificModules.containsKey(tName)){
      modules = new ArrayList<String>();
      threadSpecificModules.put(tName, modules);      
    }else{
      modules = threadSpecificModules.get(tName);      
    }
    
    if(!threadSpecificModuleLevels.containsKey(tName)){      
      moduleLevels = new ArrayList<Integer>();
      threadSpecificModuleLevels.put(tName, moduleLevels);
    }else{
      moduleLevels = threadSpecificModuleLevels.get(tName);
    }    
    
    if(modules.indexOf(moduleId)<0){
      modules.add(moduleId);
      moduleLevels.add(level);
    }   
    return uniqueId;
  }
  
  public static synchronized Long recordEndTime(String moduleId, Integer uniqueId){
    Long tStamp = end(moduleId, uniqueId);
    String tName = Thread.currentThread().getName();
    int level = threadSpecificLevelCounter.get(tName)-1;
    threadSpecificLevelCounter.put(tName, level);
    return tStamp;
  }
  
  /**
   * Starts the time for the given moduleId.
   * 
   * @param moduleId
   */
  public static synchronized Integer start(String moduleId) {
    Long timestamp = System.currentTimeMillis();
    Integer uniqueId = 0;
    Map<Integer, Long> starts = moduleStart.get(moduleId);
    if (starts != null) {
      uniqueId = moduleStart.get(moduleId).size();
    } else {
      starts = new HashMap<Integer, Long>();
      moduleStart.put(moduleId, starts);
    }
    starts.put(uniqueId, timestamp);
    // maintain stack trace for each module that is timed
    List<String> lstCallers = moduleCaller.get(moduleId);
    String callers = DebugUtils.getCallingMethod();
    if(lstCallers != null){      
      if(!lstCallers.contains(callers)) {
        lstCallers.add(callers);
      }      
    }else{
      lstCallers = new ArrayList<String>();      
      lstCallers.add(DebugUtils.getCallingMethod());           
    }
    moduleCaller.put(moduleId, lstCallers);
    return uniqueId;
  }
  
  /**
   * Halts the timer for the given moduleId, capturing the full time of 
   * the module. The uniqueId has to correspond to what is returned by 
   * start(moduleId).
   * 
   * @param moduleId
   */
  public static Long end(String moduleId, Integer uniqueId) {
    Long timestamp = System.currentTimeMillis();
    Map<Integer, Long> end = moduleEnd.get(moduleId);
    if (end == null) {
      end = new ConcurrentHashMap<Integer, Long>();
      moduleEnd.put(moduleId, end);
    }
    end.put(uniqueId, timestamp);
    Long startTime = moduleStart.get(moduleId).get(uniqueId);
    return timestamp - startTime;
  }
}
