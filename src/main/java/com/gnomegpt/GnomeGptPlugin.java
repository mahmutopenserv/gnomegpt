package com.gnomegpt;

import com.google.inject.Provides;
import com.gnomegpt.calc.SkillCalculator;
import com.gnomegpt.chat.ChatHistory;
import com.gnomegpt.chat.ChatMessage;
import com.gnomegpt.commands.SlashCommandHandler;
import com.gnomegpt.llm.*;
import com.gnomegpt.search.QueryExtractor;
import com.gnomegpt.wiki.GePriceClient;
import com.gnomegpt.wiki.HiscoresClient;
import com.gnomegpt.wiki.OsrsWikiClient;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.GameState;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
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
        "8. Light humor welcome. Never condescending.\n" +
        "9. When skill calculator data is provided, use those exact numbers for cost estimates.\n\n" +
        "You have access to the OSRS Wiki — it's automatically searched and the results are " +
        "included below as context. You DON'T need to browse anything yourself. When wiki context " +
        "is provided, use it confidently. When it's not, just say you're not sure about the specifics.\n" +
        "NEVER say 'I can't browse the wiki' or 'I don't have access to the wiki' — you DO, " +
        "the results are right there in your context.";

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private GnomeGptConfig config;

    @Inject
    private Client client;

    @Inject
    private ConfigManager configManager;

    private GnomeGptPanel panel;
    private NavigationButton navButton;

    private final ChatHistory chatHistory = new ChatHistory();
    private final OsrsWikiClient wikiClient = new OsrsWikiClient();
    private final GePriceClient geClient = new GePriceClient();
    private final HiscoresClient hiscoresClient = new HiscoresClient();
    private final SkillCalculator skillCalc;
    private final OpenAiProvider openAiProvider = new OpenAiProvider();
    private final AnthropicProvider anthropicProvider = new AnthropicProvider();
    private final OllamaProvider ollamaProvider = new OllamaProvider();
    private SlashCommandHandler commandHandler;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Auto-detected RSN
    private String detectedRsn = null;

    {
        skillCalc = new SkillCalculator(geClient);
    }

    @Override
    protected void startUp()
    {
        commandHandler = new SlashCommandHandler(wikiClient, geClient, skillCalc);
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

    /**
     * Auto-detect RSN when player logs in.
     */
    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            Player local = client.getLocalPlayer();
            if (local != null && local.getName() != null)
            {
                detectedRsn = local.getName();
                log.info("GnomeGPT detected RSN: {}", detectedRsn);

                // Auto-fill config if empty
                if (config.rsn() == null || config.rsn().trim().isEmpty())
                {
                    configManager.setConfiguration("gnomegpt", "rsn", detectedRsn);
                }
            }
        }
    }

    /**
     * Get the effective RSN (config override > auto-detected).
     */
    private String getEffectiveRsn()
    {
        String configRsn = config.rsn();
        if (configRsn != null && !configRsn.trim().isEmpty())
        {
            return configRsn.trim();
        }
        return detectedRsn;
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

        // Regular message → LLM with streaming
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
        panel.startStreamingBubble();

        executor.submit(() ->
        {
            try
            {
                String lower = trimmed.toLowerCase();

                // 1. Smart wiki search
                String wikiContext = "";
                if (config.wikiLookup())
                {
                    try
                    {
                        List<String> queries = QueryExtractor.extractMultiple(trimmed);
                        StringBuilder wikiBuilder = new StringBuilder();

                        for (String query : queries)
                        {
                            String result = wikiClient.searchAndFetch(query,
                                queries.size() > 1 ? 2 : config.maxWikiResults());
                            if (!result.isEmpty())
                            {
                                wikiBuilder.append(result);
                            }
                        }
                        wikiContext = wikiBuilder.toString();

                        // Truncate if too long to avoid token limits
                        if (wikiContext.length() > 12000)
                        {
                            wikiContext = wikiContext.substring(0, 12000) + "\n...[truncated]";
                        }
                    }
                    catch (Exception e)
                    {
                        log.warn("Wiki lookup failed", e);
                    }
                }

                // 2. Skill calculator context
                String calcContext = "";
                if (lower.contains("cost") || lower.contains("how much") ||
                    lower.contains("99") || lower.contains("train") ||
                    lower.contains("level") || lower.contains("xp"))
                {
                    calcContext = getCalcContext(lower, getEffectiveRsn());
                }

                // 3. Player stats
                String playerContext = "";
                String rsn = getEffectiveRsn();
                if (rsn != null)
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

                // 4. Build conversation
                List<ChatMessage> conversation = buildConversation(wikiContext, playerContext, calcContext);

                // 5. Stream the response
                LlmProvider provider = getProvider();
                final StringBuilder fullResponse = new StringBuilder();

                provider.chatStream(conversation, config.model(), new StreamCallback()
                {
                    @Override
                    public void onToken(String token)
                    {
                        fullResponse.append(token);
                        panel.appendStreamToken(token);
                    }

                    @Override
                    public void onComplete(String response)
                    {
                        // Re-render with full formatting
                        panel.finalizeStreamBubble(response);

                        ChatMessage assistantMsg = new ChatMessage(ChatMessage.Role.ASSISTANT, response);
                        chatHistory.addMessage(assistantMsg);
                        panel.setLoading(false);
                    }

                    @Override
                    public void onError(String error)
                    {
                        panel.finalizeStreamBubble("Error: " + error);
                        panel.setLoading(false);
                    }
                });
            }
            catch (Exception e)
            {
                log.error("Error getting AI response", e);
                panel.finalizeStreamBubble("Something went wrong: " + e.getMessage());
                panel.setLoading(false);
            }
        });
    }

    private List<ChatMessage> buildConversation(String wikiContext, String playerContext, String calcContext)
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

        if (!calcContext.isEmpty())
        {
            systemPrompt += "\n\n--- Skill Calculator Data (live GE prices) ---\n" + calcContext +
                "\nUse this data to give accurate cost estimates. These prices are live from the GE.";
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

    private String getCalcContext(String query, String rsn)
    {
        StringBuilder context = new StringBuilder();

        for (String skill : SkillCalculator.supportedSkills())
        {
            if (query.contains(skill))
            {
                int currentLevel = 1;
                if (rsn != null && !rsn.isEmpty())
                {
                    try
                    {
                        String stats = hiscoresClient.getPlayerStats(rsn);
                        String skillCap = skill.substring(0, 1).toUpperCase() + skill.substring(1);
                        int idx = stats.indexOf(skillCap + ": ");
                        if (idx >= 0)
                        {
                            String after = stats.substring(idx + skillCap.length() + 2);
                            String levelStr = after.split("[,\\s]")[0];
                            currentLevel = Integer.parseInt(levelStr);
                        }
                    }
                    catch (Exception e)
                    {
                        log.debug("Could not get {} level from hiscores", skill);
                    }
                }

                int targetLevel = 99;
                java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                    "(?:to|level|lvl)\\s*(\\d{1,2})"
                ).matcher(query);
                if (m.find())
                {
                    int parsed = Integer.parseInt(m.group(1));
                    if (parsed > currentLevel && parsed <= 99)
                    {
                        targetLevel = parsed;
                    }
                }

                if (currentLevel < targetLevel)
                {
                    context.append(skillCalc.calculate(skill, currentLevel, targetLevel));
                    context.append("\n");
                }
            }
        }

        return context.toString();
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
