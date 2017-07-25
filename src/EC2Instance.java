package renderfarm;

import java.net.HttpURLConnection;
import java.util.Date;
import java.util.concurrent.*;

import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.autoscaling.model.*;

public class EC2Instance {

  private static final int MAX_RETRIES = 3;
  private static final Logger classLogger = new Logger("ec2-instance");

  private Logger logger;
  private String id;
  private String publicDnsName;
  private String type;
  private Date launchTime;
  private Thread pollingThread;
  private boolean isHealthy;

  public EC2Instance(com.amazonaws.services.ec2.model.Instance ec2Instance) {
    this.isHealthy = true;
    this.id = ec2Instance.getInstanceId();
    this.publicDnsName = ec2Instance.getPublicDnsName();
    this.type = ec2Instance.getInstanceType();
    this.launchTime = ec2Instance.getLaunchTime();
    this.logger = classLogger.getChild(this.id);
  }

  public String getId() {
    return id;
  }

  public String getPublicDnsName() {
    return publicDnsName;
  }

  public String getType() {
    return type;
  }

  public Date getLaunchTime() {
    return launchTime;
  }

  public boolean isHealthy() {
    return isHealthy;
  }

  public void setHealthStatus(boolean status) {
    String healthStatus = status ? "Healthy" : "Unhealthy";
    if (this.isHealthy == status) {
      return;
    }
    this.isHealthy = status;
    // Ensure proper clean up of the poller
    if (!status) {
      stopPoller();
    } else {
      startPoller();
    }
    AWSUtils.setHealthStatus(this.id, healthStatus);
  }

  // Start/stop the poller
  public void monitor(boolean start) {
    if (start) {
      if (this.pollingThread == null || !this.pollingThread.isAlive()) {
        startPoller();
      }
    } else {
      if (this.pollingThread != null && this.pollingThread.isAlive()) {
        stopPoller();
      }
    }
  }

  private void startPoller () {
    Poller poller = new Poller();
    this.pollingThread = new Thread(poller);
    this.pollingThread.start();
  }

  private void stopPoller () {
    logger.debug("EC2 monitor stopping");
    this.pollingThread.interrupt();
    // Wait 200 milis to for the thread to die, else keep on moving
    try {
      this.pollingThread.join(200);
    } catch (InterruptedException e) {
      return;
    }
  }

  private void runHealthCheck () {
    String url = "http://" + this.publicDnsName + WebUtils.HEALTHCHECK_PATH;
    int currentRetries = 0;
    for(int i = 0; i < MAX_RETRIES; i++) {
      try {
        HttpURLConnection conn = WebUtils.request("GET", url, 1000);
        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
          setHealthStatus(true);
        } else {
          logger.debug("Healthcheck returned " + responseCode);
          logger.debug("Instance flagged as unhealthy");
          setHealthStatus(false);
        }
        return;
      } catch (Exception e) {
        logger.debug("Healthcheck endpoint inaccessible - " + e.getMessage() + " - retry" + currentRetries);
      }
    }
    // Nothing to do but to set the instance as unealthy
    logger.debug("Instance flagged as unhealthy");
    setHealthStatus(false);
  }

  @Override
  public String toString() {
    return this.id + " " + this.publicDnsName;
  }

  // Clean up thread on GC swipe
  @Override
  protected void finalize() throws Throwable {
    stopPoller();
  };

  public class Poller implements Runnable {
    private static final int SLEEP_TIME = 10000;

    @Override
    public void run() {
      logger.debug("EC2 monitor starting");
      boolean run = true;
      while(run) {
        try{
          logger.debug("EC2 monitor running");
          runHealthCheck();
          Thread.sleep(SLEEP_TIME);
        } catch (InterruptedException e) {
          run = false;
        }
      }
    }
  }
}
