package renderfarm;

import java.util.*;

public class RoundRobinStrategy implements ScheduleStrategy {
  public int currentIndex;
  private static final Logger logger = new Logger("strategy-roundrobin");

  public RoundRobinStrategy() {
    this.currentIndex = 0;
  }

  @Override
  public EC2Instance execute(RayTracerRequest request, List<EC2Instance> availableInstances, RequestCache requestCache)
      throws NoAvailableInstancesException, IllegalStateException {
    logger.debug("Executing round robin strategy");
    if (this.currentIndex >= availableInstances.size()) {
      this.currentIndex = 0;
    }
    if (availableInstances.size() == 0) {
      throw new NoAvailableInstancesException();
    }
    EC2Instance instance = availableInstances.get(this.currentIndex);
    this.currentIndex++;
    return instance;
  }
}
