package renderfarm;

import java.util.*;

public class RoundRobinStrategy implements ScheduleStrategy {
  public int currentIndex;
  private static final Logger logger = new Logger("strategy-roundrobin");

  public RoundRobinStrategy() {
    this.currentIndex = 0;
  }

  @Override
  public String execute(RayTracerRequest request, List<EC2Instance> availableInstances) throws NoAvailableInstancesException {
    logger.debug("Executing round robin strategy");
    if (this.currentIndex >= availableInstances.size()) {
      this.currentIndex = 0;
    }
    if (availableInstances.size() == 0) {
      throw new NoAvailableInstancesException();
    }
    String instance = availableInstances.get(this.currentIndex).getPublicDnsName();
    this.currentIndex++;
    return instance;
  }
}
