package renderfarm;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Date;
import java.util.Collection;
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

  public boolean runHealthCheck () {
    String url = "http://" + this.publicDnsName + WebUtils.HEALTHCHECK_PATH;
    int currentRetries = 0;
    for(int i = 0; i < MAX_RETRIES; i++) {
      try {
        HttpURLConnection conn = WebUtils.request("GET", url, 1000);
        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
          return true;
        } else {
          logger.warning("Healthcheck returned " + responseCode);
          logger.warning("Instance flagged as unhealthy");
          return false;
        }
      } catch (Exception e) {
        logger.warning("Healthcheck endpoint inaccessible - " + e.getMessage() + " - retry" + currentRetries);
      }
    }
    // Nothing to do but to set the instance as unealthy
    logger.warning("Instance flagged as unhealthy");
    return false;
  }

  @Override
  public String toString() {
    return this.id + " " + this.publicDnsName;
  }
}
