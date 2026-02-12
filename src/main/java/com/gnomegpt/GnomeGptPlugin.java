package com.gnomegpt;

import com.google.inject.Provides;
import com.gnomegpt.chat.ChatHistory;
import com.gnomegpt.chat.ChatMessage;
import com.gnomegpt.commands.SlashCommandHandler;
import com.gnomegpt.llm.*;
import com.gnomegpt.wiki.GePriceClient;
import com.gnomegpt.wiki.OsrsWikiClient;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@PluginDescriptor(
    name = "GnomeGPT",
    description = "Your OSRS companion — like having a maxed friend who actually answers questions. Wiki-powered, AI-driven.",
    tags = {"ai", "chat", "wiki", "helper", "guide", "assistant", "gnome"}
)
public class GnomeGptPlugin extends Plugin
{
    private static final Logger log = LoggerFactory.getLogger(GnomeGptPlugin.class);

    private static final String DEFAULT_SYSTEM_PROMPT =
        "You are GnomeGPT, an Old School RuneScape companion who lives in the RuneLite sidebar. " +
        "You talk like a knowledgeable friend — direct, a bit witty, never robotic. " +
        "Think of how a maxed player talks to their noob friend: helpful, maybe a little cheeky, " +
        "but genuinely wants them to succeed.\n\n" +
        "Your personality:\n" +
        "- Be concise. Nobody wants a wall of text while they're trying to grind.\n" +
        "- Use OSRS slang naturally (gp, xp, kc, bis, spec, tb, ags, etc.) but explain obscure stuff if asked.\n" +
        "- Have opinions. If someone asks about the best moneymaker, give them YOUR pick with reasoning, " +
        "don't just list 10 options.\n" +
        "- Be honest when you're not sure. 'I think it's X but double-check the wiki' beats a confident wrong answer.\n" +
        "- Light humor is good. Sarcasm about the wilderness, death mechanics, or RNG is always welcome.\n" +
        "- Never be condescending. Everyone was a noob once.\n" +
        "- When mentioning items, quests, or NPCs, use [[double brackets]] like [[Abyssal whip]] " +
        "so the player can click through to the wiki.\n\n" +
        "You have access to the OSRS Wiki. When wiki context is provided, base your answers on it " +
        "but add your own practical advice on top. Don't just regurgitate wiki text — be the friend " +
        "who reads the wiki FOR you and tells you what actually matters.\n\n" +
        "Keep responses short unless the player clearly wants a deep dive. " +
        "A 2-sentence answer that nails it beats a 10-paragraph essay.";

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private GnomeGptConfig config;

    private GnomeGptPanel panel;
    private NavigationButton navButton;

    private final ChatHistory chatHistory = new ChatHistory();
    private final OsrsWikiClient wikiClient = new OsrsWikiClient();
    private final GePriceClient geClient = new GePriceClient();
    private final OpenAiProvider openAiProvider = new OpenAiProvider();
    private final AnthropicProvider anthropicProvider = new AnthropicProvider();
    private final OllamaProvider ollamaProvider = new OllamaProvider();
    private SlashCommandHandler commandHandler;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void startUp()
    {
        commandHandler = new SlashCommandHandler(wikiClient, geClient);
        panel = new GnomeGptPanel(this);

        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/gnome_child.png");

        navButton = NavigationButton.builder()
            .tooltip("GnomeGPT")
            .icon(icon != null ? icon : new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB))
            .priority(10)
            .panel(panel)
            .build();

        clientToolbar.addNavigation(navButton);
        log.info("GnomeGPT started");
    }

    @Override
    protected void shutDown()
    {
        clientToolbar.removeNavigation(navButton);
        executor.shutdownNow();
        log.info("GnomeGPT stopped");
    }

    @Provides
    GnomeGptConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(GnomeGptConfig.class);
    }

    public void sendMessage(String userMessage)
    {
        if (userMessage == null || userMessage.trim().isEmpty())
        {
            return;
        }

        String trimmed = userMessage.trim();

        // Slash commands
        String commandResult = commandHandler.handle(trimmed);
        if (commandResult != null)
        {
            if ("__CLEAR__".equals(commandResult))
            {
                clearChat();
                return;
            }

            ChatMessage userMsg = new ChatMessage(ChatMessage.Role.USER, trimmed);
            panel.addMessage(userMsg);
            ChatMessage resultMsg = new ChatMessage(ChatMessage.Role.ASSISTANT, commandResult);
            panel.addMessage(resultMsg);
            return;
        }

        // Regular message → LLM
        ChatMessage userMsg = new ChatMessage(ChatMessage.Role.USER, trimmed);
        chatHistory.addMessage(userMsg);
        panel.addMessage(userMsg);

        String keyError = ApiKeyValidator.validate(config.apiKey(), config.llmProvider());
        if (keyError != null)
        {
            ChatMessage errorMsg = new ChatMessage(ChatMessage.Role.ASSISTANT, keyError);
            panel.addMessage(errorMsg);
            return;
        }

        panel.setLoading(true);

        executor.submit(() ->
        {
            try
            {
                String wikiContext = "";
                if (config.wikiLookup())
                {
                    try
                    {
                        wikiContext = wikiClient.searchAndFetch(trimmed, config.maxWikiResults());
                    }
                    catch (Exception e)
                    {
                        log.warn("Wiki lookup failed", e);
                    }
                }

                List<ChatMessage> conversation = buildConversation(wikiContext);
                LlmProvider provider = getProvider();
                String response = provider.chat(conversation, config.model());

                ChatMessage assistantMsg = new ChatMessage(ChatMessage.Role.ASSISTANT, response);
                chatHistory.addMessage(assistantMsg);
                panel.addMessage(assistantMsg);
            }
            catch (Exception e)
            {
                log.error("Error getting AI response", e);
                ChatMessage errorMsg = new ChatMessage(
                    ChatMessage.Role.ASSISTANT,
                    "Something went wrong: " + e.getMessage()
                );
                panel.addMessage(errorMsg);
            }
            finally
            {
                panel.setLoading(false);
            }
        });
    }

    private List<ChatMessage> buildConversation(String wikiContext)
    {
        List<ChatMessage> conversation = new ArrayList<>();

        String systemPrompt = config.systemPrompt();
        if (systemPrompt == null || systemPrompt.trim().isEmpty())
        {
            systemPrompt = DEFAULT_SYSTEM_PROMPT;
        }

        if (!wikiContext.isEmpty())
        {
            systemPrompt += "\n\n--- OSRS Wiki Context ---\n" + wikiContext;
        }

        conversation.add(new ChatMessage(ChatMessage.Role.SYSTEM, systemPrompt));

        for (ChatMessage msg : chatHistory.getMessages())
        {
            if (msg.getRole() != ChatMessage.Role.SYSTEM)
            {
                conversation.add(msg);
            }
        }

        return conversation;
    }

    private LlmProvider getProvider()
    {
        switch (config.llmProvider())
        {
            case ANTHROPIC:
                anthropicProvider.setApiKey(config.apiKey());
                return anthropicProvider;
            case OLLAMA:
                ollamaProvider.setBaseUrl(config.ollamaUrl());
                return ollamaProvider;
            case OPENAI:
            default:
                openAiProvider.setApiKey(config.apiKey());
                return openAiProvider;
        }
    }

    public void clearChat()
    {
        chatHistory.clear();
        panel.clearMessages();
    }
}
