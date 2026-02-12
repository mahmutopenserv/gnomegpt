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

public class OpenAiProvider implements LlmProvider
{
    private static final Logger log = LoggerFactory.getLogger(OpenAiProvider.class);
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private String apiKey;

    public OpenAiProvider()
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
            return "Please set your OpenAI API key in the plugin settings. Get one at: https://platform.openai.com/api-keys";
        }

        JsonObject body = new JsonObject();
        body.addProperty("model", model != null && !model.isEmpty() ? model : "gpt-4o-mini");
        body.addProperty("max_tokens", 1024);

        JsonArray messagesArr = new JsonArray();
        for (ChatMessage msg : messages)
        {
            JsonObject m = new JsonObject();
            m.addProperty("role", msg.getRoleString());
            m.addProperty("content", msg.getContent());
            messagesArr.add(m);
        }
        body.add("messages", messagesArr);

        Request request = new Request.Builder()
            .url(API_URL)
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .post(RequestBody.create(body.toString(), JSON))
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (response.body() == null)
            {
                return "Error: Empty response from OpenAI";
            }

            String responseBody = response.body().string();

            if (!response.isSuccessful())
            {
                log.error("OpenAI API error {}: {}", response.code(), responseBody);
                if (response.code() == 401)
                {
                    return "Invalid API key. Check your key at: https://platform.openai.com/api-keys";
                }
                return "OpenAI API error (" + response.code() + "). Check the RuneLite logs for details.";
            }

            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray choices = json.getAsJsonArray("choices");
            if (choices != null && choices.size() > 0)
            {
                return choices.get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString().trim();
            }
            return "Error: No response generated";
        }
    }
}
