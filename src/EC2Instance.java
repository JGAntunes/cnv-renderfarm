package renderfarm;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Date;
import java.util.AbstractQueue;
import java.util.concurrent.*;

import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.autoscaling.model.*;
import com.amazonaws.services.cloudwatch.model.Datapoint;

public class EC2Instance {

  private static final int MAX_RETRIES = 3;
  private static final int RECORD_SIZE = 10;
  private static final Logger classLogger = new Logger("ec2-instance");

  private Logger logger;
  private String id;
  private String publicDnsName;
  private String type;
  private Date launchTime;
  private Thread pollingThread;
  private AtomicInteger credit;
  private CopyOnWriteArrayList<Datapoint> metrics;
  private ConcurrentLinkedQueue<RayTracerRequest> inFlightRequests;

  public EC2Instance(com.amazonaws.services.ec2.model.Instance ec2Instance) {
    this.inFlightRequests = new ConcurrentLinkedQueue();
    this.credit = new AtomicInteger(0);
    this.metrics = new CopyOnWriteArrayList<Datapoint>();
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

  public int getCredit() {
    return credit.get();
  }

  public Datapoint getMetric(int index) {
    metrics.get(index);
  }

  public int getCPU() {
    if (metrics.size() == 0) {
      return 0;
    }
    return getMetric(0).getAverage();
  }

  public AbstractQueue getInFlightRequests() {
    return inFlightRequests;
  }

  public void setCredit( int c ) {
    credit.set();
  }

  public void addMetric(Datapoint dp) {
    if (metrics.size() >= RECORD_SIZE) {
      metric = metric.subList(0, RECORD_SIZE); 
    }
    metrics.add(0, dp);
  }

  public void addMetrics(Collection<Datapoint> dps) {
    metrics.addAll(0, dps);

    if (metrics.size() >= RECORD_SIZE) {
      metrics = metrics.subList(0, RECORD_SIZE); 
    }
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

  public RayTracerResponse processRequest(RayTracerRequest request) throws IOException {
    // Register inflight request
    this.inFlightRequests.add(request);
    Logger requestLogger = logger.getChild("req-id: " + request.getId());
    requestLogger.debug("Selected worker: " + this.publicDnsName);
    logger.debug("Current request load: " + this.inFlightRequests.size());
    // Assemble the URL
    String url = "http://" + this.publicDnsName + request.getPath();
    // Make the request
    requestLogger.debug("Sending request: " + url);
    Timer workerTimer = new Timer();
    HttpURLConnection conn = WebUtils.request("GET", url, 5000);

    RayTracerResponse response = new RayTracerResponse(conn);
    long finishTime = workerTimer.getTime();
    request.done(finishTime);
    requestLogger.debug("Worker processed request in " + finishTime + "ms");
    // Deregister request
    this.inFlightRequests.remove(request);
    logger.debug("Remaining request load: " + this.inFlightRequests.size());
    // Reply
    return response;
  }

  private void setHealthStatus(boolean status) {
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

  private void startPoller () {
    Poller poller = new Poller();
    if (this.pollingThread != null) {
      stopPoller();
    }
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
          logger.warning("Healthcheck returned " + responseCode);
          logger.warning("Instance flagged as unhealthy");
          setHealthStatus(false);
        }
        return;
      } catch (Exception e) {
        logger.warning("Healthcheck endpoint inaccessible - " + e.getMessage() + " - retry" + currentRetries);
      }
    }
    // Nothing to do but to set the instance as unealthy
    logger.warning("Instance flagged as unhealthy");
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
          List<Datapoints> datapoints= AWSUtils.getCPU(this.id);
          addMetrics(datapoints);
          setCredit(AWSUtils.getCredit());
          Thread.sleep(SLEEP_TIME);
        } catch (InterruptedException e) {
          run = false;
        }
      }
    }
  }
}
