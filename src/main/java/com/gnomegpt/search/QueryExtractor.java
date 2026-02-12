package com.gnomegpt.search;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts meaningful OSRS search terms from natural language questions.
 * Strips filler words and focuses on game-specific nouns.
 */
public class QueryExtractor
{
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
        "how", "do", "i", "can", "what", "is", "the", "a", "an", "to", "for",
        "of", "in", "on", "at", "with", "my", "me", "should", "would", "could",
        "where", "when", "why", "does", "did", "will", "am", "are", "was", "were",
        "be", "been", "being", "have", "has", "had", "it", "its", "this", "that",
        "which", "who", "from", "about", "into", "best", "good", "way", "some",
        "any", "much", "many", "most", "more", "also", "just", "very", "really",
        "like", "get", "got", "go", "going", "need", "want", "know", "tell",
        "please", "thanks", "thank", "help", "hey", "hi", "yo", "whats", "hows"
    ));

    /** Known OSRS multi-word terms that should stay together. */
    private static final List<String> COMPOUND_TERMS = Arrays.asList(
        "dragon bones", "superior dragon bones", "dagannoth bones",
        "abyssal whip", "abyssal sire", "abyssal demon",
        "theatre of blood", "chambers of xeric", "tombs of amascut",
        "giant mole", "king black dragon", "kalphite queen",
        "corporeal beast", "nightmare zone", "pest control",
        "fight caves", "the inferno", "the gauntlet", "corrupted gauntlet",
        "guardians of the rift", "hallowed sepulchre",
        "blast furnace", "motherlode mine", "volcanic mine",
        "god wars dungeon", "wilderness bosses",
        "money making", "quest guide", "skill guide",
        "fire cape", "infernal cape", "barrows gloves",
        "dragon slayer", "monkey madness", "desert treasure",
        "recipe for disaster", "song of the elves", "sins of the father",
        "a night at the theatre",
        "black chinchompa", "red chinchompa",
        "mahogany table", "oak larder", "teak bench",
        "gilded altar", "wilderness altar",
        "slayer task", "slayer master",
        "grand exchange", "collection log",
        "max cape", "quest cape", "music cape",
        "cox", "tob", "toa", "gwd", "nmz",
        "dps calculator", "skill calculator"
    );

    private static final Pattern LEVEL_PATTERN = Pattern.compile(
        "(?:level|lvl)\\s*\\d+", Pattern.CASE_INSENSITIVE
    );

    /**
     * Extract the best wiki search query from a natural language question.
     */
    public static String extract(String input)
    {
        if (input == null || input.trim().isEmpty()) return input;

        String lower = input.toLowerCase().trim();

        // Check for compound OSRS terms first — preserve them
        List<String> foundCompounds = new ArrayList<>();
        for (String term : COMPOUND_TERMS)
        {
            if (lower.contains(term))
            {
                foundCompounds.add(term);
            }
        }

        // If we found specific OSRS terms, use those as the query
        if (!foundCompounds.isEmpty())
        {
            // Also grab any remaining meaningful words
            String remaining = lower;
            for (String compound : foundCompounds)
            {
                remaining = remaining.replace(compound, "");
            }

            List<String> extraWords = extractMeaningfulWords(remaining);

            // Combine: compounds first, then extra meaningful words
            StringBuilder query = new StringBuilder();
            for (String compound : foundCompounds)
            {
                if (query.length() > 0) query.append(" ");
                query.append(compound);
            }
            for (String word : extraWords)
            {
                if (query.length() > 0) query.append(" ");
                query.append(word);
            }
            return query.toString().trim();
        }

        // No compound terms — extract meaningful words
        List<String> words = extractMeaningfulWords(lower);
        if (words.isEmpty())
        {
            // Fall back to original minus obvious filler
            return lower;
        }

        return String.join(" ", words);
    }

    /**
     * Extract multiple search queries for broader coverage.
     * Returns the primary query plus any secondary focused queries.
     */
    public static List<String> extractMultiple(String input)
    {
        List<String> queries = new ArrayList<>();
        String primary = extract(input);
        queries.add(primary);

        String lower = input.toLowerCase();

        // Add specific supplementary searches
        if (lower.contains("money") || lower.contains("gp/h") || lower.contains("profit"))
        {
            queries.add("Money making guide");
        }
        if (lower.contains("quest") && !primary.toLowerCase().contains("quest"))
        {
            queries.add(primary + " quest");
        }
        if (lower.contains("train") || lower.contains("level") || lower.contains("xp") ||
            lower.contains("fastest") || lower.contains("cheapest") || lower.contains("quickest") ||
            lower.contains("efficient") || lower.contains("99"))
        {
            queries.add(primary + " training");
            // Also try the specific skill training page
            for (String skill : new String[]{
                "attack", "strength", "defence", "ranged", "prayer", "magic",
                "hitpoints", "mining", "smithing", "fishing", "cooking",
                "firemaking", "woodcutting", "agility", "herblore", "thieving",
                "crafting", "fletching", "slayer", "hunter", "construction",
                "farming", "runecraft"})
            {
                if (lower.contains(skill))
                {
                    queries.add(skill.substring(0, 1).toUpperCase() + skill.substring(1) + " training");
                    break;
                }
            }
        }
        if (lower.contains("kill") || lower.contains("fight") || lower.contains("strategy"))
        {
            queries.add(primary + " strategy");
        }
        if (lower.contains("gear") || lower.contains("setup") || lower.contains("equipment") ||
            lower.contains("bis") || lower.contains("what to wear") || lower.contains("loadout"))
        {
            queries.add(primary + " equipment");
            // Also try the strategy page for bosses
            queries.add(primary + " strategy");
            queries.add(primary + "/Strategies");
        }

        return queries;
    }

    private static List<String> extractMeaningfulWords(String text)
    {
        // Remove level references (keep the context but not "level 50")
        text = LEVEL_PATTERN.matcher(text).replaceAll("");

        String[] tokens = text.split("[\\s,.?!;:]+");
        List<String> meaningful = new ArrayList<>();
        for (String token : tokens)
        {
            String clean = token.replaceAll("[^a-z0-9'-]", "").trim();
            if (clean.length() > 1 && !STOP_WORDS.contains(clean))
            {
                meaningful.add(clean);
            }
        }
        return meaningful;
    }
}
