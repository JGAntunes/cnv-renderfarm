package renderfarm;

import java.util.concurrent.*;

public class RequestCache {

  ConcurrentNavigableMap<Integer, CachedValue> map;

  public RequestCache() {
    this.map = new ConcurrentSkipListMap();
  }

  public int size() {
    return map.size();
  }

  public CachedValue get(RayTracerRequest request) {
    return this.map.ceilingEntry(calculateKey(request)).getValue();
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
