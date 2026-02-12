package com.gnomegpt.llm;

import com.gnomegpt.chat.ChatMessage;

import java.io.IOException;
import java.util.List;

public interface LlmProvider
{
    String chat(List<ChatMessage> messages, String model) throws IOException;

    /**
     * Stream a response, calling back with tokens as they arrive.
     * Default implementation falls back to non-streaming.
     */
    default void chatStream(List<ChatMessage> messages, String model, StreamCallback callback)
    {
        try
        {
            String result = chat(messages, model);
            callback.onToken(result);
            callback.onComplete(result);
        }
        catch (IOException e)
        {
            callback.onError(e.getMessage());
        }
    }

    boolean isAvailable();
}
