package com.gnomegpt.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatHistory
{
    private final List<ChatMessage> messages = new ArrayList<>();
    private static final int MAX_HISTORY = 20;

    public void addMessage(ChatMessage message)
    {
        messages.add(message);
        while (messages.size() > MAX_HISTORY)
        {
            messages.remove(0);
        }
    }

    public List<ChatMessage> getMessages()
    {
        return Collections.unmodifiableList(messages);
    }

    public void clear()
    {
        messages.clear();
    }

    public boolean isEmpty()
    {
        return messages.isEmpty();
    }
}
