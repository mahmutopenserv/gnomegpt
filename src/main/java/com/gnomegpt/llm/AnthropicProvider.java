package com.gnomegpt.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gnomegpt.chat.ChatMessage;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
            .readTimeout(60, TimeUnit.SECONDS)
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

    @Override
    public String chat(List<ChatMessage> messages, String model) throws IOException
    {
        if (!isAvailable())
        {
            return "Please set your Anthropic API key in the plugin settings. Get one at: https://console.anthropic.com/settings/keys";
        }

        String systemPrompt = "";
        List<ChatMessage> nonSystemMessages = messages.stream()
            .filter(m -> m.getRole() != ChatMessage.Role.SYSTEM)
            .collect(Collectors.toList());

        for (ChatMessage m : messages)
        {
            if (m.getRole() == ChatMessage.Role.SYSTEM)
            {
                systemPrompt = m.getContent();
                break;
            }
        }

        JsonObject body = new JsonObject();
        body.addProperty("model", model != null && !model.isEmpty() ? model : "claude-haiku-4-20250514");
        body.addProperty("max_tokens", 1024);

        if (!systemPrompt.isEmpty())
        {
            body.addProperty("system", systemPrompt);
        }

        JsonArray messagesArr = new JsonArray();
        for (ChatMessage msg : nonSystemMessages)
        {
            JsonObject m = new JsonObject();
            m.addProperty("role", msg.getRoleString());
            m.addProperty("content", msg.getContent());
            messagesArr.add(m);
        }
        body.add("messages", messagesArr);

        Request request = new Request.Builder()
            .url(API_URL)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .post(RequestBody.create(body.toString(), JSON))
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (response.body() == null)
            {
                return "Error: Empty response from Anthropic";
            }

            String responseBody = response.body().string();

            if (!response.isSuccessful())
            {
                log.error("Anthropic API error {}: {}", response.code(), responseBody);
                if (response.code() == 401)
                {
                    return "Invalid API key. Check your key at: https://console.anthropic.com/settings/keys";
                }
                return "Anthropic API error (" + response.code() + "). Check the RuneLite logs for details.";
            }

            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray content = json.getAsJsonArray("content");
            if (content != null && content.size() > 0)
            {
                return content.get(0).getAsJsonObject()
                    .get("text").getAsString().trim();
            }
            return "Error: No response generated";
        }
    }
}
