package com.gnomegpt.chat;

public class ChatMessage
{
    public enum Role
    {
        USER,
        ASSISTANT,
        SYSTEM
    }

    private final Role role;
    private final String content;
    private final long timestamp;

    public ChatMessage(Role role, String content)
    {
        this.role = role;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    public Role getRole()
    {
        return role;
    }

    public String getContent()
    {
        return content;
    }

    public long getTimestamp()
    {
        return timestamp;
    }

    public String getRoleString()
    {
        switch (role)
        {
            case USER:
                return "user";
            case ASSISTANT:
                return "assistant";
            case SYSTEM:
                return "system";
            default:
                return "user";
        }
    }
}
