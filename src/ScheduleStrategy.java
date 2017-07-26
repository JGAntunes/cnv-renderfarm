package renderfarm;

import java.util.*;

public interface ScheduleStrategy {

  public EC2Instance execute(RayTracerRequest request, List<EC2Instance> instances, RequestCache requestCache)
    throws NoAvailableInstancesException, IllegalStateException;
}
