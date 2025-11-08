package com.steve.ai.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.steve.ai.SteveMod;
import com.steve.ai.action.Task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResponseParser {
    
    public static ParsedResponse parseAIResponse(String response) {
        if (response == null || response.isEmpty()) {
            return null;
        }

        try {
            String jsonString = extractJSON(response);
            
            JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();
            
            String reasoning = json.has("reasoning") ? json.get("reasoning").getAsString() : "";
            String plan = json.has("plan") ? json.get("plan").getAsString() : "";
            List<Task> tasks = new ArrayList<>();
            
            if (json.has("tasks") && json.get("tasks").isJsonArray()) {
                JsonArray tasksArray = json.getAsJsonArray("tasks");
                
                for (JsonElement taskElement : tasksArray) {
                    if (taskElement.isJsonObject()) {
                        JsonObject taskObj = taskElement.getAsJsonObject();
                        Task task = parseTask(taskObj);
                        if (task != null) {
                            tasks.add(task);
                        }
                    }
                }
            }
            
            if (!reasoning.isEmpty()) {            }
            
            return new ParsedResponse(reasoning, plan, tasks);
            
        } catch (Exception e) {
            SteveMod.LOGGER.error("Failed to parse AI response: {}", response, e);
            return null;
        }
    }

    private static String extractJSON(String response) {
        String cleaned = response.trim();
        
        // Удаляем <think> блоки (LM Studio может добавлять их)
        cleaned = cleaned.replaceAll("(?s)<think>.*?</think>", "");
        cleaned = cleaned.replaceAll("(?s)<think>.*?</think>", "");
        cleaned = cleaned.replaceAll("(?s)<reasoning>.*?</reasoning>", "");
        
        // Ищем JSON объект в тексте (может быть обернут в markdown или текст)
        int jsonStart = cleaned.indexOf("{");
        if (jsonStart != -1) {
            // Находим начало JSON
            cleaned = cleaned.substring(jsonStart);
            
            // Находим конец JSON (последняя закрывающая скобка)
            int depth = 0;
            int jsonEnd = -1;
            for (int i = 0; i < cleaned.length(); i++) {
                char c = cleaned.charAt(i);
                if (c == '{') depth++;
                if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        jsonEnd = i + 1;
                        break;
                    }
                }
            }
            
            if (jsonEnd != -1) {
                cleaned = cleaned.substring(0, jsonEnd);
            }
        }
        
        // Удаляем markdown code blocks
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        
        cleaned = cleaned.trim();
        
        // Удаляем весь текст до первой открывающей скобки JSON
        int firstBrace = cleaned.indexOf("{");
        if (firstBrace > 0) {
            cleaned = cleaned.substring(firstBrace);
        }
        
        // Fix common JSON formatting issues
        cleaned = cleaned.replaceAll("\\n\\s*", " ");
        
        // Fix missing commas between array/object elements (common AI mistake)
        cleaned = cleaned.replaceAll("}\\s+\\{", "},{");
        cleaned = cleaned.replaceAll("}\\s+\\[", "},[");
        cleaned = cleaned.replaceAll("]\\s+\\{", "],{");
        cleaned = cleaned.replaceAll("]\\s+\\[", "],[");
        
        return cleaned;
    }

    private static Task parseTask(JsonObject taskObj) {
        if (!taskObj.has("action")) {
            return null;
        }
        
        String action = taskObj.get("action").getAsString();
        Map<String, Object> parameters = new HashMap<>();
        
        if (taskObj.has("parameters") && taskObj.get("parameters").isJsonObject()) {
            JsonObject paramsObj = taskObj.getAsJsonObject("parameters");
            
            for (String key : paramsObj.keySet()) {
                JsonElement value = paramsObj.get(key);
                
                if (value.isJsonPrimitive()) {
                    if (value.getAsJsonPrimitive().isNumber()) {
                        parameters.put(key, value.getAsNumber());
                    } else if (value.getAsJsonPrimitive().isBoolean()) {
                        parameters.put(key, value.getAsBoolean());
                    } else {
                        parameters.put(key, value.getAsString());
                    }
                } else if (value.isJsonArray()) {
                    List<Object> list = new ArrayList<>();
                    for (JsonElement element : value.getAsJsonArray()) {
                        if (element.isJsonPrimitive()) {
                            if (element.getAsJsonPrimitive().isNumber()) {
                                list.add(element.getAsNumber());
                            } else {
                                list.add(element.getAsString());
                            }
                        }
                    }
                    parameters.put(key, list);
                }
            }
        }
        
        return new Task(action, parameters);
    }

    public static class ParsedResponse {
        private final String reasoning;
        private final String plan;
        private final List<Task> tasks;

        public ParsedResponse(String reasoning, String plan, List<Task> tasks) {
            this.reasoning = reasoning;
            this.plan = plan;
            this.tasks = tasks;
        }

        public String getReasoning() {
            return reasoning;
        }

        public String getPlan() {
            return plan;
        }

        public List<Task> getTasks() {
            return tasks;
        }
    }
}

