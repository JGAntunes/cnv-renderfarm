package renderfarm;

import java.util.*;
import java.util.concurrent.*;

public class RequestCache {

  ConcurrentNavigableMap<Integer, CachedValue> map;

  public RequestCache() {
    this.map = new ConcurrentSkipListMap();
  }

  public int size() {
    return map.size();
  }

  public Integer get(RayTracerRequest request) {
    if (this.map.size() == 0) {
      return null;
    }
    Map.Entry<Integer, CachedValue> closer = this.map.ceilingEntry(calculateKey(request));
    if (closer == null){
      closer = this.map.floorEntry(calculateKey(request));
    }
    return closer.getValue().getTime();
  }

  public void put(RayTracerRequest request, int time) {
    int key = calculateKey(request);
    CachedValue cachedValue = this.map.get(key);
    // Don't throw away already cached time
    if(cachedValue != null) {
      time = cachedValue.time / 2;
    }
    this.map.put(key, new CachedValue(time));
  }

  private int calculateKey(RayTracerRequest request) {
    return request.getWindowRows() * request.getWindowColumns();
  }

  private class CachedValue {
    private int time;

    public CachedValue(int time) {
      this.time = time;
    }

    public int getTime() {
      return time;
    }
  }

}
