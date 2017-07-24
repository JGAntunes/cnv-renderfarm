package renderfarm;

import java.util.concurrent.*;
import java.util.*;

public class Scheduler {

  private static final int MAX_RETRIES = 3;
  private static final Logger logger = new Logger("scheduler");

  private ConcurrentHashMap<String, EC2Instance> availableInstances;
  private Thread pollingThread;
  private ScheduleStrategy strategy;
  private int currentRetries;

  public Scheduler(ScheduleStrategy strategy) {
    this.availableInstances = new ConcurrentHashMap<String, EC2Instance>();
    Poller poller = new Poller();
    this.pollingThread = new Thread(poller);
    this.pollingThread.start();
    this.strategy = strategy;
  }

  public String schedule(RayTracerRequest request) throws NoAvailableInstancesException {
    String instance;
    try {
      instance = strategy.execute(request, new ArrayList<EC2Instance>(this.availableInstances.values()));
    } catch (NoAvailableInstancesException e) {
      if (this.currentRetries > MAX_RETRIES) {
        throw e;
      }
      this.currentRetries++;
      this.updateInstances();
      return this.schedule(request);
    }
    this.currentRetries = 0;
    return instance;
  }

  public void updateInstances() {
    Map<String, EC2Instance> newInstances = AWSUtils.getAvailableInstances();
    logger.debug("Got a total of " + newInstances.size() + " instances available");
    // Clean and disable monitoring on the old instances
    for (Map.Entry<String, EC2Instance> entry : availableInstances.entrySet()) {
      String instanceId = entry.getKey();
      EC2Instance oldInstance = entry.getValue();
      EC2Instance newInstance = newInstances.get(instanceId);
      if (newInstance == null) {
        oldInstance.monitor(false);
        availableInstances.remove(instanceId);
      }
    }
    // Set and initialize monitoring in the new instances
    for (Map.Entry<String, EC2Instance> entry : newInstances.entrySet()) {
      String instanceId = entry.getKey();
      EC2Instance newInstance = entry.getValue();
      availableInstances.putIfAbsent(instanceId, newInstance);
      availableInstances.get(instanceId).monitor(true);
    }
  }

  public class Poller implements Runnable {
    private static final int SLEEP_TIME = 10000;

    @Override
    public void run() {
      logger.debug("Instance poller starting");
      while(true) {

        try{
          logger.debug("Instance poller running");
          updateInstances();
          Thread.sleep(SLEEP_TIME);
        } catch (InterruptedException e) {

        }
      }
    }
  }
}
