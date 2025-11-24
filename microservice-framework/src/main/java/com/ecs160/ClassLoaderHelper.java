package com.ecs160;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ClassLoaderHelper {

    /**
     * Just call this method to get all classes in the project.
     */
    public List<Class<?>> listClassesInAllJarsInOwnDirectory() {
        List<Class<?>> classes = new ArrayList<>();
        try {
            ClassLoader appClassLoader = Thread.currentThread().getContextClassLoader();
            // List all root URLs from the classloader
            Enumeration<URL> resources = appClassLoader.getResources("");
            while (resources.hasMoreElements()) {
                URL resourceUrl = resources.nextElement();
                if ("file".equals(resourceUrl.getProtocol())) {
                    // Handle directory case
                    try {
                        File dir = new File(resourceUrl.toURI());
                        List<File> classFiles = new ArrayList<>();
                        collectClassFiles(dir, classFiles);
                        for (File classFile : classFiles) {
                            String relativePath = dir.toURI().relativize(classFile.toURI()).getPath();
                            // FIX: Skip module-info and META-INF
                            if (relativePath.contains("module-info") || relativePath.startsWith("META-INF")) continue;

                            String className = relativePath.replace('/', '.').replaceAll("\\.class$", "");
                            try {
                                Class<?> clazz = Class.forName(className);
                                classes.add(clazz);
                            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                                // Ignore
                            }
                        }
                    } catch (java.net.URISyntaxException e) {
                        System.out.println("URISyntaxException: " + e.getMessage());
                    }
                } else if ("jar".equals(resourceUrl.getProtocol())) {
                    // Handle JAR case
                    JarURLConnection jarConnection = (JarURLConnection) resourceUrl.openConnection();
                    JarFile jarFile = jarConnection.getJarFile();
                    Enumeration<JarEntry> entries = jarFile.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String name = entry.getName();
                        
                        if (name.endsWith(".class") && !entry.isDirectory() 
                            && !name.contains("module-info") 
                            && !name.startsWith("META-INF")) {
                            
                            String className = name.replace('/', '.').replaceAll("\\.class$", "");
                            try {
                                Class<?> clazz = Class.forName(className);
                                classes.add(clazz);
                            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                                // Ignore bad classes safely
                            }
                        }
                    }
                }
            } 
        } catch (IOException e ) {
            // Fallback
            classes.add(ClassLoaderHelper.class);
        }
        return classes;
    }

    // Helper to recursively collect all .class files in the directory structure
    private void collectClassFiles(File dir, List<File> classFiles) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectClassFiles(f, classFiles);
            } else if (f.getName().endsWith(".class")) {
                classFiles.add(f);
            }
        }
    }
}