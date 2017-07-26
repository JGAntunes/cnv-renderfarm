package renderfarm;

import java.util.*;

public class HistoryBasedStrategy implements ScheduleStrategy {
  private static final Logger logger = new Logger("strategy-history-based");

  public HistoryBasedStrategy() {
  }

  @Override
  public EC2Instance execute(RayTracerRequest request, List<EC2Instance> availableInstances, RequestCache requestCache)
      throws NoAvailableInstancesException, IllegalStateException {
    logger.debug("Executing history based strategy");
    // if (this.currentIndex >= availableInstances.size()) {
    //   this.currentIndex = 0;
    // }
    // if (availableInstances.size() == 0) {
    //   throw new NoAvailableInstancesException();
    // }
    // String instance = availableInstances.get(this.currentIndex).getPublicDnsName();
    // this.currentIndex++;
    // return instance;
    return availableInstances.get(0);
  }
}
