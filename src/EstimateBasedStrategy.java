package renderfarm;

import java.util.*;

public class EstimateBasedStrategy extends TimeCPURatioStrategy implements ScheduleStrategy {
  private static final Logger logger = new Logger("strategy-estimate-based");

  public EstimateBasedStrategy() {
  }

  @Override
  public EC2Instance execute(RayTracerRequest request, List<EC2Instance> availableInstances, RequestCache requestCache)
      throws NoAvailableInstancesException, IllegalStateException {
    logger.debug("Executing estimate based strategy");
    long expectation = RayTracerRequest.calculateExpectedTime(request);
    return super.execute(expectation, availableInstances);
  }
}
