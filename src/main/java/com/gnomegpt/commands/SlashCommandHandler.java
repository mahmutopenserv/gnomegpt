package com.gnomegpt.commands;

import com.gnomegpt.calc.SkillCalculator;
import com.gnomegpt.ironman.IronmanGuide;
import com.gnomegpt.wiki.OsrsWikiClient;
import com.gnomegpt.wiki.GePriceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SlashCommandHandler
{
    private static final Logger log = LoggerFactory.getLogger(SlashCommandHandler.class);

    private final OsrsWikiClient wikiClient;
    private final GePriceClient geClient;
    private final SkillCalculator skillCalc;
    private final IronmanGuide ironmanGuide;

    public SlashCommandHandler(OsrsWikiClient wikiClient, GePriceClient geClient,
                               SkillCalculator skillCalc, IronmanGuide ironmanGuide)
    {
        this.wikiClient = wikiClient;
        this.geClient = geClient;
        this.skillCalc = skillCalc;
        this.ironmanGuide = ironmanGuide;
    }

    public String handle(String message)
    {
        if (message == null || !message.startsWith("/"))
        {
            return null;
        }

        String[] parts = message.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1].trim() : "";

        switch (command)
        {
            case "/help":
                return getHelpText();
            case "/price":
                return handlePrice(args);
            case "/wiki":
                return handleWiki(args);
            case "/item":
                return handleWiki(args);
            case "/quest":
                return handleWiki(args + " quest");
            case "/monster":
                return handleWiki(args);
            case "/calc":
                return handleCalc(args);
            case "/gear":
                return handleGear(args);
            case "/iron":
                return handleIron(args);
            case "/clear":
                return "__CLEAR__";
            default:
                return "Unknown command: " + command + "\nType /help for available commands.";
        }
    }

    private String getHelpText()
    {
        return "\uD83E\uDDD9 **GnomeGPT Commands:**\n\n" +
            "• /price <item> — GE price check\n" +
            "• /wiki <topic> — Quick wiki lookup\n" +
            "• /quest <name> — Quest info\n" +
            "• /monster <name> — Monster info\n" +
            "• /gear <item> — Item stats + GE price\n" +
            "• /calc <skill> <current> <target> — Training cost calc\n" +
            "• /iron — Ironman guide tracker (BRUHsailer)\n" +
            "• /clear — Clear chat history\n" +
            "• /help — This message\n\n" +
            "Supported /calc skills: " + String.join(", ", SkillCalculator.supportedSkills()) +
            "\nOr just type normally and I'll help you out!";
    }

    private String handlePrice(String itemName)
    {
        if (itemName.isEmpty())
        {
            return "Usage: /price <item name>\nExample: /price Dragon bones";
        }

        try
        {
            return geClient.lookup(itemName);
        }
        catch (Exception e)
        {
            log.warn("GE price lookup failed for: {}", itemName, e);
            return "Couldn't look up price for '" + itemName + "'. Try again or check the wiki.";
        }
    }

    private String handleWiki(String query)
    {
        if (query.isEmpty())
        {
            return "Usage: /wiki <topic>\nExample: /wiki Abyssal whip";
        }

        try
        {
            List<String> titles = wikiClient.search(query, 1);
            if (titles.isEmpty())
            {
                return "No wiki results for '" + query + "'.";
            }

            String title = titles.get(0);
            String content = wikiClient.getPageContent(title);
            String url = "https://oldschool.runescape.wiki/w/" + title.replace(" ", "_");

            if (content.isEmpty())
            {
                return title + "\n" + url + "\n\nNo content available.";
            }

            if (content.length() > 1500)
            {
                content = content.substring(0, 1500) + "...\n\nRead more: " + url;
            }
            else
            {
                content += "\n\n" + url;
            }

            return "=== " + title + " ===\n" + content;
        }
        catch (Exception e)
        {
            log.warn("Wiki lookup failed for: {}", query, e);
            return "Wiki search failed for '" + query + "'.";
        }
    }

    private String handleGear(String itemName)
    {
        if (itemName.isEmpty())
        {
            return "Usage: /gear <item name>\nExample: /gear Abyssal whip";
        }

        try
        {
            // Get wiki info
            List<String> titles = wikiClient.search(itemName, 1);
            String wikiInfo = "";
            String url = "";
            if (!titles.isEmpty())
            {
                String title = titles.get(0);
                wikiInfo = wikiClient.getPageContent(title);
                url = "https://oldschool.runescape.wiki/w/" + title.replace(" ", "_");
                if (wikiInfo.length() > 800)
                {
                    wikiInfo = wikiInfo.substring(0, 800) + "...";
                }
            }

            // Get GE price
            String priceInfo = "";
            try
            {
                priceInfo = geClient.lookup(itemName);
            }
            catch (Exception e)
            {
                priceInfo = "Price unavailable";
            }

            StringBuilder result = new StringBuilder();
            result.append("⚔️ **").append(itemName).append("**\n\n");

            if (!priceInfo.isEmpty() && !priceInfo.startsWith("Couldn't"))
            {
                result.append(priceInfo).append("\n\n");
            }

            if (!wikiInfo.isEmpty())
            {
                result.append(wikiInfo).append("\n");
            }

            if (!url.isEmpty())
            {
                result.append("\n").append(url);
            }

            return result.toString();
        }
        catch (Exception e)
        {
            log.warn("Gear lookup failed for: {}", itemName, e);
            return "Couldn't look up '" + itemName + "'.";
        }
    }

    private String handleIron(String args)
    {
        if (!ironmanGuide.isLoaded())
        {
            return "Ironman guide data not loaded. Something went wrong at startup.";
        }

        if (args.isEmpty())
        {
            return ironmanGuide.getCurrentStep();
        }

        switch (args.toLowerCase())
        {
            case "next":
            case "done":
            case "complete":
                return ironmanGuide.markCurrentComplete();
            case "status":
            case "progress":
                return ironmanGuide.getStatus();
            case "back":
            case "undo":
                return ironmanGuide.undoLastStep();
            case "reset":
                return ironmanGuide.reset();
            case "help":
                return "🗡️ **Ironman Guide (BRUHsailer)**\n\n" +
                    "• /iron — Show current step\n" +
                    "• /iron next — Mark step complete, show next\n" +
                    "• /iron back — Undo last step\n" +
                    "• /iron status — Show progress %\n" +
                    "• /iron reset — Reset all progress\n\n" +
                    "Based on the BRUHsailer guide by So Iron BRUH & ParasailerOSRS.";
            default:
                return "Unknown /iron command. Try: /iron help";
        }
    }

    private String handleCalc(String args)
    {
        if (args.isEmpty())
        {
            return "Usage: /calc <skill> <current_level> <target_level>\n" +
                "Example: /calc construction 50 99\n\n" +
                "Supported skills: " + String.join(", ", SkillCalculator.supportedSkills());
        }

        String[] parts = args.split("\\s+");
        if (parts.length < 3)
        {
            return "Usage: /calc <skill> <current_level> <target_level>\nExample: /calc construction 50 99";
        }

        String skill = parts[0].toLowerCase();
        if (!SkillCalculator.hasMethodsFor(skill))
        {
            return "No calculator data for '" + skill + "' yet.\nSupported: " +
                String.join(", ", SkillCalculator.supportedSkills());
        }

        try
        {
            int current = Integer.parseInt(parts[1]);
            int target = Integer.parseInt(parts[2]);

            if (current < 1 || current > 99 || target < 1 || target > 99)
            {
                return "Levels must be between 1 and 99.";
            }
            if (target <= current)
            {
                return "Target level must be higher than current level.";
            }

            return skillCalc.calculate(skill, current, target);
        }
        catch (NumberFormatException e)
        {
            return "Invalid levels. Use numbers: /calc construction 50 99";
        }
    }
}
