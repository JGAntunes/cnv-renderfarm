package renderfarm;

import com.amazonaws.services.cloudwatch.*;
import com.amazonaws.services.cloudwatch.model.*;
import com.amazonaws.services.ec2.*;
import com.amazonaws.services.ec2.model.*;

import java.util.*;
import java.lang.Integer;

public class AWSUtils {

  private static final String INSTANCE_TYPE= "t2.micro";

  public static final AmazonEC2 ec2Client = AmazonEC2ClientBuilder.standard().withRegion("eu-west-1").build();
  public static final AmazonCloudWatch cloudWatch = AmazonCloudWatchClientBuilder.standard().withRegion("eu-west-1").build();

  private AWSUtils () {}

  public static Map<String, EC2Instance> getAvailableInstances () {

    Map<String, EC2Instance> availableInstances = new HashMap();

    DescribeInstancesResult result = ec2Client.describeInstances();
    List<Reservation> reservations = result.getReservations();

    // Get the public dns name for each running instance
    for (Reservation reservation : reservations) {
      List<Instance> instances = reservation.getInstances();

      for (Instance instance : instances) {
        if (instance.getState().getName().equals("running")) {
          availableInstances.put(instance.getInstanceId(), new EC2Instance(instance));
        }
      }
    }

    return availableInstances;
  }

  public static List<Datapoint> getMetric(String instanceId , String metric) {
    Dimension instanceDimension = new Dimension();
    instanceDimension.setName("InstanceId");
    instanceDimension.setValue(instanceId);

    // Minimum offset possible
    int offsetInMilliseconds = 10 * 60 * 1000;
    GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
      .withStartTime(new Date(new Date().getTime() - offsetInMilliseconds))
      .withNamespace("AWS/EC2")
      .withPeriod(60)
      .withMetricName(metric)
      .withStatistics("Average")
      .withDimensions(instanceDimension)
      .withEndTime(new Date());

    GetMetricStatisticsResult getMetricStatisticsResult =
      cloudWatch.getMetricStatistics(request);

    List<Datapoint> datapoints = getMetricStatisticsResult.getDatapoints();
    if(datapoints.size() > 1) {
      // Sort datapoints by time
      Collections.sort(datapoints, new Comparator<Datapoint>() {
        @Override
        public int compare(Datapoint o1, Datapoint o2) {
          return o1.getTimestamp().compareTo(o2.getTimestamp());
        }
      });
    }
    return datapoints;
  }

  public static List<Datapoint> getCPU( String instanceId ) {
    return getMetric(instanceId, "CPUUtilization");
  }

  public static int getCredit(String instanceId) {
    List<Datapoint> datapoints = getMetric(instanceId, "CPUCreditBalance");
    if (datapoints.size() == 0) {
      return 0;
    }
    return (int) Math.round(datapoints.get(0).getAverage());
  }

  public static EC2Instance startNewInstance() throws Exception {
    EC2Instance newInstance = null;
    RunInstancesRequest runInstancesRequest =
      new RunInstancesRequest();

    runInstancesRequest.withImageId("ami-a58d62dc")
      .withInstanceType(INSTANCE_TYPE)
      .withMinCount(1)
      .withMaxCount(1)
      .withKeyName("aws_personal")
      .withSecurityGroups("launch-wizard-1");

    RunInstancesResult runInstancesResult = ec2Client.runInstances(runInstancesRequest);
    for (Instance instance : runInstancesResult.getReservation().getInstances()) {
      newInstance = new EC2Instance(instance);
    }
    return newInstance;
  }

  private static void terminateInstance( String instanceId ) throws Exception {
    TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
    termInstanceReq.withInstanceIds(instanceId);
    ec2Client.terminateInstances(termInstanceReq);
  }

  // DEBUG
  public static void main(String[] args) {
    System.out.println(getAvailableInstances().size());
  }
}
