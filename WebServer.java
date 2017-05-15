import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import java.util.HashMap;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class WebServer {

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/test", new MyHandler());
        server.setExecutor(null); // creates a default executor
        server.start();
    }

    static class MyHandler implements HttpHandler {
	private HashMap<String, String> list= new HashMap<String, String>();
        
	@Override
        public void handle(HttpExchange t) throws IOException {
            String response =  t.getRequestURI().getQuery();
            createResponse(response);

	    t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
	public void createResponse(String response){
		if(response != null){
			String[] responseSplited = response.split("&");
			if(responseSplited.length == 7){
				for(int i=0;i<responseSplited.length;i++){
					String[] equalSplitter = responseSplited[i].split("=");
					list.put(equalSplitter[0],equalSplitter[1]);
					//call the raytracer her						
				}
			}
		
		}
		
	}


    }
	
}
