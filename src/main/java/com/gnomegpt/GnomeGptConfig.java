package com.gnomegpt;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("gnomegpt")
public interface GnomeGptConfig extends Config
{
    enum LlmProvider
    {
        OPENAI("OpenAI"),
        ANTHROPIC("Anthropic"),
        OLLAMA("Ollama (Local)");

        private final String name;

        LlmProvider(String name)
        {
            this.name = name;
        }

        @Override
        public String toString()
        {
            return name;
        }
    }

    @ConfigSection(
        name = "LLM Settings",
        description = "Configure your AI provider",
        position = 0
    )
    String llmSection = "llmSection";

    @ConfigItem(
        keyName = "llmProvider",
        name = "Provider",
        description = "Which LLM provider to use",
        section = llmSection,
        position = 0
    )
    default LlmProvider llmProvider()
    {
        return LlmProvider.OPENAI;
    }

    @ConfigItem(
        keyName = "apiKey",
        name = "API Key",
        description = "Your API key (stored locally, only sent to your chosen provider over HTTPS). " +
            "OpenAI: https://platform.openai.com/api-keys | " +
            "Anthropic: https://console.anthropic.com/settings/keys",
        section = llmSection,
        position = 1,
        secret = true
    )
    default String apiKey()
    {
        return "";
    }

    @ConfigItem(
        keyName = "model",
        name = "Model",
        description = "Model to use. Recommended: gpt-4o-mini (OpenAI, cheapest), claude-haiku-4-20250514 (Anthropic), llama3.2 (Ollama)",
        section = llmSection,
        position = 2
    )
    default String model()
    {
        return "gpt-4o-mini";
    }

    @ConfigItem(
        keyName = "ollamaUrl",
        name = "Ollama URL",
        description = "Ollama server URL (only used with Ollama provider)",
        section = llmSection,
        position = 3
    )
    default String ollamaUrl()
    {
        return "http://localhost:11434";
    }

    @ConfigSection(
        name = "Behavior",
        description = "Configure GnomeGPT behavior",
        position = 1
    )
    String behaviorSection = "behaviorSection";

    @ConfigItem(
        keyName = "wikiLookup",
        name = "Wiki Lookup",
        description = "Automatically search the OSRS Wiki for context before answering",
        section = behaviorSection,
        position = 0
    )
    default boolean wikiLookup()
    {
        return true;
    }

    @ConfigItem(
        keyName = "maxWikiResults",
        name = "Max Wiki Results",
        description = "Maximum number of wiki pages to fetch for context (1-5)",
        section = behaviorSection,
        position = 1
    )
    default int maxWikiResults()
    {
        return 3;
    }

    @ConfigItem(
        keyName = "systemPrompt",
        name = "System Prompt",
        description = "Custom system prompt (leave empty for default GnomeGPT personality)",
        section = behaviorSection,
        position = 2
    )
    default String systemPrompt()
    {
        return "";
    }
}
