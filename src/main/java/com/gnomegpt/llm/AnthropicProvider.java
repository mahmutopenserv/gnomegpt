package com.gnomegpt.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gnomegpt.chat.ChatMessage;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AnthropicProvider implements LlmProvider
{
    private static final Logger log = LoggerFactory.getLogger(AnthropicProvider.class);
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private String apiKey;

    public AnthropicProvider()
    {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build();
    }

    public void setApiKey(String apiKey)
    {
        this.apiKey = apiKey;
    }

    @Override
    public boolean isAvailable()
    {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    private String extractSystem(List<ChatMessage> messages)
    {
        for (ChatMessage m : messages)
        {
            if (m.getRole() == ChatMessage.Role.SYSTEM) return m.getContent();
        }
        return "";
    }

    private JsonObject buildBody(List<ChatMessage> messages, String model, boolean stream)
    {
        String systemPrompt = extractSystem(messages);
        List<ChatMessage> nonSystem = messages.stream()
            .filter(m -> m.getRole() != ChatMessage.Role.SYSTEM)
            .collect(Collectors.toList());

        JsonObject body = new JsonObject();
        body.addProperty("model", model != null && !model.isEmpty() ? model : "claude-haiku-4-20250514");
        body.addProperty("max_tokens", 1024);
        body.addProperty("stream", stream);

        if (!systemPrompt.isEmpty())
        {
            body.addProperty("system", systemPrompt);
        }

        JsonArray messagesArr = new JsonArray();
        for (ChatMessage msg : nonSystem)
        {
            JsonObject m = new JsonObject();
            m.addProperty("role", msg.getRoleString());
            m.addProperty("content", msg.getContent());
            messagesArr.add(m);
        }
        body.add("messages", messagesArr);

        return body;
    }

    private Request buildRequest(JsonObject body)
    {
        return new Request.Builder()
            .url(API_URL)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .post(RequestBody.create(body.toString(), JSON))
            .build();
    }

    @Override
    public String chat(List<ChatMessage> messages, String model) throws IOException
    {
        if (!isAvailable()) return "Please set your Anthropic API key.";

        Request request = buildRequest(buildBody(messages, model, false));
        Response response = httpClient.newCall(request).execute();

        if ((response.code() == 429 || response.code() >= 500))
        {
            response.close();
            try { Thread.sleep(1000); } catch (InterruptedException e) {}
            response = httpClient.newCall(request).execute();
        }

        if (response.body() == null) return "Error: Empty response";

        String responseBody = response.body().string();
        if (!response.isSuccessful())
        {
            log.error("Anthropic error {}: {}", response.code(), responseBody);
            if (response.code() == 401) return "Invalid API key.";
            return "Anthropic API error (" + response.code() + ").";
        }

        JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
        JsonArray content = json.getAsJsonArray("content");
        if (content != null && content.size() > 0)
        {
            return content.get(0).getAsJsonObject().get("text").getAsString().trim();
        }
        return "Error: No response generated";
    }

    @Override
    public void chatStream(List<ChatMessage> messages, String model, StreamCallback callback)
    {
        if (!isAvailable())
        {
            callback.onError("Please set your Anthropic API key.");
            return;
        }

        Request request = buildRequest(buildBody(messages, model, true));

        try
        {
            Response response = httpClient.newCall(request).execute();

            if (!response.isSuccessful() || response.body() == null)
            {
                int code = response.code();
                response.close();
                if (code == 429 || code >= 500)
                {
                    try { Thread.sleep(1000); } catch (InterruptedException ie) {}
                    response = httpClient.newCall(request).execute();
                    if (!response.isSuccessful() || response.body() == null)
                    {
                        callback.onError("Anthropic API error (" + response.code() + ")");
                        response.close();
                        return;
                    }
                }
                else
                {
                    callback.onError(code == 401 ? "Invalid API key." : "Anthropic error (" + code + ")");
                    return;
                }
            }

            StringBuilder fullResponse = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body().byteStream())))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    if (!line.startsWith("data: ")) continue;
                    String data = line.substring(6).trim();

                    try
                    {
                        JsonObject event = JsonParser.parseString(data).getAsJsonObject();
                        String type = event.has("type") ? event.get("type").getAsString() : "";

                        if ("content_block_delta".equals(type))
                        {
                            JsonObject delta = event.getAsJsonObject("delta");
                            if (delta != null && delta.has("text"))
                            {
                                String token = delta.get("text").getAsString();
                                fullResponse.append(token);
                                callback.onToken(token);
                            }
                        }
                        else if ("message_stop".equals(type))
                        {
                            break;
                        }
                    }
                    catch (Exception e)
                    {
                        // skip
                    }
                }
            }

            callback.onComplete(fullResponse.toString());
        }
        catch (IOException e)
        {
            callback.onError("Connection error: " + e.getMessage());
        }
    }
}
