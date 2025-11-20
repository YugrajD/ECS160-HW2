package com.ecs160;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.util.*;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.io.IOException;



class Launcher {

    Map<String, Object> instances = new HashMap<>();
    Map<String, Method> methods = new HashMap<>();

    class MyHTTPHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "The server is running!!";
            String url = exchange.getRequestURI().getPath();
            if (url.startsWith("/")) {
                url = url.substring(1);
            }
            if (!methods.containsKey(url)) {
                response = "Endpoint not found";
                exchange.sendResponseHeaders(404, response.length());
                exchange.getResponseBody().write(response.getBytes());
                exchange.getResponseBody().close();
                return;
            }
            Method method = methods.get(url);
            Object instance = instances.get(url);
            String input = exchange.getRequestURI().getQuery();
            if (input == null) {
                input = "";
            }
            String result;
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
