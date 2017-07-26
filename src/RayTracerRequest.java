package renderfarm;

import java.util.Map;
import java.util.UUID;
import com.sun.net.httpserver.HttpExchange;

public class RayTracerRequest {

  private static final String MODEL_FILENAME_PARAM = "f";
  private static final String SCENE_COLUMNS_PARAM = "sc";
  private static final String SCENE_ROWS_PARAM = "sr";
  private static final String WINDOW_COLUMNS_PARAM = "wc";
  private static final String WINDOW_ROWS_PARAM = "wr";
  private static final String COLUMNS_OFFSET_PARAM = "coff";
  private static final String ROWS_OFFSET_PARAM = "roff";

  private String id;
  private int scols;
  private int srows;
  private int wcols;
  private int wrows;
  private int coff;
  private int roff;
  private String fileName;
  private String path;
  private boolean isFinished;
  private long realTime;
  private long expectedTime;


  public RayTracerRequest(HttpExchange req) {
    Map<String,String> params = WebUtils.getQueryParameters(req);
    this.expectedTime = this.realTime = -1;
    this.id = UUID.randomUUID().toString();
    this.path = req.getRequestURI().toString();
    this.scols = Integer.parseInt(params.get(SCENE_COLUMNS_PARAM));
    this.srows = Integer.parseInt(params.get(SCENE_ROWS_PARAM));
    this.wcols = Integer.parseInt(params.get(WINDOW_COLUMNS_PARAM));
    this.wrows = Integer.parseInt(params.get(WINDOW_ROWS_PARAM));
    this.coff = Integer.parseInt(params.get(COLUMNS_OFFSET_PARAM));
    this.roff = - Integer.parseInt(params.get(ROWS_OFFSET_PARAM));
    this.fileName = params.get(MODEL_FILENAME_PARAM);
  }

  public String getId() {
    return this.id;
  }

  public int getSceneColumns() {
    return this.scols;
  }

  public int getSceneRows() {
    return this.srows;
  }

  public int getWindowColumns() {
    return this.wcols;
  }

  public int getWindowRows() {
    return this.wrows;
  }

  public int getColumnsOffset() {
    return this.coff;
  }

  public int getRowsOffset() {
    return this.roff;
  }

  public String getFileName() {
    return this.fileName;
  }

  public String getPath() {
    return this.path;
  }

  public boolean isFinished() {
    return this.isFinished;
  }

  public long getRealTime() throws IllegalStateException {
    if (this.realTime > 0) {
      return this.realTime;
    } else {
      throw new IllegalStateException("Request must be finished first");
    }
  }

  public void setExpectedTime(long time) {
    this.expectedTime = time;
  }

  public long getExpectedTime() throws IllegalStateException {
    if (this.expectedTime > 0) {
      return this.expectedTime;
    } else {
      throw new IllegalStateException("Expected time not set");
    }
  }

  public void done(long realTime) {
    this.realTime = realTime;
    this.isFinished = true;
  }
}
