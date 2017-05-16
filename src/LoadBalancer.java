package renderfarm;

import java.io.File;
import java.nio.file.Files;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

import raytracer.*;

public class LoadBalancer {

  public static final Integer PORT = 80;

  public static void main(String[] args) throws Exception {
    // Let's bind to all the supported ipv4 interfaces
    HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);
    // Set routes
    server.createContext(WebUtils.RENDER_PATH, new RenderHandler());
    server.createContext(WebUtils.HEALTHCHECK_PATH, new HealthcheckHandler());
    // Multi thread support
    server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
    server.start();
    System.out.println("Server running on port: " + PORT);
  }

  static class HealthcheckHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange req) throws IOException {
      String outResponse = "OK";
      OutputStream os = req.getResponseBody();
      Integer responseCode = 200;
      req.sendResponseHeaders(responseCode, outResponse.length());
      os.write(outResponse.getBytes());
      os.close();
    }
  }
     
  
  static class RenderHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange req) throws IOException {
      String outResponse = "OK";
      OutputStream os = req.getResponseBody();
      Integer responseCode = 200;
      req.sendResponseHeaders(responseCode, outResponse.length());
      os.write(outResponse.getBytes());
      os.close();
    }
  }

}
