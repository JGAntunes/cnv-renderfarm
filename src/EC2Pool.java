package renderfarm;

public class EC2Pool {

  private static final Logger classLogger = new Logger("ec2-pool");

  // Active instance pool
  private ConcurrentHashMap<String, EC2Instance> healthyPool;
  // Pool of instances whose health checks actually failed
  private ConcurrentHashMap<String, EC2Instance> unhealthyPool;
  // Pool of instances flagged by the scheduler as unable to reply, need to be checked
  private ConcurrentHashMap<String, EC2Instance> flaggedPool;
  // Pool of instances marked for termination
  private ConcurrentHashMap<String, EC2Instance> terminatingPool;
  private ExecutorService threadPool;

  public EC2Pool() {
    // TODO Get initial status for instances
    this.healthyPool = new ConcurrentHashMap();
    this.unhealthyPool = new ConcurrentHashMap();
    this.terminatingPool = new ConcurrentHashMap();
    this.threadPool = Executor.newCachedThreadPool();
  }

  public Collection<EC2Instance> getAvailableInstances() {
    return healthyPool.values();
  }

  private class EC2Poller implements Runnable {
    private static final int SLEEP_TIME = 10000;

    private EC2Instance instance;

    public EC2Poller (EC2Instance instance) {
      this.instance = instance;
    }

    private void healthCheck(boolean reportedStatus) {
      boolean isHealthy = this.instance.runHealthCheck();
      if (reportedStatus == null || isHealthy != reportedStatus) {
        if (isHealthy) {
          unealthyPool.remove(this.instance.getId());
          healthyPool.put(this.instance.getId(), this.instance);
        } else {
          unealthyPool.remove(this.instance.getId());
          healthyPool.put(this.instance.getId(), this.instance);
        }
      }
    }

    @Override
    public void run() {
      logger.debug("EC2 monitor starting");
      boolean run = true;
      while(run) {
        try{
          Thread.sleep(SLEEP_TIME);
          logger.debug("EC2 monitor running");
          if (terminatingPool.containsKey(instance.getId())) {
            if (instance.getInFlightRequests().size() <= 0) {
              // TODO terminate instance
            }
            continue;
          }
          boolean currentStatus = null;
          if (healthyPool.containsKey(instance.getId())) {
            currentStatus = true;
          } else if (unealthyPool.containsKey(instance.getId())) {
            currentStatus = false;
          } else if (flaggedPool.containsKey(instance.getId())) {
            flaggedPool.remove(instance.getId());
          }
          healthCheck(currentStatus);
        } catch (InterruptedException e) {
          run = false;
        }
      }
    }
  }
}
