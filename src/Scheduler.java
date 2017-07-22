package renderfarm;

import java.util.concurrent.*;
import java.util.*;

public class Scheduler {

  private static final int MAX_RETRIES = 3;
  private static final Logger logger = new Logger("scheduler");

  private List<String> availableInstances;
  private Thread pollingThread;
  private ScheduleStrategy strategy;
  private int currentRetries;

  public Scheduler(ScheduleStrategy strategy) {
    this.availableInstances = new ArrayList<String>();
    Poller poller = new Poller();
    this.pollingThread = new Thread(poller);
    this.pollingThread.start();
    this.strategy = strategy;
  }

  public String schedule(RayTracerRequest request) throws NoAvailableInstancesException {
    String instance;
    try {
      instance = strategy.execute(request, this.availableInstances);
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
    List<String> instances = AWSUtils.getAvailableInstances();
    logger.debug("Got a total of " + instances.size() + " instances available");
    for(String instance : instances) {
      logger.debug(instance);
    }
    availableInstances = instances;
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
