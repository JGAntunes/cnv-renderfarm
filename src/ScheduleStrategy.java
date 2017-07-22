package renderfarm;

import java.util.*;

public interface ScheduleStrategy {

  public String execute(RayTracerRequest request, List<String> instances) throws NoAvailableInstancesException;
}
