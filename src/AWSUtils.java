package renderfarm;

import com.amazonaws.services.autoscaling.*;
import com.amazonaws.services.autoscaling.model.*;
import com.amazonaws.services.ec2.*;
import com.amazonaws.services.ec2.model.*;

import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

public class AWSUtils {
  
  public static final String AUTOSCALING_NAME = "cnv";
  public static final AmazonAutoScaling autoscalingClient = new AmazonAutoScalingClient();
  public static final AmazonEC2 ec2Client = AmazonEC2ClientBuilder.defaultClient();

  private AWSUtils () {}

  public static List<String> getAvailableInstances () {

    DescribeAutoScalingGroupsRequest groupsRequest = new DescribeAutoScalingGroupsRequest()
     .withAutoScalingGroupNames(AUTOSCALING_NAME);
    List<AutoScalingGroup> groups = autoscalingClient
     .describeAutoScalingGroups(groupsRequest)
     .getAutoScalingGroups();

    List<String> instanceIds = new ArrayList();
    List<String> publicIps = new ArrayList();

    for (AutoScalingGroup group : groups) {
      List<com.amazonaws.services.autoscaling.model.Instance> instances = group.getInstances();
      for (com.amazonaws.services.autoscaling.model.Instance instance : instances) {
        if (instance.getHealthStatus().equals("Healthy")) {
          instanceIds.add(instance.getInstanceId());
        }       
      }
    }
    
    DescribeInstancesRequest request = new DescribeInstancesRequest().withInstanceIds(instanceIds);
    DescribeInstancesResult result = ec2Client.describeInstances(request);
    
    List<Reservation> reservations = result.getReservations();
    for (Reservation reservation : reservations) {
      List<com.amazonaws.services.ec2.model.Instance> instances = reservation.getInstances();

      for (com.amazonaws.services.ec2.model.Instance instance : instances) {
        if (instance.getState().getName().equals("running")) {
          publicIps.add(instance.getPublicDnsName());
        }
      }
    }

    return publicIps;
  }

  public static void main(String[] args) {
    System.out.println(getAvailableInstances().size());
  }
}
