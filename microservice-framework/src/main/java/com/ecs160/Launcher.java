package com.ecs160;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;


class Launcher {

    Map<String, Object> instances = new HashMap<>();
    Map<String, Method> methods = new HashMap<>();

    public boolean launch(int port) {
        ClassLoaderHelper classLoaderHelper = new ClassLoaderHelper();

        Map<String, EndpointHandler> routes = new HashMap<>();
        
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
    }
}
