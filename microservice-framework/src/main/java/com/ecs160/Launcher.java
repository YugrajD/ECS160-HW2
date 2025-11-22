package com.ecs160;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ecs160.annotations.Endpoint;
import com.ecs160.annotations.Microservice;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;


// Changed to public? (review later)
public class Launcher {

    Map<String, Object> instances = new HashMap<>();
    Map<String, Method> methods = new HashMap<>();

    class MyHTTPHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {

            // This will get the URL path which is the endpoint
            String url = exchange.getRequestURI().getPath();
            if (url.startsWith("/")) {
                url = url.substring(1);
            }
            String response;
            if (!methods.containsKey(url)) {
                response = "Endpoint not found";
                exchange.sendResponseHeaders(404, response.length());
                exchange.getResponseBody().write(response.getBytes());
                exchange.getResponseBody().close();
                return;
            }

            Method method = methods.get(url);
            Object instance = instances.get(url);
            
            // I need to test this still, since part C says you are free to only handle GET requests, this was simplest way I could find. There are interesting ways with POST reqeuests. 
            String input = exchange.getRequestURI().getQuery();

            String result;

            // Once we get the method and instance, we try to invoke the method, like in the midterm practice.
            try {
                result = method.invoke(instance, input).toString();
            } catch (Exception e) {
                result = "Error invoking method";
                exchange.sendResponseHeaders(500, result.length());
                exchange.getResponseBody().write(result.getBytes());
                exchange.getResponseBody().close();
                return;
            }

            exchange.sendResponseHeaders(200, result.length());
            exchange.getResponseBody().write(result.getBytes());
            exchange.getResponseBody().close();
        
        }
    }

    public boolean launch(int port) {
        try {
        ClassLoaderHelper classLoaderHelper = new ClassLoaderHelper();
        
        List<Class<?>> classes = classLoaderHelper.listClassesInAllJarsInOwnDirectory();
        for (Class<?> clazz : classes) {
            if (clazz.isAnnotationPresent(Microservice.class)) {
                Object instance = clazz.getDeclaredConstructor().newInstance();
                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(Endpoint.class)) {
                        Endpoint endpoint = method.getAnnotation(Endpoint.class);
                        String url = endpoint.url();

                        instances.put(url, instance);
                        methods.put(url, method);
                    }
                }
            }
        }
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
    
        server.createContext("/", new MyHTTPHandler());

        server.start();
        return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
