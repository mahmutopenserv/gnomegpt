package com.gnomegpt.commands;

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

    public SlashCommandHandler(OsrsWikiClient wikiClient, GePriceClient geClient)
    {
        this.wikiClient = wikiClient;
        this.geClient = geClient;
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
            case "/clear":
                return "__CLEAR__";
            default:
                return "Unknown command: " + command + "\nType /help for available commands.";
        }
    }

    private String getHelpText()
    {
        return "🧙 GnomeGPT Commands:\n" +
            "/price <item> — GE price check\n" +
            "/wiki <topic> — Quick wiki lookup\n" +
            "/item <item> — Wiki lookup (same as /wiki)\n" +
            "/quest <name> — Quest info\n" +
            "/monster <name> — Monster info\n" +
            "/clear — Clear chat history\n" +
            "/help — This message\n\n" +
            "Or just type normally and I'll help you out!";
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
}
