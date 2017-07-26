/* 2016-04 Edited by Luis Veiga and Joao Garcia */
import java.util.Comparator;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.amazonaws.auth.AWSStaticCredentialsProvider;

import java.util.ArrayList;
import java.util.Date; 
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;

public class EC2LaunchMeasureCPU {

  static AmazonEC2      ec2;
  static AmazonCloudWatch cloudWatch;

  private static final double CPU_HIGH_THRESHOLD = 0.6;
  private static final double CPU_LOW_THRESHOLD = 0.3;
  private static final long SCAN_INTERVAL = 60*1000;
  private static final String INSTANCE_TYPE= "t2.nano";

  private static void init() throws Exception {
    AWSCredentials credentials = null;
    try {
      credentials = new ProfileCredentialsProvider().getCredentials();
    } catch (Exception e) {
      throw new AmazonClientException(
          "Cannot load the credentials from the credential profiles file. " +
          "Please make sure that your credentials file is at the correct " +
          "location (~/.aws/credentials), and is in valid format.",
          e);
    }
    ec2 = AmazonEC2ClientBuilder.standard().withRegion("eu-west-1").withCredentials(new AWSStaticCredentialsProvider(credentials)).build();

    cloudWatch = AmazonCloudWatchClientBuilder.standard().withRegion("eu-west-1").withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
  }

  private static void startNewInstance() throws Exception {
    System.out.println(" Starting new instance ");
    RunInstancesRequest runInstancesRequest =
      new RunInstancesRequest();

    runInstancesRequest.withImageId("ami-a58d62dc")
      .withInstanceType(INSTANCE_TYPE)
      .withMinCount(1)
      .withMaxCount(1)
      .withKeyName("aws_personal")
      .withSecurityGroups("launch-wizard-1");

    RunInstancesResult runInstancesResult =
      ec2.runInstances(runInstancesRequest);
  }

  public static void main(String[] args) throws Exception { 

    init();

    try {
      DescribeInstancesResult describeInstancesResult = ec2.describeInstances();
      List<Reservation> reservations = describeInstancesResult.getReservations();
      Set<Instance> instances = new HashSet<Instance>();

      for (Reservation reservation : reservations) {
        instances.addAll(reservation.getInstances());
      }
      System.out.println("total instances = " + instances.size());
      while(true){
        long offsetInMilliseconds = 1000 * 60 * 5;
        Dimension instanceDimension = new Dimension();
        instanceDimension.setName("InstanceId");
        for (Instance instance : instances) {
          String name = instance.getInstanceId();
          String state = instance.getState().getName();
          if (!state.equals("running")) { 

            // TODO
            // remove from healthy pool if there

          } else {

            // TODO
            // add to healthy pool if not in there

            System.out.println("running instance id = " + name);
            instanceDimension.setValue(name);

            GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
              .withStartTime(new Date(new Date().getTime() - offsetInMilliseconds))
              .withNamespace("AWS/EC2")
              .withPeriod(120)
              .withMetricName("CPUUtilization")
              .withStatistics("Average")
              .withDimensions(instanceDimension)
              .withEndTime(new Date());

            GetMetricStatisticsResult getMetricStatisticsResult = 
              cloudWatch.getMetricStatistics(request);

            List<Datapoint> datapoints = getMetricStatisticsResult.getDatapoints();
            if(datapoints.size() != 0) {
              if(datapoints.size() > 1) {

                Collections.sort(datapoints, new Comparator<Datapoint>() {
                  @Override
                  public int compare(Datapoint o1, Datapoint o2) {
                    return o1.getTimestamp().compareTo(o2.getTimestamp());
                  }
                });

              }
              Datapoint dp = datapoints.get(0);
              System.out.println(dp.getTimestamp() + " CPU utilization for instance " + name + " = " + dp.getAverage());
              if( dp.getAverage() >= CPU_HIGH_THRESHOLD ) {
                startNewInstance();

                // if high cpu for two consecutive metrics
                // increase core

              }
              if( dp.getAverage() >= CPU_LOW_THRESHOLD ) {

                // tell scheduler not o send traffic to this machine
                // finish all requests
                // if it is a m3.medium and t2 core has credit
                // remove m3.medium
                // else 
                // keep
                // 
                // if low cpu for two consecutive metrics
                // reduce core (never bellow CORE_MIN = 2)

              }
            }
          }
          Thread.sleep(SCAN_INTERVAL);
        }
      }
    } catch (AmazonServiceException ase) {
      System.out.println("Caught Exception: " + ase.getMessage());
      System.out.println("Reponse Status Code: " + ase.getStatusCode());
      System.out.println("Error Code: " + ase.getErrorCode());
      System.out.println("Request ID: " + ase.getRequestId());
    }
  }
}
