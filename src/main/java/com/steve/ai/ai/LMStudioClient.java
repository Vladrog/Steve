package com.steve.ai.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.steve.ai.SteveMod;
import com.steve.ai.config.SteveConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Client for LM Studio local API
 * LM Studio provides an OpenAI-compatible API endpoint
 * Default URL: http://localhost:1234/v1/chat/completions
 * No API key required for local usage
 */
public class LMStudioClient {
    private static final int MAX_RETRIES = 3;
    private static final int INITIAL_RETRY_DELAY_MS = 1000; // 1 second

    private final HttpClient client;
    private final String apiUrl;
    private final String model;

    public LMStudioClient() {
        this.apiUrl = SteveConfig.LMSTUDIO_API_URL.get();
        this.model = SteveConfig.LMSTUDIO_MODEL.get();
        this.client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1) // Явно используем HTTP/1.1 для совместимости
            .connectTimeout(Duration.ofSeconds(5)) // Короткий таймаут подключения
            .build();
        
        SteveMod.LOGGER.info("LMStudioClient initialized with URL: {}, Model: {}", apiUrl, model);
    }

    public String sendRequest(String systemPrompt, String userPrompt) {
        SteveMod.LOGGER.info("LMStudioClient.sendRequest called");
        SteveMod.LOGGER.debug("API URL: {}, Model: {}", apiUrl, model);
        
        if (apiUrl == null || apiUrl.isEmpty()) {
            SteveMod.LOGGER.error("LM Studio API URL not configured!");
            return null;
        }

        if (model == null || model.isEmpty()) {
            SteveMod.LOGGER.warn("LM Studio model name not configured! Using empty model name. " +
                "Make sure to set the model name in config/steve-common.toml under [lmstudio] section.");
        }

        JsonObject requestBody = buildRequestBody(systemPrompt, userPrompt);
        String requestBodyStr = requestBody.toString();
        SteveMod.LOGGER.info("Request body size: {} chars, System prompt: {} chars, User prompt: {} chars", 
            requestBodyStr.length(), systemPrompt.length(), userPrompt.length());
        
        // Логируем начало запроса для диагностики (первые 500 символов)
        String requestPreview = requestBodyStr.length() > 500 
            ? requestBodyStr.substring(0, 500) + "..." 
            : requestBodyStr;
        SteveMod.LOGGER.info("Request preview: {}", requestPreview);

        // LM Studio doesn't require API key, but some setups might use it
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .header("Content-Type", "application/json; charset=UTF-8") // Явно указываем кодировку
            .timeout(Duration.ofSeconds(60)) // Таймаут для локальных моделей
            .POST(HttpRequest.BodyPublishers.ofString(requestBodyStr, java.nio.charset.StandardCharsets.UTF_8));

        // Add API key header only if configured (optional for LM Studio)
        String apiKey = SteveConfig.LMSTUDIO_API_KEY.get();
        if (apiKey != null && !apiKey.isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }

        HttpRequest request = requestBuilder.build();
        
        SteveMod.LOGGER.info("Sending request to LM Studio at: {}", apiUrl);
        SteveMod.LOGGER.info("Request URI: {}", request.uri());
        SteveMod.LOGGER.info("Request method: {}", request.method());
        SteveMod.LOGGER.info("Request headers: {}", request.headers().map());
        
        // Проверяем, что тело запроса не пустое
        if (requestBodyStr == null || requestBodyStr.isEmpty()) {
            SteveMod.LOGGER.error("Request body is empty!");
            return null;
        }
        SteveMod.LOGGER.info("Request body length: {} bytes", requestBodyStr.getBytes().length);

        // Retry logic with exponential backoff
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                SteveMod.LOGGER.info("Attempting to connect to LM Studio (attempt {}/{})", attempt + 1, MAX_RETRIES);
                long startTime = System.currentTimeMillis();
                
                // Проверяем, можем ли мы вообще подключиться
                SteveMod.LOGGER.info("Calling client.send()...");
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                long duration = System.currentTimeMillis() - startTime;
                SteveMod.LOGGER.info("Received response with status code: {} (took {}ms)", response.statusCode(), duration);

                if (response.statusCode() == 200) {
                    String responseBody = response.body();
                    if (responseBody == null || responseBody.isEmpty()) {
                        SteveMod.LOGGER.error("LM Studio API returned empty response");
                        return null;
                    }
                    SteveMod.LOGGER.info("LM Studio API returned successful response (body size: {} chars)", responseBody.length());
                    SteveMod.LOGGER.debug("Full response body: {}", responseBody);
                    
                    String parsed = parseResponse(responseBody);
                    if (parsed != null) {
                        SteveMod.LOGGER.info("Successfully parsed response from LM Studio (content length: {} chars)", parsed.length());
                        // Логируем первые 200 символов ответа для диагностики
                        String preview = parsed.length() > 200 ? parsed.substring(0, 200) + "..." : parsed;
                        SteveMod.LOGGER.info("Response preview: {}", preview);
                    } else {
                        SteveMod.LOGGER.error("Failed to parse response from LM Studio");
                        SteveMod.LOGGER.error("Response body was: {}", responseBody);
                    }
                    return parsed;
                }

                // Log non-200 responses for debugging
                String errorBody = response.body();
                SteveMod.LOGGER.error("LM Studio returned status code: {}", response.statusCode());
                if (errorBody != null && !errorBody.isEmpty()) {
                    SteveMod.LOGGER.error("Error response body: {}", errorBody);
                    // Try to parse error message from JSON
                    try {
                        JsonObject errorJson = JsonParser.parseString(errorBody).getAsJsonObject();
                        if (errorJson.has("error")) {
                            JsonObject error = errorJson.getAsJsonObject("error");
                            if (error.has("message")) {
                                SteveMod.LOGGER.error("LM Studio error message: {}", error.get("message").getAsString());
                            }
                        }
                    } catch (Exception e) {
                        // Ignore JSON parsing errors
                    }
                }

                // Check if error is retryable (server error)
                if (response.statusCode() >= 500) {
                    if (attempt < MAX_RETRIES - 1) {
                        int delayMs = INITIAL_RETRY_DELAY_MS * (int) Math.pow(2, attempt);
                        SteveMod.LOGGER.warn("LM Studio API request failed with status {}, retrying in {}ms (attempt {}/{})",
                            response.statusCode(), delayMs, attempt + 1, MAX_RETRIES);
                        Thread.sleep(delayMs);
                        continue;
                    }
                }

                // Non-retryable error or final attempt
                SteveMod.LOGGER.error("LM Studio API request failed: {}", response.statusCode());
                SteveMod.LOGGER.error("Response body: {}", response.body());
                SteveMod.LOGGER.error("Make sure LM Studio is running and the server is started on {}", apiUrl);
                return null;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                SteveMod.LOGGER.error("Request interrupted", e);
                return null;
            } catch (java.net.ConnectException e) {
                SteveMod.LOGGER.error("Cannot connect to LM Studio at {}. Is LM Studio server running?", apiUrl);
                SteveMod.LOGGER.error("Connection error: {}", e.getMessage());
                SteveMod.LOGGER.error("Exception details: {}", e.getClass().getName());
                e.printStackTrace();
                if (attempt < MAX_RETRIES - 1) {
                    int delayMs = INITIAL_RETRY_DELAY_MS * (int) Math.pow(2, attempt);
                    SteveMod.LOGGER.warn("Retrying in {}ms (attempt {}/{})", delayMs, attempt + 1, MAX_RETRIES);
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                } else {
                    return null;
                }
            } catch (java.net.http.HttpTimeoutException e) {
                SteveMod.LOGGER.error("Timeout connecting to LM Studio at {}", apiUrl);
                SteveMod.LOGGER.error("Timeout error: {}", e.getMessage());
                SteveMod.LOGGER.error("This usually means the request was sent but no response received.");
                SteveMod.LOGGER.error("Check if LM Studio received the request in its logs.");
                SteveMod.LOGGER.error("Exception details: {}", e.getClass().getName());
                e.printStackTrace();
                if (attempt < MAX_RETRIES - 1) {
                    int delayMs = INITIAL_RETRY_DELAY_MS * (int) Math.pow(2, attempt);
                    SteveMod.LOGGER.warn("Retrying in {}ms (attempt {}/{})", delayMs, attempt + 1, MAX_RETRIES);
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                } else {
                    SteveMod.LOGGER.error("All retry attempts failed. Check:");
                    SteveMod.LOGGER.error("1. Is the model loaded in LM Studio?");
                    SteveMod.LOGGER.error("2. Is the model name '{}' correct?", model);
                    SteveMod.LOGGER.error("3. Check LM Studio server logs for errors");
                    return null;
                }
            } catch (java.io.IOException e) {
                SteveMod.LOGGER.error("IO error communicating with LM Studio: {}", e.getMessage());
                SteveMod.LOGGER.error("Exception type: {}", e.getClass().getSimpleName());
                if (e.getCause() != null) {
                    SteveMod.LOGGER.error("Caused by: {}", e.getCause().getMessage());
                }
                if (attempt < MAX_RETRIES - 1) {
                    int delayMs = INITIAL_RETRY_DELAY_MS * (int) Math.pow(2, attempt);
                    SteveMod.LOGGER.warn("Retrying in {}ms (attempt {}/{})", delayMs, attempt + 1, MAX_RETRIES);
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                } else {
                    return null;
                }
            } catch (Exception e) {
                if (attempt < MAX_RETRIES - 1) {
                    int delayMs = INITIAL_RETRY_DELAY_MS * (int) Math.pow(2, attempt);
                    SteveMod.LOGGER.warn("Error communicating with LM Studio API, retrying in {}ms (attempt {}/{})",
                        delayMs, attempt + 1, MAX_RETRIES, e);
                    SteveMod.LOGGER.error("Exception details: {}", e.getClass().getSimpleName() + ": " + e.getMessage());
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                } else {
                    SteveMod.LOGGER.error("Error communicating with LM Studio API after {} attempts", MAX_RETRIES, e);
                    SteveMod.LOGGER.error("Exception type: {}, Message: {}", e.getClass().getSimpleName(), e.getMessage());
                    SteveMod.LOGGER.error("Make sure LM Studio is running and the server is started on {}", apiUrl);
                    if (e.getCause() != null) {
                        SteveMod.LOGGER.error("Caused by: {}", e.getCause().getMessage());
                    }
                    return null;
                }
            }
        }

        return null;
    }

    private JsonObject buildRequestBody(String systemPrompt, String userPrompt) {
        JsonObject body = new JsonObject();
        // Always add model - LM Studio requires it
        if (model != null && !model.isEmpty()) {
            body.addProperty("model", model);
            SteveMod.LOGGER.debug("Using model: {}", model);
        } else {
            SteveMod.LOGGER.warn("Model name is empty! LM Studio may not process the request correctly.");
            // Попробуем отправить без модели - некоторые версии LM Studio могут работать так
        }
        body.addProperty("temperature", SteveConfig.TEMPERATURE.get());
        body.addProperty("max_tokens", SteveConfig.MAX_TOKENS.get());
        SteveMod.LOGGER.debug("Request parameters: temperature={}, max_tokens={}", 
            SteveConfig.TEMPERATURE.get(), SteveConfig.MAX_TOKENS.get());

        JsonArray messages = new JsonArray();
        
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", systemPrompt);
        messages.add(systemMessage);

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", userPrompt);
        messages.add(userMessage);

        body.add("messages", messages);
        
        return body;
    }

    private String parseResponse(String responseBody) {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            
            if (json.has("choices") && json.getAsJsonArray("choices").size() > 0) {
                JsonObject firstChoice = json.getAsJsonArray("choices").get(0).getAsJsonObject();
                if (firstChoice.has("message")) {
                    JsonObject message = firstChoice.getAsJsonObject("message");
                    if (message.has("content")) {
                        return message.get("content").getAsString();
                    }
                }
            }
            
            SteveMod.LOGGER.error("Unexpected LM Studio response format: {}", responseBody);
            return null;
            
        } catch (Exception e) {
            SteveMod.LOGGER.error("Error parsing LM Studio response", e);
            return null;
        }
    }
}

