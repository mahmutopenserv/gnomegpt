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

public class OllamaProvider implements LlmProvider
{
    private static final Logger log = LoggerFactory.getLogger(OllamaProvider.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private String baseUrl;

    public OllamaProvider()
    {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build();
        this.baseUrl = "http://localhost:11434";
    }

    public void setBaseUrl(String url)
    {
        this.baseUrl = url != null && !url.isEmpty() ? url : "http://localhost:11434";
    }

    @Override
    public boolean isAvailable()
    {
        try
        {
            Request request = new Request.Builder()
                .url(baseUrl + "/api/tags")
                .get()
                .build();

            try (Response response = httpClient.newCall(request).execute())
            {
                return response.isSuccessful();
            }
        }
        catch (Exception e)
        {
            return false;
        }
    }

    @Override
    public String chat(List<ChatMessage> messages, String model) throws IOException
    {
        if (!isAvailable())
        {
            return "Ollama is not running. Start it at " + baseUrl + " or switch to OpenAI/Anthropic in settings.";
        }

        JsonObject body = new JsonObject();
        body.addProperty("model", model != null && !model.isEmpty() ? model : "llama3.2");
        body.addProperty("stream", false);

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
            .url(baseUrl + "/api/chat")
            .header("Content-Type", "application/json")
            .post(RequestBody.create(body.toString(), JSON))
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (response.body() == null)
            {
                return "Error: Empty response from Ollama";
            }

            String responseBody = response.body().string();

            if (!response.isSuccessful())
            {
                log.error("Ollama API error {}: {}", response.code(), responseBody);
                return "Ollama error (" + response.code() + "). Is the model '" + model + "' installed? Run: ollama pull " + model;
            }

            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            if (json.has("message"))
            {
                return json.getAsJsonObject("message")
                    .get("content").getAsString().trim();
            }
            return "Error: No response generated";
        }
    }
}
