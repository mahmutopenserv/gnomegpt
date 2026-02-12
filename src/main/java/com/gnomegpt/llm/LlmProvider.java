package com.gnomegpt.llm;

import com.gnomegpt.chat.ChatMessage;

import java.io.IOException;
import java.util.List;

public interface LlmProvider
{
    String chat(List<ChatMessage> messages, String model) throws IOException;
    boolean isAvailable();
}
