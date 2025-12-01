package com.ecs160.microservices;

import com.ecs160.annotations.Microservice;
import com.ecs160.annotations.Endpoint;

@Microservice
public class IssueSummarizerMicroservice {
    private OllamaClient ollama = new OllamaClient();


    @Endpoint(url = "summarize_issue")
    // May need to modify system prompt
    public String summarizeIssue(String issueJson) {
        String systemPrompt = "You are a senior software engineer." +
        "Summarize the following GitHub Issue JSON into a single valid JSON object with these exact fields: " +
        "\"bug_type\" (string), \"line\" (integer; default to 0), " +
        "\"description\" (string), \"filename\" (string; default to N/A).";
        
        System.out.println("Issue summarizer summarizing...");
        return ollama.query(systemPrompt, issueJson);
    }
}