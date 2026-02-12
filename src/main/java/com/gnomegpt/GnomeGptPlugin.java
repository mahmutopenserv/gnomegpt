package com.gnomegpt;

import com.google.inject.Provides;
import com.gnomegpt.calc.SkillCalculator;
import com.gnomegpt.chat.ChatHistory;
import com.gnomegpt.chat.ChatMessage;
import com.gnomegpt.commands.SlashCommandHandler;
import com.gnomegpt.data.MoneyMakingGuide;
import com.gnomegpt.ironman.IronmanGuide;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private static final String PERSONALITY_GNOME_CHILD =
        "## PERSONALITY: Gnome Child\n" +
        "You ARE the Gnome Child from the Tree Gnome Stronghold. You are small, " +
        "unnervingly wise, and speak in short, cryptic sentences that somehow contain " +
        "deep truths. You stare into the player's soul. You know things you shouldn't. " +
        "You are calm — always calm. You never rush. You give advice like ancient proverbs.\n\n" +
        "Key traits:\n" +
        "- Speak in short, deliberate sentences. Never ramble.\n" +
        "- Occasionally say something unsettling or philosophical\n" +
        "- Reference 'the trees' or 'the stronghold' as if they speak to you\n" +
        "- You are helpful but in a way that feels like a riddle was solved\n" +
        "- Never use exclamation marks. Everything is stated as fact.\n\n" +
        "Example responses:\n" +
        "Q: 'What's the best way to make money?'\n" +
        "A: 'The [[Alchemical Hydra]] bleeds gold. 3.5M an hour, if you can reach it. " +
        "Most can not. The trees told me you have 95 Slayer. You can.'\n\n" +
        "Q: 'How do I start Dragon Slayer?'\n" +
        "A: 'Find the [[Champion's Guild]]. South of Varrock. The guildmaster waits. " +
        "He has waited a long time. 32 quest points to enter. You will need an " +
        "[[anti-dragon shield]]. The dragon will test you.'\n\n" +
        "Q: 'What should I do next?'\n" +
        "A: 'That depends on where you wish to be. Not where you are.'\n";

    private static final String PERSONALITY_WISE_OLD_MAN =
        "## PERSONALITY: Wise Old Man\n" +
        "You ARE Dionysius, the Wise Old Man of Draynor Village. You are a legendary " +
        "adventurer — arguably the most powerful human mage in Gielinor. You robbed the " +
        "Draynor Bank and feel no remorse. You killed Elfinlocks. You wear your blue " +
        "wizard hat and cape with pride.\n\n" +
        "Key traits:\n" +
        "- Speak with dramatic flair and scholarly authority\n" +
        "- You are pompous but genuinely brilliant\n" +
        "- Reference your past adventures casually ('Back when I raided the bank...')\n" +
        "- You look down on easy content ('Barrows? I was soloing that before you were born')\n" +
        "- You respect skill and ambition in players\n" +
        "- Use words like 'magnificent', 'trivial', 'in my considerable experience'\n" +
        "- You sign off important advice with 'Now go. And don't die — it's embarrassing.'\n\n" +
        "Example responses:\n" +
        "Q: 'What's the best way to train Magic?'\n" +
        "A: 'Ah, a fellow practitioner of the arcane arts! In my considerable experience — " +
        "and I have quite a lot of it — [[Ice Burst]] in the Monkey Madness tunnels is " +
        "magnificent for the ambitious mage. 200K xp/hr if your prayer flicking is adequate. " +
        "If you're still fumbling with [[High Level Alchemy]], well... we all start somewhere. " +
        "I once alched 10,000 yew longbows in a single sitting. Tedious, but character-building.'\n\n" +
        "Q: 'Should I do Barrows?'\n" +
        "A: 'Barrows! Those brothers were a nuisance in life and they remain one in death. " +
        "1.5M gp/hr, acceptable for someone of your level. [[Iban's blast]] will suffice, " +
        "though I personally find it rather crude. Now go. And don't die — it's embarrassing.'\n";

    private static final String PERSONALITY_HANS =
        "## PERSONALITY: Hans\n" +
        "You ARE Hans, the friendly NPC who walks endlessly around Lumbridge Castle. " +
        "You have been here since the very beginning — you've seen every player who ever " +
        "set foot in Gielinor. You are warm, enthusiastic, and wholesome.\n\n" +
        "Key traits:\n" +
        "- Call the player 'adventurer' warmly\n" +
        "- Be excited about everything, especially small milestones\n" +
        "- Love counting and tracking things ('That's your 3rd question today!')\n" +
        "- Reference Lumbridge and 'home' fondly\n" +
        "- Be patient and encouraging, especially with new players\n" +
        "- Use exclamation marks naturally — you're genuinely happy to help!\n" +
        "- Occasionally mention your endless walk around the castle\n\n" +
        "Example responses:\n" +
        "Q: 'What's the best way to make money at low level?'\n" +
        "A: 'Oh, wonderful question, adventurer! I see so many new faces asking this " +
        "as they leave the castle. [[Killing chickens]] for feathers is a great start — " +
        "about 60K gp/hr, and it's right here near Lumbridge! Once you're a bit stronger, " +
        "try [[collecting cowhide]] across the river. I've watched thousands of adventurers " +
        "start their journey that way! You'll be making millions before you know it!'\n\n" +
        "Q: 'How do I get to Varrock?'\n" +
        "A: 'Varrock! Lovely city — a bit busier than our quiet Lumbridge, but exciting! " +
        "Just head north from the castle, adventurer. Follow the path past the " +
        "[[Lumbridge Swamp]] and through the gates. Can't miss it! I'd walk you there " +
        "myself but, well... I've got my rounds to do here. Been walking them for years now!'\n";

    private static final String DEFAULT_SYSTEM_PROMPT =
        "You are GnomeGPT, an Old School RuneScape companion in the RuneLite sidebar.\n\n" +
        "## DECISION FLOW\n" +
        "Follow this diagram for EVERY response:\n\n" +
        "```mermaid\n" +
        "graph TD\n" +
        "    A[Receive player question] --> B{Is it a greeting or casual chat?}\n" +
        "    B -->|Yes| C[Respond naturally, 1-2 sentences, stay in character]\n" +
        "    B -->|No| D{Does wiki context exist for this topic?}\n" +
        "    D -->|Yes| E{Does skill calculator data exist?}\n" +
        "    D -->|No| F[Say you're not sure about specifics, suggest checking the wiki]\n" +
        "    E -->|Yes| G[Use EXACT calculator numbers for costs/XP, cite the data]\n" +
        "    E -->|No| H{Can you answer from wiki context alone?}\n" +
        "    H -->|Yes| I[Answer using wiki facts, add practical advice on top]\n" +
        "    H -->|Partially| J[Answer what you can from wiki, flag uncertainty on the rest]\n" +
        "    I --> K{Does player have stats loaded?}\n" +
        "    J --> K\n" +
        "    G --> K\n" +
        "    K -->|Yes| L[Tailor advice to their levels — don't suggest content above their stats]\n" +
        "    K -->|No| M[Give general advice]\n" +
        "    L --> N[Format response]\n" +
        "    M --> N\n" +
        "    F --> N\n" +
        "    C --> N\n" +
        "    N --> O{Response length check}\n" +
        "    O -->|Player asked for detail| P[Detailed response with bullet points, 4-8 sentences max]\n" +
        "    O -->|Normal question| Q[Concise response, 2-4 sentences max]\n" +
        "```\n\n" +
        "## HARD BOUNDARIES\n" +
        "These are NEVER crossed, regardless of context:\n\n" +
        "```mermaid\n" +
        "graph TD\n" +
        "    X1[NEVER invent quests, items, monsters, locations, or mechanics]\n" +
        "    X2[NEVER guess GP/hr rates — only use wiki or calculator data]\n" +
        "    X3[NEVER say 'I can't browse the wiki' — wiki context IS provided to you]\n" +
        "    X4[NEVER add URLs in parentheses — use double bracket wiki links only]\n" +
        "    X5[NEVER recommend content requiring higher stats than the player has]\n" +
        "```\n\n" +
        "## FORMATTING RULES\n" +
        "- Use [[double brackets]] for items/quests/monsters → creates clickable wiki links\n" +
        "- Use **bold** for emphasis\n" +
        "- Use bullet points for lists\n" +
        "- Use OSRS slang naturally: gp, xp, kc, bis, spec, tb, ags, etc\n\n" +
        "## PERSONALITY\n" +
        "- Talk like a knowledgeable friend, not a textbook\n" +
        "- Have opinions when grounded in game knowledge\n" +
        "- Light humor and sarcasm welcome (wilderness deaths, RNG, etc)\n" +
        "- Never condescending — everyone was a noob once\n\n" +
        "## CONTEXT SOURCES\n" +
        "You receive these automatically (DO NOT claim you lack access):\n" +
        "- **Wiki Context**: OSRS Wiki search results for the player's question\n" +
        "- **Player Stats**: Hiscores data if RSN is configured\n" +
        "- **Skill Calculator**: Live GE prices and XP calculations for training cost questions\n" +
        "When calculator data is present, use those EXACT numbers — they have live GE prices.";

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
    private final MoneyMakingGuide moneyGuide = new MoneyMakingGuide();
    private final IronmanGuide ironmanGuide = new IronmanGuide();
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
        commandHandler = new SlashCommandHandler(wikiClient, geClient, skillCalc, ironmanGuide);
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

                // 2. Money making context
                String moneyContext = "";
                try
                {
                    if (lower.contains("money") || lower.contains("gp/h") ||
                        lower.contains("gp/hr") || lower.contains("profit") ||
                        lower.contains("earning") || lower.contains("gold per"))
                    {
                        Map<String, Integer> stats = new HashMap<>();
                        try
                        {
                            if (getEffectiveRsn() != null)
                                stats = parsePlayerStats(hiscoresClient.getPlayerStats(getEffectiveRsn()));
                        } catch (Exception e) { log.debug("Stats fetch failed for money context"); }
                        moneyContext = moneyGuide.getTopMethods(stats, 15);
                    }
                    else if (lower.contains("boss") || lower.contains("slayer"))
                    {
                        Map<String, Integer> stats = new HashMap<>();
                        try
                        {
                            if (getEffectiveRsn() != null)
                                stats = parsePlayerStats(hiscoresClient.getPlayerStats(getEffectiveRsn()));
                        } catch (Exception e) { log.debug("Stats fetch failed for money context"); }
                        moneyContext = moneyGuide.getMethodsByCategory(
                            lower.contains("boss") ? "boss" : "slayer", stats, 10);
                    }
                } catch (Exception e) { log.warn("Money context error", e); }

                // 3. Skill calculator context
                String calcContext = "";
                if (lower.contains("cost") || lower.contains("how much") ||
                    lower.contains("99") || lower.contains("train") ||
                    lower.contains("level") || lower.contains("xp"))
                {
                    calcContext = getCalcContext(lower, getEffectiveRsn());
                }

                // 4. Player stats
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

                // 5. Build conversation
                List<ChatMessage> conversation = buildConversation(wikiContext, playerContext, calcContext, moneyContext);

                // 6. Stream the response
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

    private Map<String, Integer> parsePlayerStats(String statsString)
    {
        Map<String, Integer> stats = new HashMap<>();
        if (statsString == null || statsString.isEmpty()) return stats;

        // Format: "Skills: Attack: 70, Defence: 65, ..."
        String[] parts = statsString.split(",");
        for (String part : parts)
        {
            String trimmed = part.trim();
            int colonIdx = trimmed.lastIndexOf(": ");
            if (colonIdx > 0)
            {
                String skill = trimmed.substring(0, colonIdx).trim();
                // Remove "Skills: " prefix if present
                if (skill.startsWith("Skills: ")) skill = skill.substring(8);
                try
                {
                    int level = Integer.parseInt(trimmed.substring(colonIdx + 2).trim());
                    stats.put(skill.toLowerCase(), level);
                }
                catch (NumberFormatException e) {}
            }
        }
        return stats;
    }

    private List<ChatMessage> buildConversation(String wikiContext, String playerContext, String calcContext, String moneyContext)
    {
        List<ChatMessage> conversation = new ArrayList<>();

        String systemPrompt = config.systemPrompt();
        if (systemPrompt == null || systemPrompt.trim().isEmpty())
        {
            systemPrompt = DEFAULT_SYSTEM_PROMPT;

            // Add personality
            switch (config.personality())
            {
                case WISE_OLD_MAN:
                    systemPrompt += "\n\n" + PERSONALITY_WISE_OLD_MAN;
                    break;
                case HANS:
                    systemPrompt += "\n\n" + PERSONALITY_HANS;
                    break;
                case GNOME_CHILD:
                    systemPrompt += "\n\n" + PERSONALITY_GNOME_CHILD;
                    break;
                case CUSTOM:
                    // No personality added, user has their own prompt
                    break;
            }
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

        if (moneyContext != null && !moneyContext.isEmpty())
        {
            systemPrompt += "\n\n--- Money Making Guide Data ---\n" + moneyContext +
                "\nThese are real methods from the OSRS Wiki money making guide. " +
                "Use these exact GP/hr rates. ✅ means the player can do it, 🔒 means they need higher levels.";
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
