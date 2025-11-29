package com.ecs160;

import static org.junit.Assert.assertTrue;
import org.junit.Test;

import com.ecs160.annotations.Endpoint;
import com.ecs160.annotations.Microservice;

/**
 * Unit test for simple App.
 */
public class AppTest 
{
    /**
     * Rigorous Test :-)
     */

    @Microservice
    public static class FirstMicroservice {
        @Endpoint(url="FirstEndpoint")
        public String handleRequest(String input) {
            return "Hello from First Microservice";
        }
    }

    public static class SecondMicroservice {
        @Endpoint(url="SecondEndpoint")
        public String handleRequest(String input) {
            return "hello " + input;
        }
    }


    @Test
    public void TestMicroserviceFramework() throws Exception
    {
        Launcher launcher = new Launcher();
        FirstMicroservice firstService = new FirstMicroservice();
        launcher.instances.put("FirstEndpoint", firstService);
        launcher.methods.put("FirstEndpoint", FirstMicroservice.class.getMethod("handleRequest", String.class));

        assertTrue(launcher.methods.containsKey("FirstEndpoint"));
        assertTrue(launcher.instances.containsKey("FirstEndpoint"));
    }

    @Test
    public void TestMicroserviceFramework2() throws Exception
    {
        Launcher launcher = new Launcher();
        SecondMicroservice secondService = new SecondMicroservice();
        launcher.instances.put("SecondEndpoint", secondService);
        launcher.methods.put("SecondEndpoint", SecondMicroservice.class.getMethod("handleRequest", String.class));

        String output = (String) launcher.methods.get("SecondEndpoint").invoke(launcher.instances.get("SecondEndpoint"), "World");
        assertTrue(output.equals("hello World"));
    }
}