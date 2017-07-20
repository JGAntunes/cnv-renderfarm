package renderfarm;

import com.amazonaws.services.autoscaling.*;
import com.amazonaws.services.autoscaling.model.*;
import com.amazonaws.services.ec2.*;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.dynamodbv2.*;
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.services.dynamodbv2.document.*;

import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

public class AWSUtils {

  public static final String AUTOSCALING_NAME = "asg-renderfarm";

  public static final AmazonAutoScaling autoscalingClient = new AmazonAutoScalingClient();
  public static final AmazonEC2 ec2Client = AmazonEC2ClientBuilder.defaultClient();

  private AWSUtils () {}

  public static List<String> getAvailableInstances () {

    // Get autoscaling group
    DescribeAutoScalingGroupsRequest groupsRequest = new DescribeAutoScalingGroupsRequest()
     .withAutoScalingGroupNames(AUTOSCALING_NAME);
    List<AutoScalingGroup> groups = autoscalingClient
     .describeAutoScalingGroups(groupsRequest)
     .getAutoScalingGroups();

    List<String> instanceIds = new ArrayList();
    List<String> publicIps = new ArrayList();

    // Iterate through instances and get those that are healthy
    for (AutoScalingGroup group : groups) {
      List<com.amazonaws.services.autoscaling.model.Instance> instances = group.getInstances();
      for (com.amazonaws.services.autoscaling.model.Instance instance : instances) {
        if (instance.getHealthStatus().equals("Healthy")) {
          instanceIds.add(instance.getInstanceId());
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
          publicIps.add(instance.getPublicDnsName());
        }
      }
    }

    return publicIps;
  }

  // public static void setupDynamoTables() {
  //   DynamoDB dynamo = new DynamoDB(dynamoClient);
  //   try {
  //     TableDescription table = dynamoClient.describeTable(new DescribeTableRequest(REQUEST_TABLE_NAME))
  //       .getTable();
  //     if (!TableStatus.ACTIVE.toString().equals(table.getTableStatus())) {
  //       throw new ResourceNotFoundException(REQUEST_TABLE_NAME);
  //     }
  //     requestTable = dynamo.getTable(REQUEST_TABLE_NAME);
  //   } catch (ResourceNotFoundException rnfe) {
  //     // This means the table doesn't exist in the account yet
  //     List<AttributeDefinition> attributeDefinitions= new ArrayList<AttributeDefinition>();
  //     attributeDefinitions.add(new AttributeDefinition().withAttributeName("File").withAttributeType("S"));
  //     attributeDefinitions.add(new AttributeDefinition().withAttributeName("Size").withAttributeType("N"));
  //
  //     List<KeySchemaElement> keySchema = new ArrayList<KeySchemaElement>();
  //     keySchema.add(new KeySchemaElement().withAttributeName("File").withKeyType(KeyType.HASH));
  //     keySchema.add(new KeySchemaElement().withAttributeName("Size").withKeyType(KeyType.RANGE));
  //     CreateTableRequest request = new CreateTableRequest()
  //       .withTableName(REQUEST_TABLE_NAME)
  //       .withKeySchema(keySchema)
  //       .withAttributeDefinitions(attributeDefinitions)
  //       .withProvisionedThroughput(new ProvisionedThroughput()
  //           .withReadCapacityUnits(1L)
  //           .withWriteCapacityUnits(1L));
  //
  //     requestTable = dynamo.createTable(request);
  //     try {
  //       requestTable.waitForActive();
  //     } catch (Exception e) {
  //       System.err.println("Error handling table creation " + e.getMessage());
  //     }
  //     System.out.println("Done");
  //   }
  // }

  // public static void addItemToRequest(String fileName, int size, int value) {
  //   if (requestTable == null) {
  //     throw new UnsupportedOperationException("Request table not setup - run setupDynamoTables first");
  //   }
  //
  //   AttributeUpdate update = new AttributeUpdate("value").put(value);
  //   try {
  //     UpdateItemOutcome outcome = requestTable.updateItem("File", fileName,  "Size", size, update);
  //     System.out.println("UpdateItem succeeded:\n");
  //   } catch (Exception e) {
  //     System.err.println("Unable to add item to table " + e.getMessage());
  //   }
  // }
  //
  // public static void getWorstCaseRequest(String fileName, int size) {
  //   if (requestTable == null) {
  //     throw new UnsupportedOperationException("Request table not setup - run setupDynamoTables first");
  //   }
  //
  //   ItemCollection<QueryOutcome> items = requestTable.query("File", fileName, new RangeKeyCondition("Size").ge(size));
  //   Iterator<Item> iter = items.iterator();
  //     System.out.println(iter.next().get("value"));
  // }

  public static void main(String[] args) {
    System.out.println(getAvailableInstances().size());
//  setupDynamoTables();
//    try{
//      addItemToRequest("test01.txt", 1500, 6069);
//    } catch(Exception e) {
//      System.out.println(e.getMessage());
//    }
//    getWorstCaseRequest("test01.txt", 1000);
  }
}
