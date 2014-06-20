package mpi.aida.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MathUtil {

  private static final Logger logger = LoggerFactory.getLogger(MathUtil.class);

  public static double rescale(double value, double min, double max) {
    if (min == max) {
      // No score or only one, return max.
      return 1.0;
    }

    if (value < min) {
      logger.debug("Wrong normalization, " + value 
          + " not in [" + min + "," + max + "], " + "renormalizing to 0.0.");
      return 0.0;
    } else if (value > max) {
      logger.debug("Wrong normalization, " + value 
          + " not in [" + min + "," + max + "], " + "renormalizing to 1.0.");
      return 1.0;
    }
    return (value - min) / (max - min);
  }

  public static double rescale(int value, int min, int max) {
    double dValue = value;
    double dMin = min;
    double dMax = max;
    return rescale(dValue, dMin, dMax);
  }
  
  public static double F1(double precision, double recall) {
    return (precision == 0 && recall == 0) ? 0 : 
      (2 * precision * recall) / (1.0 * (precision + recall));
  }

}
