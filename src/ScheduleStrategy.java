package renderfarm;

import java.util.*;

public interface ScheduleStrategy {

  public String execute(RayTracerRequest request, List<EC2Instance> instances) throws NoAvailableInstancesException;
}
