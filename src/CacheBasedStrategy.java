package renderfarm;

import java.util.*;

public class CacheBasedStrategy extends TimeCPURatioStrategy implements ScheduleStrategy {
  private static final Logger logger = new Logger("strategy-cache-based");

  public CacheBasedStrategy() {
  }

  @Override
  public EC2Instance execute(RayTracerRequest request, List<EC2Instance> availableInstances, RequestCache requestCache)
      throws NoAvailableInstancesException, IllegalStateException {
    logger.debug("Executing cache based strategy");
    RequestCache.CachedValue cachedValue = requestCache.get(request);
    if (cachedValue == null){
      logger.debug("Cache miss, strategy failed");
      throw new IllegalStateException();
    }
    return super.execute(cachedValue.getTime(), availableInstances);
  }
}
