package renderfarm;

import java.nio.file.Files;
import java.io.*;
import java.net.*;
import java.util.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import raytracer.*;

public class LoadBalancer {

  private static final Integer PORT = 80;
  private static final Logger logger = new Logger("load-balancer");
  private static final Scheduler scheduler = new Scheduler(new RoundRobinStrategy());

  public static void main(String[] args) throws Exception {
    // Let's bind to all the supported ipv4 interfaces
    HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);
    // Set routes
    server.createContext(WebUtils.RENDER_PATH, new RenderHandler());
    server.createContext(WebUtils.HEALTHCHECK_PATH, new HealthcheckHandler());
    // Multi thread support
    server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
    server.start();
    logger.log("Server running on port: " + PORT);
  }

  private static class HealthcheckHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange req) throws IOException {
      WebUtils.simpleReply(req, 200, "OK");
    }
  }


  private static class RenderHandler implements HttpHandler {
    private static final Logger handlerLogger = logger.getChild("render-handler");

    @Override
    public void handle(HttpExchange req) throws IOException {
      Timer requestTimer = new Timer();

      // Parse the request fields
      RayTracerRequest request = new RayTracerRequest(req);
      Logger requestLogger = handlerLogger.getChild("req-id: " + request.getId());
      requestLogger.debug("Got request " + request.getPath());
      // Get the instance hostname to use
      RayTracerResponse response;
      try {
        response = scheduler.schedule(request);
      } catch(NoAvailableInstancesException e) {
        requestLogger.error("No available workers, terminating request");
        WebUtils.simpleReply(req, 500, "No available workers");
        return;
      }

      InputStream is = response.getInputStream();
      int contentLength = response.getContentLength();
      int responseCode = response.getResponseCode();

      OutputStream os = req.getResponseBody();
      req.sendResponseHeaders(responseCode, contentLength);
      try {
        WebUtils.pipe(is, os);
      }
      catch (Exception e) {
        String outError = "Internal Server Error: " + e.getMessage();
        requestLogger.error(outError);
        WebUtils.simpleReply(req, 500, outError);
      } finally {
        is.close();
        os.close();
        requestLogger.debug("Request done in " + requestTimer.getTime() + "ms");
      }
    }
  }

}
