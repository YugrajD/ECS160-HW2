package com.ecs160;

class Launcher {

    public boolean launch(int port) {
        ClassLoaderHelper classLoaderHelper = new ClassLoaderHelper();
        List<Class<?>> classes = classLoaderHelper.listClassesInAllJarsInOwnDirectory();
        for (Class<?> clazz : classes) {
            if (clazz.isAnnotationPresent(Microservice.class)) {
                Object instance = clazz.getDeclaredConstructor().newInstance();
                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(Endpoint.class)) {
                        Endpoint endpoint = method.getAnnotation(Endpoint.class);
                        String url = endpoint.url();
                    }
                }
            }
        }
        return false;
    }
}
