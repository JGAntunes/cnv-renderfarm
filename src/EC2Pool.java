package renderfarm;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class EC2Pool {

  private static final Logger logger = new Logger("ec2-pool");

  // All instances
  private ConcurrentHashMap<String, EC2Instance> instances;
  // Active instance pool
  private ConcurrentHashMap<String, EC2Instance> healthyPool;
  // Pool of instances whose health checks actually failed
  private ConcurrentHashMap<String, EC2Instance> unhealthyPool;
  // Pool of instances flagged by the scheduler as unable to reply, need to be checked
  private ConcurrentHashMap<String, EC2Instance> flaggedPool;
  // Pool of instances marked for termination
  private ConcurrentHashMap<String, EC2Instance> terminatingPool;

  private ExecutorService threadPool;
  private ConcurrentHashMap<String, EC2Poller> workers;

  public EC2Pool(Map<String, EC2Instance> initialInstances) {
    this.instances = new ConcurrentHashMap(initialInstances);
    // :rainbow: All is beautiful and all of our instances "start" super healthy :rainbow:
    this.healthyPool = new ConcurrentHashMap(initialInstances);
    this.unhealthyPool = new ConcurrentHashMap();
    this.flaggedPool = new ConcurrentHashMap();
    this.terminatingPool = new ConcurrentHashMap();
    // Structures that will hold our runnable info
    this.threadPool = Executors.newCachedThreadPool();
    this.workers = new ConcurrentHashMap();
    // Start all the EC2Pollers
    for(Map.Entry<String, EC2Instance> entry : initialInstances.entrySet()) {
      EC2Poller poller = new EC2Poller(entry.getValue());
      Future thread = this.threadPool.submit(poller);
      this.workers.put(entry.getKey(), poller);
    }
  }

  public Collection<EC2Instance> getAvailableInstances() {
    return healthyPool.values();
  }

  public EC2Instance getInstance(String instanceId) {
    return this.instances.get(instanceId);
  }

  public void terminateInstance(String instanceId) {
    this.workers.get(instanceId).setInstanceForTermination();
  }

  public void flagInstance(String instanceId) {
    this.workers.get(instanceId).flagInstance();
  }

  private class EC2Poller implements Runnable {
    private static final int SLEEP_TIME = 10000;

    private EC2Instance instance;
    private AtomicBoolean run;

    public EC2Poller (EC2Instance instance) {
      this.instance = instance;
      this.run = new AtomicBoolean(false);
    }

    private synchronized  void healthCheck(Boolean reportedStatus) {
      boolean isHealthy = this.instance.runHealthCheck();
      if (reportedStatus == null || isHealthy != reportedStatus) {
        if (isHealthy) {
          unhealthyPool.remove(this.instance.getId());
          healthyPool.put(this.instance.getId(), this.instance);
        } else {
          unhealthyPool.remove(this.instance.getId());
          healthyPool.put(this.instance.getId(), this.instance);
        }
      }
    }

    public synchronized void flagInstance() {
      healthyPool.remove(this.instance.getId());
      flaggedPool.put(this.instance.getId(), getInstance(this.instance.getId()));
    }

    public synchronized void setInstanceForTermination() {
      // Remove from every pool
      healthyPool.remove(this.instance.getId());
      unhealthyPool.remove(this.instance.getId());
      flaggedPool.remove(this.instance.getId());
      // Add to the terminating pool
      terminatingPool.remove(this.instance.getId());
    }

    public synchronized void terminateInstance() {
      // Stop poller thread
      this.run.set(false);
      // Remove from every map
      healthyPool.remove(this.instance.getId());
      unhealthyPool.remove(this.instance.getId());
      flaggedPool.remove(this.instance.getId());
      terminatingPool.remove(this.instance.getId());
      workers.remove(this.instance.getId());
      // And finally from the all instances map
      instances.remove(this.instance.getId());
    }

    @Override
    public void run() {
      logger.debug("EC2 monitor starting");
      this.run.set(true);
      while(run.get()) {
        try{
          Thread.sleep(SLEEP_TIME);
          logger.debug("EC2 monitor running");

          // Update pool status
          Boolean currentStatus = null;
          if (terminatingPool.containsKey(instance.getId())) {
            if (instance.getInFlightRequests().size() <= 0) {
              terminateInstance();
              // Exit thread safely
              return;
            }
            // Continue as we don't need to update anything on a terminating instance
            continue;
          }
          if (healthyPool.containsKey(instance.getId())) {
            currentStatus = true;
          } else if (unhealthyPool.containsKey(instance.getId())) {
            currentStatus = false;
          } else if (flaggedPool.containsKey(instance.getId())) {
            flaggedPool.remove(instance.getId());
          }
          // Run health check based on the current status
          healthCheck(currentStatus);

          // Update metrics
          this.instance.addMetrics(AWSUtils.getCPU(this.id));
          this.instance.setCredit(AWSUtils.getCredit());
        } catch (InterruptedException e) {
          run.set(false);
        }
      }
    }
  }
}
