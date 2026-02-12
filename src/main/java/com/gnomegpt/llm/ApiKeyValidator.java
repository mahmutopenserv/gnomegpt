package com.gnomegpt.llm;

import com.gnomegpt.GnomeGptConfig;

public class ApiKeyValidator
{
    private ApiKeyValidator() {}

    public static String validate(String apiKey, GnomeGptConfig.LlmProvider provider)
    {
        if (provider == GnomeGptConfig.LlmProvider.OLLAMA)
        {
            return null;
        }

        if (apiKey == null || apiKey.trim().isEmpty())
        {
            switch (provider)
            {
                case OPENAI:
                    return "Need an OpenAI API key to work my magic. Grab one at: https://platform.openai.com/api-keys";
                case ANTHROPIC:
                    return "Need an Anthropic API key. Get one at: https://console.anthropic.com/settings/keys";
                default:
                    return "API key required.";
            }
        }

        String key = apiKey.trim();

        switch (provider)
        {
            case OPENAI:
                if (!key.startsWith("sk-"))
                {
                    return "That doesn't look like an OpenAI key (they start with 'sk-'). Did you paste an Anthropic key by mistake?";
                }
                break;
            case ANTHROPIC:
                if (key.startsWith("sk-") && !key.startsWith("sk-ant-"))
                {
                    return "That looks like an OpenAI key (starts with 'sk-'). Anthropic keys start with 'sk-ant-'. Switch your provider or paste the right key.";
                }
                break;
        }

        return null;
    }

    public static String redact(String apiKey)
    {
        if (apiKey == null || apiKey.length() < 8)
        {
            return "***";
        }
        return apiKey.substring(0, 5) + "..." + apiKey.substring(apiKey.length() - 3);
    }
}
