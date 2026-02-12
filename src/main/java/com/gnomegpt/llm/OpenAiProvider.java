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

    @Override
    public String chat(List<ChatMessage> messages, String model) throws IOException
    {
        if (!isAvailable())
        {
            return "Please set your OpenAI API key in the plugin settings.";
        }

        JsonObject body = buildBody(messages, model, false);

        Request request = new Request.Builder()
            .url(API_URL)
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .post(RequestBody.create(body.toString(), JSON))
            .build();

        return executeWithRetry(request);
    }

    @Override
    public void chatStream(List<ChatMessage> messages, String model, StreamCallback callback)
    {
        if (!isAvailable())
        {
            callback.onError("Please set your OpenAI API key in the plugin settings.");
            return;
        }

        JsonObject body = buildBody(messages, model, true);

        Request request = new Request.Builder()
            .url(API_URL)
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .post(RequestBody.create(body.toString(), JSON))
            .build();

        try
        {
            Response response = httpClient.newCall(request).execute();

            if (!response.isSuccessful() || response.body() == null)
            {
                String errorBody = response.body() != null ? response.body().string() : "";
                response.close();

                // Retry once on 429/500
                if (response.code() == 429 || response.code() >= 500)
                {
                    try { Thread.sleep(1000); } catch (InterruptedException ie) {}
                    response = httpClient.newCall(request).execute();
                    if (!response.isSuccessful() || response.body() == null)
                    {
                        callback.onError("OpenAI API error (" + response.code() + ")");
                        response.close();
                        return;
                    }
                }
                else
                {
                    if (response.code() == 401)
                    {
                        callback.onError("Invalid API key.");
                    }
                    else
                    {
                        callback.onError("OpenAI API error (" + response.code() + ")");
                    }
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
                    if ("[DONE]".equals(data)) break;

                    try
                    {
                        JsonObject chunk = JsonParser.parseString(data).getAsJsonObject();
                        JsonArray choices = chunk.getAsJsonArray("choices");
                        if (choices != null && choices.size() > 0)
                        {
                            JsonObject delta = choices.get(0).getAsJsonObject().getAsJsonObject("delta");
                            if (delta != null && delta.has("content"))
                            {
                                String token = delta.get("content").getAsString();
                                fullResponse.append(token);
                                callback.onToken(token);
                            }
                        }
                    }
                    catch (Exception e)
                    {
                        // skip malformed chunks
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

    private JsonObject buildBody(List<ChatMessage> messages, String model, boolean stream)
    {
        JsonObject body = new JsonObject();
        body.addProperty("model", model != null && !model.isEmpty() ? model : "gpt-4o-mini");
        body.addProperty("max_tokens", 1024);
        body.addProperty("stream", stream);

        JsonArray messagesArr = new JsonArray();
        for (ChatMessage msg : messages)
        {
            JsonObject m = new JsonObject();
            m.addProperty("role", msg.getRoleString());
            m.addProperty("content", msg.getContent());
            messagesArr.add(m);
        }
        body.add("messages", messagesArr);

        return body;
    }

    private String executeWithRetry(Request request) throws IOException
    {
        Response response = httpClient.newCall(request).execute();

        // Retry once on 429 or 5xx
        if ((response.code() == 429 || response.code() >= 500) && response.body() != null)
        {
            response.close();
            try { Thread.sleep(1000); } catch (InterruptedException e) {}
            response = httpClient.newCall(request).execute();
        }

        if (response.body() == null)
        {
            return "Error: Empty response from OpenAI";
        }

        String responseBody = response.body().string();

        if (!response.isSuccessful())
        {
            log.error("OpenAI API error {}: {}", response.code(), responseBody);
            if (response.code() == 401) return "Invalid API key.";
            return "OpenAI API error (" + response.code() + ").";
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
