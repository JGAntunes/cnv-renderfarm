package renderfarm;

import java.util.*;
import java.util.concurrent.*;

public class RequestCache {

  WeakHashMap<Integer, CachedValue> map;

  public RequestCache() {
    this.map = new WeakHashMap();
  }

  public synchronized int size() {
    return map.size();
  }

  public synchronized CachedValue get(RayTracerRequest request) {
    return map.get(calculateKey(request));
  }

  public synchronized void put(RayTracerRequest request) {
    this.map.put(calculateKey(request), new CachedValue(request.getRealTime()));
  }

  private int calculateKey(RayTracerRequest request) {
    return request.getWindowRows() * request.getWindowColumns();
  }

  public class CachedValue {
    private long time;

    public CachedValue(long time) {
      this.time = time;
    }

    public long getTime() {
      return time;
    }
  }

}
