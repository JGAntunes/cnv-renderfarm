package renderfarm;

import java.util.*;

public class TimeCPURatioStrategy {

  public TimeCPURatioStrategy() {
  }

  public EC2Instance execute(long baseTimeValue, List<EC2Instance> availableInstances) {
    double smallestLoad = -1;
    EC2Instance selectedInstance = null;
    for(EC2Instance instance : availableInstances) {
      long totalTime = baseTimeValue;
      for(RayTracerRequest r : instance.getInFlightRequests()) {
        totalTime += r.getExpectedTime();
      }
      // Total estimate time devided by available CPU
      double loadEstimate = totalTime / (100 - instance.getCPU());
      if (smallestLoad > loadEstimate || smallestLoad == -1) {
        smallestLoad = loadEstimate;
        selectedInstance = instance;
      }
    }
    return selectedInstance;
  }
}
