package com.gnomegpt.calc;

import com.gnomegpt.wiki.GePriceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.*;

/**
 * Calculates XP remaining, actions needed, and costs for training methods.
 * Uses the real OSRS XP table and live GE prices.
 */
public class SkillCalculator
{
    private static final Logger log = LoggerFactory.getLogger(SkillCalculator.class);
    private static final NumberFormat NUM = NumberFormat.getNumberInstance(Locale.US);

    // OSRS XP table (levels 1-99)
    private static final int[] XP_TABLE = new int[100];

    static
    {
        XP_TABLE[1] = 0;
        for (int level = 2; level <= 99; level++)
        {
            double sum = 0;
            for (int i = 1; i < level; i++)
            {
                sum += Math.floor(i + 300 * Math.pow(2, i / 7.0));
            }
            XP_TABLE[level] = (int) Math.floor(sum / 4);
        }
    }

    /**
     * Common training methods: skill -> list of (method name, xp per action, item name, items per action)
     * item name is used for GE price lookup. null means no cost.
     */
    private static final Map<String, List<TrainingMethod>> METHODS = new LinkedHashMap<>();

    static
    {
        // Construction
        METHODS.put("construction", Arrays.asList(
            new TrainingMethod("Oak larders", 480, "Oak plank", 8),
            new TrainingMethod("Mahogany tables", 840, "Mahogany plank", 6),
            new TrainingMethod("Teak garden benches", 540, "Teak plank", 6),
            new TrainingMethod("Oak dungeon doors", 600, "Oak plank", 10),
            new TrainingMethod("Gnome benches (myth cape rack)", 720, "Mahogany plank", 5)
        ));

        // Prayer
        METHODS.put("prayer", Arrays.asList(
            new TrainingMethod("Dragon bones (gilded altar)", 252, "Dragon bones", 1),
            new TrainingMethod("Superior dragon bones (gilded altar)", 630, "Superior dragon bones", 1),
            new TrainingMethod("Dagannoth bones (gilded altar)", 437.5, "Dagannoth bones", 1),
            new TrainingMethod("Ensouled dragon heads", 1560, "Ensouled dragon head", 1)
        ));

        // Herblore
        METHODS.put("herblore", Arrays.asList(
            new TrainingMethod("Prayer potions", 87.5, "Ranarr weed", 1),
            new TrainingMethod("Super restores", 142.5, "Snapdragon", 1),
            new TrainingMethod("Saradomin brews", 180, "Toadflax", 1),
            new TrainingMethod("Ranging potions", 162.5, "Dwarf weed", 1)
        ));

        // Crafting
        METHODS.put("crafting", Arrays.asList(
            new TrainingMethod("Black d'hide bodies", 258, "Black dragon leather", 3),
            new TrainingMethod("Cutting rubies", 85, "Uncut ruby", 1),
            new TrainingMethod("Cutting diamonds", 107.5, "Uncut diamond", 1)
        ));

        // Smithing
        METHODS.put("smithing", Arrays.asList(
            new TrainingMethod("Gold bars (Goldsmith gauntlets)", 56.2, "Gold ore", 1),
            new TrainingMethod("Mithril platebodies", 250, "Mithril bar", 5),
            new TrainingMethod("Adamant platebodies", 312.5, "Adamantite bar", 5)
        ));

        // Cooking
        METHODS.put("cooking", Arrays.asList(
            new TrainingMethod("Sharks", 210, "Raw shark", 1),
            new TrainingMethod("Anglerfish", 230, "Raw anglerfish", 1),
            new TrainingMethod("Karambwans", 190, "Raw karambwan", 1)
        ));

        // Fletching
        METHODS.put("fletching", Arrays.asList(
            new TrainingMethod("Magic longbow (u)", 91.5, "Magic logs", 1),
            new TrainingMethod("Rune arrows", 12.5, "Rune arrowtips", 1),
            new TrainingMethod("Dragon darts", 25, "Dragon dart tip", 1)
        ));

        // Firemaking
        METHODS.put("firemaking", Arrays.asList(
            new TrainingMethod("Maple logs", 135, "Maple logs", 1),
            new TrainingMethod("Yew logs", 202.5, "Yew logs", 1),
            new TrainingMethod("Magic logs", 303.8, "Magic logs", 1),
            new TrainingMethod("Redwood logs", 350, "Redwood logs", 1)
        ));
    }

    private final GePriceClient geClient;

    public SkillCalculator(GePriceClient geClient)
    {
        this.geClient = geClient;
    }

