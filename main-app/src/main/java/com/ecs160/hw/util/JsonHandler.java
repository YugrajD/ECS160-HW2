package com.ecs160.hw.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.util.List;

public class JsonHandler {
    private static final Gson gson = new Gson();

    // Combines JSON strings Microservice A and B into a JSON structure for Microservice C
    public static String createComparatorInput(List<String> summarizedIssues, List<String> bugFinderOutputs) {
        // Process summaries
        JsonArray list1 = new JsonArray();
        for (String json : summarizedIssues) {
            try {
                if (isValidJson(json)) {
                    list1.add(JsonParser.parseString(json));
                }
            } catch (Exception e) {
                System.err.println("Skipping invalid JSON from Summarizer: " + json);
            }
        }

        // Process bug finder outputs
        JsonArray list2 = new JsonArray();
        for (String jsonList : bugFinderOutputs) {
            try {
                if (isValidJson(jsonList)) {
                    JsonElement element = JsonParser.parseString(jsonList);
                    if (element.isJsonArray()) {
                        list2.addAll(element.getAsJsonArray());
                    }
                }
            } catch (Exception e) {
                System.err.println("Skipping invalid JSON from BugFinder: " + jsonList);
            }
        }

        // Combine into [List1, List2]
        JsonArray combined = new JsonArray();
        combined.add(list1);
        combined.add(list2);

        return gson.toJson(combined);
    }
    
    private static boolean isValidJson(String json) {
        return json != null && !json.trim().isEmpty() && !json.trim().equals("{}");
    }
}