package mpi.aida.graph.similarity;

import gnu.trove.iterator.TIntDoubleIterator;
import gnu.trove.map.hash.TIntDoubleHashMap;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;

import mpi.aida.data.Entity;
import mpi.aida.data.Mention;

/**
 * This class calculates the prior probability of a mention
 * being associated with a given entity. The prior probability is based
 * on the occurrence count of links (and their anchor text as mention) with
 * a given Wikipedia/YAGO entity as target.
 * 
 * The calculation is done on the fly, so it is a bit slow. For a faster implementation,
 * use {@link MaterializedPriorProbability}.
 * 
 * It uses the 'hasInternalWikipediaLinkTo' and 'hasAnchorText' relations
 * in the YAGO2 database.
 * 
 *
 */
public abstract class PriorProbability {
 
  protected HashMap<Mention, TIntDoubleHashMap> priors;
  
  private double weight;
  
  public PriorProbability(Set<Mention> mentions) throws SQLException {
    setupMentions(mentions);
  }
  
  public double getWeight() {
    return weight;
  }

  public void setWeight(double weight) {
    this.weight = weight;
  }
  
  protected abstract void setupMentions(Set<Mention> mentions) throws SQLException;

  /**
   * Returns the prior probability for the given mention-entity pair.
   * If smoothing is true, it will return the lowest prior among all entities if
   * there is no real prior.
   * 
   * @param mention
   * @param entity
   * @param smoothing
   * @return
   */
  public double getPriorProbability(
      Mention mention, Entity entity, boolean smoothing) {
    
    TIntDoubleHashMap allMentionPriors = priors.get(mention);    
    double entityPrior = allMentionPriors.get(entity.getId());
    
    if (smoothing && entityPrior == 0.0) {
      double smallestPrior = 1.0;
      
      for (TIntDoubleIterator it = allMentionPriors.iterator(); it.hasNext();) {
        it.advance();
        double currentPrior = it.value(); 
        if (currentPrior < smallestPrior) {
          smallestPrior = currentPrior;
        }
      }      
      entityPrior = smallestPrior;
    }
    
    return entityPrior;
  }
  
  public double getBestPrior(Mention mention) {
    TIntDoubleHashMap allMentionPriors = priors.get(mention);


    double bestPrior = 0.0;
    for (TIntDoubleIterator it = allMentionPriors.iterator(); it.hasNext();) {
      it.advance();
      double currentPrior = it.value();
      if (currentPrior > bestPrior) {
        bestPrior = currentPrior;
      }
    }
    
    return bestPrior;
  }
  
  public double getPriorProbability(Mention mention, Entity entity) {
    return getPriorProbability(mention, entity, false);
  }
  
  public static String conflateMention(String mention) {
    // conflate cases for mentions of length >= 4
    if (mention.length() >= 4) {
      mention = mention.toUpperCase(Locale.ENGLISH);
    }
    
    return mention;
  }
} 
