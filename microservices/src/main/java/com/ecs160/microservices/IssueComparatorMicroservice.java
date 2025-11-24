package com.ecs160.microservices;

import com.ecs160.annotations.Microservice;
import com.ecs160.annotations.Endpoint;

@Microservice
public class IssueComparatorMicroservice {
    private OllamaClient ollama = new OllamaClient();

    @Endpoint(url = "check_equivalence")
    // May need to modify system prompt
    public String checkEquivalence(String issueJsonArray) {
        String systemPrompt = "I will provide a JSON array containing two lists of bugs. " +
                "Compare both lists and return a new JSON list with show up in BOTH lists. ";

        System.out.println("Issue Comparator comparing...");
        return ollama.query(systemPrompt, issueJsonArray);
    }
}