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
  // Pool of instances still under the grace period
  private ConcurrentHashMap<String, EC2Instance> startingPool;
  private AutoScaler autoScaler;
  private ExecutorService threadPool;
  private ConcurrentHashMap<String, EC2Poller> workers;

  public EC2Pool(Map<String, EC2Instance> initialInstances) {
    this.instances = new ConcurrentHashMap(initialInstances);
    // :rainbow: All is beautiful and all of our instances "start" super healthy :rainbow:
    this.healthyPool = new ConcurrentHashMap(initialInstances);
    this.unhealthyPool = new ConcurrentHashMap();
    this.flaggedPool = new ConcurrentHashMap();
    this.terminatingPool = new ConcurrentHashMap();
    this.startingPool = new ConcurrentHashMap();
    // Structures that will hold our runnable info
    this.threadPool = Executors.newCachedThreadPool();
    this.workers = new ConcurrentHashMap();
    // Start all the EC2Pollers
    for(Map.Entry<String, EC2Instance> entry : initialInstances.entrySet()) {
      EC2Poller poller = new EC2Poller(entry.getValue());
      Future thread = this.threadPool.submit(poller);
      this.workers.put(entry.getKey(), poller);
    }
    // Start the auto scaler
    this.autoScaler = new AutoScaler();
    this.threadPool.submit(this.autoScaler);
  }

  public Collection<EC2Instance> getAvailableInstances() {
    return healthyPool.values();
  }

  public EC2Instance getInstance(String instanceId) {
    return this.instances.get(instanceId);
  }

  private void startNewInstance() {
    logger.debug("Starting new instance");
    try {
      EC2Instance newInstance = AWSUtils.startNewInstance();
      this.instances.put(newInstance.getId(), newInstance);
      this.startingPool.put(newInstance.getId(), newInstance);
      // Start the poller
      EC2Poller poller = new EC2Poller(newInstance);
      Future thread = this.threadPool.submit(poller);
      this.workers.put(newInstance.getId(), poller);
    } catch (Exception e) {
      logger.error("Failed to start instance " + e.getMessage());
    }
  }

  public void terminateInstance(String instanceId) {
    this.workers.get(instanceId).setInstanceForTermination();
  }

  public void flagInstance(String instanceId) {
    this.workers.get(instanceId).flagInstance();
  }

  private class AutoScaler implements Runnable {
    private static final int MIN_INSTANCES = 3;
    private static final int SLEEP_TIME = 8 * 60 * 1000;
    private static final double CPU_HIGH_THRESHOLD = 60;
    private static final double CPU_LOW_THRESHOLD = 30;

    private boolean run;

    public AutoScaler() {
      this.run = true;
    }

    @Override
    public void run() {
      while(run) {
        try {
          Map<Integer, String> creditList = new TreeMap<Integer, String>();
          int numInstances = getAvailableInstances().size() + startingPool.size();
          int cpu = 0;
          // Assure the minimum instances running
          if (numInstances < MIN_INSTANCES) {
            logger.debug("Auto scaler detected lack of instances - num: " + numInstances);
            startNewInstance();
          } else {
            for( EC2Instance instance : getAvailableInstances() ) {
              cpu += instance.getCPU();
              creditList.put(Integer.valueOf(instance.getCredit()), instance.getId());
            }

            // Start/kill instances based on the average CPU utilization of the whole system
            int sysCpuAvg = cpu/numInstances;
            if( sysCpuAvg >= CPU_HIGH_THRESHOLD ) {
              logger.debug("CPU usage too high - " + sysCpuAvg + " - starting new instance");
              startNewInstance();
            } else if ( sysCpuAvg <= CPU_LOW_THRESHOLD &&  numInstances > MIN_INSTANCES) {
              logger.debug("CPU usage too low - " + sysCpuAvg + " - killing instance");
              //choose the one with the least credits
              terminateInstance(creditList.get(0));
            }
          }
          Thread.sleep(SLEEP_TIME);
        } catch (InterruptedException e) {
          run = false;
        }
      }
    }
  }

  private class EC2Poller implements Runnable {
    private static final int SLEEP_TIME = 10 * 1000;
    private static final int GRACE_PERIOD = 3 * 60 * 1000;

    private EC2Instance instance;
    private AtomicBoolean run;
    private Timer runningTime;

    public EC2Poller (EC2Instance instance) {
      this.runningTime = new Timer();
      this.instance = instance;
      this.run = new AtomicBoolean(false);
    }

    private synchronized void healthCheck(Boolean reportedStatus) {
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
      logger.debug("Flagging instance " + this.instance.getId());
      healthyPool.remove(this.instance.getId());
      flaggedPool.put(this.instance.getId(), getInstance(this.instance.getId()));
    }

    public synchronized void setInstanceForTermination() {
      logger.debug("Set instance for termination " + this.instance.getId());
      // Remove from every pool
      healthyPool.remove(this.instance.getId());
      unhealthyPool.remove(this.instance.getId());
      flaggedPool.remove(this.instance.getId());
      // Add to the terminating pool
      terminatingPool.remove(this.instance.getId());
    }

    public synchronized void terminateInstance() {
      logger.debug("Terminating instance " + this.instance.getId());
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
          logger.debug("EC2 monitor running");

          // Update pool status
          Boolean currentStatus = null;
          if (terminatingPool.containsKey(instance.getId())) {
            if (instance.getInFlightRequests().size() <= 0) {
              terminateInstance();
              // Exit thread safely
              return;
            }
          } else if (startingPool.containsKey(instance.getId())) {
            // Still under the grace period?
            if (runningTime.getTime() > GRACE_PERIOD) {
              startingPool.remove(instance.getId());
              healthyPool.put(instance.getId(), instance);
            }
          } else {
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
            this.instance.addMetrics(AWSUtils.getCPU(instance.getId()));
            this.instance.setCredit(AWSUtils.getCredit(instance.getId()));
          }
          Thread.sleep(SLEEP_TIME);
        } catch (InterruptedException e) {
          run.set(false);
        }
      }
    }
  }
}
