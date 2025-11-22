package com.ecs160;

// Imports launcher from microservice-framework
import com.ecs160.Launcher; 

public class App 
{
    public static void main( String[] args )
    {
        System.out.println("Starting Microservice server on port 8080.");
        Launcher launcher = new Launcher();
        boolean success = launcher.launch(8080);
        
        if (!success) {
            System.err.println("Server failed to start.");
            System.exit(1);
        }
    }
}