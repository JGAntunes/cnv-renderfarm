package renderfarm;

import java.util.concurrent.*;
import java.util.*;

public class Scheduler {

  private static final int MAX_RETRIES = 3;
  private static final Logger logger = new Logger("scheduler");

  private ConcurrentHashMap<String, EC2Instance> availableInstances;
  private Thread pollingThread;
  private ScheduleStrategy strategy;

  public Scheduler(ScheduleStrategy strategy) {
    this.availableInstances = new ConcurrentHashMap<String, EC2Instance>();
    Poller poller = new Poller();
    this.pollingThread = new Thread(poller);
    this.pollingThread.start();
    this.strategy = strategy;
  }

  public String schedule(RayTracerRequest request) throws NoAvailableInstancesException {
    // Try to schedule
    for (int i = 0; i < MAX_RETRIES; i++) {
      try {
        String instanceUrl = strategy.execute(request, new ArrayList<EC2Instance>(this.availableInstances.values()));
        return instanceUrl;
      } catch (NoAvailableInstancesException e) {
        this.updateInstances();
      }
    }
    // Fail case, throw error
    throw new NoAvailableInstancesException();
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
    // Set and initialize monitoring in the new instannce
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
