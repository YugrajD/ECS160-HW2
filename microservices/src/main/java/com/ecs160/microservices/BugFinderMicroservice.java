package com.ecs160.microservices;

import com.ecs160.annotations.Microservice;
import com.ecs160.annotations.Endpoint;

@Microservice
public class BugFinderMicroservice {

    private OllamaClient ollama = new OllamaClient();

    @Endpoint(url = "find_bugs")
    // May need to modify system prompt
    public String findBugs(String code) {
        String systemPrompt = "You are a senior software engineer." +
                "Analyze the following C++ code for bugs. " +
                "Return the result as a valid JSON LIST of objects in the format: \"bug_type\", \"line\", \"description\", \"filename\". " +
                "If there are no bugs, return an empty list.";

        System.out.println("Bug Finder locating bugs...");
        return ollama.query(systemPrompt, code);
    }
}