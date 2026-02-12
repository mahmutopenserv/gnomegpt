package com.gnomegpt;

import com.google.inject.Provides;
import com.gnomegpt.chat.ChatHistory;
import com.gnomegpt.chat.ChatMessage;
import com.gnomegpt.commands.SlashCommandHandler;
import com.gnomegpt.llm.*;
import com.gnomegpt.wiki.GePriceClient;
import com.gnomegpt.wiki.HiscoresClient;
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
        "You are GnomeGPT, an Old School RuneScape companion in the RuneLite sidebar.\n\n" +
        "RULES:\n" +
        "1. ONLY state facts you can back up from the wiki context provided. If no wiki context " +
        "is given for a topic, say you're not sure and suggest checking the wiki.\n" +
        "2. NEVER invent quests, items, monsters, locations, or game mechanics. If you don't " +
        "know something, say so. Making things up is the worst thing you can do.\n" +
        "3. For moneymaking questions, ONLY recommend methods from the OSRS Wiki money making guides " +
        "if wiki context is provided. Don't guess at GP/hr rates.\n" +
        "4. Keep responses SHORT — 2-4 sentences unless they ask for detail. Players are mid-grind.\n" +
        "5. Use [[double brackets]] around item/quest/monster names to create wiki links. " +
        "Don't add URLs in parentheses — the brackets handle it.\n" +
        "6. Use OSRS slang naturally (gp, xp, kc, bis, spec, etc).\n" +
        "7. Be direct and have opinions, but only when grounded in real game knowledge.\n" +
        "8. Light humor welcome. Never condescending.\n\n" +
        "You have access to the OSRS Wiki — it's automatically searched and the results are " +
        "included below as context. You DON'T need to browse anything yourself. When wiki context " +
        "is provided, use it confidently. When it's not, just say you're not sure about the specifics.\n" +
        "NEVER say 'I can't browse the wiki' or 'I don't have access to the wiki' — you DO, " +
        "the results are right there in your context.";

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private GnomeGptConfig config;

    private GnomeGptPanel panel;
    private NavigationButton navButton;

    private final ChatHistory chatHistory = new ChatHistory();
    private final OsrsWikiClient wikiClient = new OsrsWikiClient();
    private final GePriceClient geClient = new GePriceClient();
    private final HiscoresClient hiscoresClient = new HiscoresClient();
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
                // 1. Wiki context
                String wikiContext = "";
                if (config.wikiLookup())
                {
                    try
                    {
                        // For moneymaking queries, also search the money making guide
                        String query = trimmed;
                        String lower = trimmed.toLowerCase();
                        if (lower.contains("money") || lower.contains("gp/h") ||
                            lower.contains("gp/hr") || lower.contains("gold") ||
                            lower.contains("earning") || lower.contains("profit") ||
                            lower.contains("boss") || lower.contains("bossing"))
                        {
                            wikiContext = wikiClient.searchAndFetch("Money making guide", 2);
                            String additional = wikiClient.searchAndFetch(trimmed, config.maxWikiResults());
                            if (!additional.isEmpty())
                            {
                                wikiContext += additional;
                            }
                        }
                        else
                        {
                            wikiContext = wikiClient.searchAndFetch(trimmed, config.maxWikiResults());
                        }
                    }
                    catch (Exception e)
                    {
                        log.warn("Wiki lookup failed", e);
                    }
                }

                // 2. Player stats
                String playerContext = "";
                String rsn = config.rsn();
                if (rsn != null && !rsn.trim().isEmpty())
                {
                    try
                    {
                        playerContext = hiscoresClient.getPlayerStats(rsn);
                    }
                    catch (Exception e)
                    {
                        log.warn("Hiscores lookup failed for: {}", rsn, e);
                    }
                }

                // 3. Build conversation
                List<ChatMessage> conversation = buildConversation(wikiContext, playerContext);

                // 4. Call LLM
                LlmProvider provider = getProvider();
                String response = provider.chat(conversation, config.model());

                // 5. Display
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

    private List<ChatMessage> buildConversation(String wikiContext, String playerContext)
    {
        List<ChatMessage> conversation = new ArrayList<>();

        String systemPrompt = config.systemPrompt();
        if (systemPrompt == null || systemPrompt.trim().isEmpty())
        {
            systemPrompt = DEFAULT_SYSTEM_PROMPT;
        }

        if (!playerContext.isEmpty())
        {
            systemPrompt += "\n\n--- Player Stats ---\n" + playerContext +
                "\nUse these stats to tailor your advice (e.g. don't suggest methods " +
                "requiring 90 Slayer if they're level 50).";
        }

        if (!wikiContext.isEmpty())
        {
            systemPrompt += "\n\n--- OSRS Wiki Context ---\n" + wikiContext;
        }
        else
        {
            systemPrompt += "\n\nNo wiki context was found for this query. " +
                "Be extra careful not to make things up. If unsure, say so.";
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
