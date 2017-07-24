package renderfarm;

import com.amazonaws.services.autoscaling.*;
import com.amazonaws.services.autoscaling.model.*;
import com.amazonaws.services.ec2.*;
import com.amazonaws.services.ec2.model.*;

import java.util.*;

public class AWSUtils {

  public static final String AUTOSCALING_NAME = "asg-renderfarm";

  public static final AmazonAutoScaling autoscalingClient = AmazonAutoScalingClientBuilder.defaultClient();
  public static final AmazonEC2 ec2Client = AmazonEC2ClientBuilder.defaultClient();

  private AWSUtils () {}

  public static Map<String, EC2Instance> getAvailableInstances () {

    // Get autoscaling group
    DescribeAutoScalingGroupsRequest groupsRequest = new DescribeAutoScalingGroupsRequest()
     .withAutoScalingGroupNames(AUTOSCALING_NAME);
    List<AutoScalingGroup> groups = autoscalingClient
     .describeAutoScalingGroups(groupsRequest)
     .getAutoScalingGroups();

    List<String> instanceIds = new ArrayList();
    Map<String, EC2Instance> availableInstances = new HashMap();
    Map<String, com.amazonaws.services.autoscaling.model.Instance> autoscalingInstances = new HashMap();

    // Iterate through instances and get those that are healthy
    for (AutoScalingGroup group : groups) {
      List<com.amazonaws.services.autoscaling.model.Instance> instances = group.getInstances();
      for (com.amazonaws.services.autoscaling.model.Instance instance : instances) {
        if (instance.getHealthStatus().equals("Healthy")) {
          instanceIds.add(instance.getInstanceId());
          autoscalingInstances.put(instance.getInstanceId(), instance);
        }
      }
    }

    // Get each all the healthy instances
    DescribeInstancesRequest request = new DescribeInstancesRequest().withInstanceIds(instanceIds);
    DescribeInstancesResult result = ec2Client.describeInstances(request);
    List<Reservation> reservations = result.getReservations();

    // Get the public dns name for each running instance
    for (Reservation reservation : reservations) {
      List<com.amazonaws.services.ec2.model.Instance> instances = reservation.getInstances();

      for (com.amazonaws.services.ec2.model.Instance instance : instances) {
        if (instance.getState().getName().equals("running")) {
          availableInstances.put(instance.getInstanceId(), new EC2Instance(instance));
        }
      }
    }

    return availableInstances;
  }

  public static void setHealthStatus(String instanceId, String status) {
    SetInstanceHealthRequest request = new SetInstanceHealthRequest()
      .withHealthStatus(status)
      .withInstanceId(instanceId);
    SetInstanceHealthResult response = autoscalingClient.setInstanceHealth(request);
  }

  // DEBUG
  public static void main(String[] args) {
    System.out.println(getAvailableInstances().size());
  }
}
