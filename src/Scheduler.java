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
  private ScheduleStrategy strategy;

  public Scheduler(ScheduleStrategy strategy) {
    this.requestCache = new RequestCache();
    this.ec2Pool = new EC2Pool(AWSUtils.getAvailableInstances());
    this.availableInstances = new ConcurrentHashMap<String, EC2Instance>();
    this.strategy = strategy;
  }

  // FIXME IOException needs to be handled here or on the EC2Instance
  public RayTracerResponse schedule(RayTracerRequest request) throws NoAvailableInstancesException {
    // Try to schedule
    EC2Instance instance;
    for (int i = 0; i < MAX_RETRIES; i++) {
      try {
        instance = strategy.execute(request, new ArrayList(ec2Pool.getAvailableInstances()), requestCache);
      } catch (NoAvailableInstancesException e) {
        logger.warning("Unable to process request " + request.getId() + " " + e.getMessage());
        logger.warning("Retry " + i);
        continue;
      }
      try {
        return instance.processRequest(request);
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
