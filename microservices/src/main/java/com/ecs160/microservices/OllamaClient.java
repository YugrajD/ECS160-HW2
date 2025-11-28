package com.ecs160.microservices;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class OllamaClient {
    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final String MODEL = "deepcoder:1.5b"; 

    public String query(String systemPrompt, String userQuery) {
        try {
            // Fix for quotation marks, back slashes, new lines
            String cleanedQuery = userQuery.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
            // Fix for quotation marks, back slashes, new lines
            String cleanedPrompt = systemPrompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
            String fullPrompt = cleanedPrompt + "\\n" + cleanedQuery;

            String jsonBody = String.format(
                "{\"model\": \"%s\", \"prompt\": \"%s\", \"stream\": false, \"format\": \"json\"}",
                MODEL, fullPrompt
            );

            // Sends request
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_URL))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Parse response using Gson
            JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
            
            if (jsonResponse.has("response")) {
                return jsonResponse.get("response").getAsString();
            } else {
                return "{}";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "{\"error\": \"Failed to connect to Ollama: " + e.getMessage() + "\"}";
        }
    }
}