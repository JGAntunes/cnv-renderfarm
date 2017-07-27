package renderfarm;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.*;

public class Scheduler {

  private static final int MAX_RETRIES = 3;
  private static final Logger logger = new Logger("scheduler");

  private RequestCache requestCache;
  private EC2Pool ec2Pool;
  private ConcurrentHashMap<String, EC2Instance> availableInstances;
  private Thread pollingThread;
  private List<ScheduleStrategy> strategies;

  public Scheduler(List<ScheduleStrategy> strategies) {
    this.requestCache = new RequestCache();
    this.ec2Pool = new EC2Pool(AWSUtils.getAvailableInstances());
    this.availableInstances = new ConcurrentHashMap<String, EC2Instance>();
    this.strategies = strategies;
  }

  public RayTracerResponse schedule(RayTracerRequest request) throws NoAvailableInstancesException {
    // Try to schedule
    EC2Instance instance = null;
    for (int i = 0; i < MAX_RETRIES; i++) {
      try {
        // Iterate through the available strategies by order until one works
        for(ScheduleStrategy strategy : this.strategies) {
          try {
            instance = strategy.execute(request, new ArrayList(ec2Pool.getAvailableInstances()), requestCache);
            // Success, let's get out
            break;
          } catch (IllegalStateException e) {
            continue;
          }
        }
      }
      // Don't give up, retry
      catch (NoAvailableInstancesException e) {
        logger.warning("Unable to process request " + request.getId() + " " + e.getMessage());
        logger.warning("Retry " + i);
        continue;
      }
      try {
        RayTracerResponse response = instance.processRequest(request);
        // Set value in cache
        requestCache.put(request);
        return response;
      } catch (IOException e) {
        logger.warning(String.format("Got error scheduling request %s - instance %s - %s",
          request.getId(), instance.getId(), e.getMessage()));
        // Flag the instance
        ec2Pool.flagInstance(instance.getId());
        return schedule(request);
      }
    }
    // Fail case, throw error
    throw new NoAvailableInstancesException();
  }
}
