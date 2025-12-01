package com.ecs160.microservices;

import com.ecs160.annotations.Endpoint;
import com.ecs160.annotations.Microservice;

@Microservice
public class IssueComparatorMicroservice {
    private OllamaClient ollama = new OllamaClient();

    @Endpoint(url = "check_equivalence")
    // May need to modify system prompt
    public String checkEquivalence(String issueJsonArray) {
        String systemPrompt = "You are a senior software engineer. " +
                "I will provide a JSON array containing two lists of bugs: [List A, List B]. " +
                "Your task is to identify overlapping bugs that are present in BOTH lists. " +
                "Return a single valid JSON array containing only the matching bugs. " +
                "If no overlapping bugs are found, return an empty JSON array: []. " +
                "If one list is empty (no bugs), you should default return an empty JSON array [].";

        System.out.println("Issue Comparator comparing...");
        return ollama.query(systemPrompt, issueJsonArray);
    }
}