    /**
     * Get XP required for a level.
     */
    public static int xpForLevel(int level)
    {
        if (level < 1) return 0;
        if (level > 99) level = 99;
        return XP_TABLE[level];
    }

    /**
     * Get XP remaining from current level to target level.
     */
    public static int xpRemaining(int currentLevel, int targetLevel)
    {
        return xpForLevel(targetLevel) - xpForLevel(currentLevel);
    }

    /**
     * Calculate training cost/info for a skill from current to target level.
     * Returns a formatted string for the LLM or slash command.
     */
    public String calculate(String skill, int currentLevel, int targetLevel)
    {
        skill = skill.toLowerCase().trim();
        int xpNeeded = xpRemaining(currentLevel, targetLevel);

        StringBuilder sb = new StringBuilder();
        sb.append("📊 ").append(capitalize(skill)).append(": Level ")
          .append(currentLevel).append(" → ").append(targetLevel).append("\n");
        sb.append("XP needed: ").append(NUM.format(xpNeeded)).append("\n\n");

        List<TrainingMethod> methods = METHODS.get(skill);
        if (methods == null || methods.isEmpty())
        {
            sb.append("No cost data for this skill yet. Check the wiki for training methods.");
            return sb.toString();
        }

        for (TrainingMethod method : methods)
        {
            int actions = (int) Math.ceil(xpNeeded / method.xpPerAction);
            int itemsNeeded = actions * method.itemsPerAction;

            sb.append("• ").append(method.name).append("\n");
            sb.append("  ").append(NUM.format(actions)).append(" actions");
            sb.append(" | ").append(NUM.format(itemsNeeded)).append("x [[").append(method.itemName).append("]]\n");

            // Try to get GE price
            if (method.itemName != null)
            {
                try
                {
                    String priceInfo = geClient.lookup(method.itemName);
                    // Extract buy price from the response
                    String buyPrice = extractBuyPrice(priceInfo);
                    if (buyPrice != null)
                    {
                        long pricePerItem = parsePriceValue(buyPrice);
                        if (pricePerItem > 0)
                        {
                            long totalCost = pricePerItem * itemsNeeded;
                            sb.append("  Cost: ").append(formatGp(totalCost)).append(" gp");
                            sb.append(" (").append(formatGp(pricePerItem)).append(" ea)\n");
                        }
                    }
                }
                catch (Exception e)
                {
                    log.debug("Price lookup failed for {}", method.itemName);
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Generate a context string for the LLM with player-specific skill calc data.
     */
    public String contextForSkill(String skill, int currentLevel, int targetLevel)
    {
        return calculate(skill, currentLevel, targetLevel);
    }

    private String extractBuyPrice(String priceInfo)
    {
        for (String line : priceInfo.split("\n"))
        {
            if (line.trim().startsWith("Buy:"))
            {
                return line.trim().replace("Buy:", "").replace("gp", "").trim();
            }
        }
        return null;
    }

    private long parsePriceValue(String value)
    {
        try
        {
            value = value.trim().replace(",", "");
            if (value.endsWith("B")) return (long) (Double.parseDouble(value.replace("B", "")) * 1_000_000_000);
            if (value.endsWith("M")) return (long) (Double.parseDouble(value.replace("M", "")) * 1_000_000);
            if (value.endsWith("K")) return (long) (Double.parseDouble(value.replace("K", "")) * 1_000);
            return Long.parseLong(value);
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    private String formatGp(long amount)
    {
        if (amount >= 1_000_000_000) return String.format("%.2fB", amount / 1_000_000_000.0);
        if (amount >= 1_000_000) return String.format("%.1fM", amount / 1_000_000.0);
        if (amount >= 1_000) return String.format("%.0fK", amount / 1_000.0);
        return NUM.format(amount);
    }

    private String capitalize(String s)
    {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    public static boolean hasMethodsFor(String skill)
    {
        return METHODS.containsKey(skill.toLowerCase().trim());
    }

    public static Set<String> supportedSkills()
    {
        return METHODS.keySet();
    }

    static class TrainingMethod
    {
        final String name;
        final double xpPerAction;
        final String itemName;
        final int itemsPerAction;

        TrainingMethod(String name, double xpPerAction, String itemName, int itemsPerAction)
        {
            this.name = name;
            this.xpPerAction = xpPerAction;
            this.itemName = itemName;
            this.itemsPerAction = itemsPerAction;
        }
    }
}
