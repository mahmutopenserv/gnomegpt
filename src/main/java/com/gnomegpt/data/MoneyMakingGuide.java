package com.gnomegpt.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.*;

/**
 * Loads and queries the embedded money making guide data.
 * Filters by player stats to only show achievable methods.
 */
public class MoneyMakingGuide
{
    private static final Logger log = LoggerFactory.getLogger(MoneyMakingGuide.class);
    private static final NumberFormat NUM = NumberFormat.getNumberInstance(Locale.US);

    private final List<MoneyMethod> methods = new ArrayList<>();

    public MoneyMakingGuide()
    {
        loadData();
    }

    private void loadData()
    {
        try (InputStream is = getClass().getResourceAsStream("/data/moneymakers.json"))
        {
            if (is == null)
            {
                log.warn("moneymakers.json not found in resources");
                return;
            }

            JsonArray arr = JsonParser.parseReader(
                new InputStreamReader(is, StandardCharsets.UTF_8)
            ).getAsJsonArray();

            for (JsonElement el : arr)
            {
                JsonObject obj = el.getAsJsonObject();
                methods.add(new MoneyMethod(
                    obj.get("method").getAsString(),
                    obj.get("gp_hr").getAsLong(),
                    obj.has("skill") ? obj.get("skill").getAsString() : "",
                    obj.has("level") ? obj.get("level").getAsInt() : 1,
                    obj.has("category") ? obj.get("category").getAsString() : "",
                    obj.has("intensity") ? obj.get("intensity").getAsString() : "",
                    obj.has("members") ? obj.get("members").getAsBoolean() : true
                ));
            }

            // Sort by GP/hr descending
            methods.sort((a, b) -> Long.compare(b.gpHr, a.gpHr));
            log.info("Loaded {} money making methods", methods.size());
        }
        catch (Exception e)
        {
            log.error("Failed to load moneymakers.json", e);
        }
    }

    /**
     * Get top money makers, optionally filtered by player stats.
     * @param playerStats map of skill name -> level (can be null for no filtering)
     * @param limit max results
     * @return formatted string for LLM context
     */
    public String getTopMethods(Map<String, Integer> playerStats, int limit)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Top Money Making Methods (from OSRS Wiki, sorted by GP/hr):\n\n");

        int count = 0;
        for (MoneyMethod m : methods)
        {
            if (count >= limit) break;

            // Check if player can do this method
            boolean canDo = true;
            if (playerStats != null && !playerStats.isEmpty() && m.level > 1)
            {
                String skillKey = m.skill.toLowerCase();
                // For combat, check overall combat level approximation
                if (skillKey.equals("combat"))
                {
                    // rough check — if they have attack/strength/ranged data
                    int combatEst = playerStats.getOrDefault("attack", 1);
                    combatEst = Math.max(combatEst, playerStats.getOrDefault("strength", 1));
                    combatEst = Math.max(combatEst, playerStats.getOrDefault("ranged", 1));
                    combatEst = Math.max(combatEst, playerStats.getOrDefault("magic", 1));
                    canDo = combatEst >= (m.level * 0.7); // rough approximation
                }
                else if (playerStats.containsKey(skillKey))
                {
                    canDo = playerStats.get(skillKey) >= m.level;
                }
            }

            String status = canDo ? "✅" : "🔒";
            sb.append(status).append(" **").append(m.name).append("**\n");
            sb.append("   ").append(formatGp(m.gpHr)).append(" gp/hr");
            if (m.level > 1)
            {
                sb.append(" | Requires: ").append(m.skill).append(" ").append(m.level);
            }
            sb.append(" | ").append(m.intensity);
            if (!canDo)
            {
                sb.append(" (need higher level)");
            }
            sb.append("\n\n");
            count++;
        }

        return sb.toString();
    }

    /**
     * Get methods for a specific category or skill.
     */
    public String getMethodsByCategory(String query, Map<String, Integer> playerStats, int limit)
    {
        String lower = query.toLowerCase();
        List<MoneyMethod> filtered = new ArrayList<>();

        for (MoneyMethod m : methods)
        {
            if (m.category.toLowerCase().contains(lower) ||
                m.skill.toLowerCase().contains(lower) ||
                m.name.toLowerCase().contains(lower))
            {
                filtered.add(m);
            }
        }

        if (filtered.isEmpty())
        {
            return getTopMethods(playerStats, limit);
        }

        // Sort filtered by GP/hr descending
        filtered.sort((a, b) -> Long.compare(b.gpHr, a.gpHr));

        StringBuilder sb = new StringBuilder();
        sb.append("Money Making Methods matching '").append(query).append("' (sorted by GP/hr):\n\n");

        int count = 0;
        for (MoneyMethod m : filtered)
        {
            if (count >= limit) break;
            sb.append("• **").append(m.name).append("** — ").append(formatGp(m.gpHr)).append(" gp/hr");
            if (m.level > 1) sb.append(" (").append(m.skill).append(" ").append(m.level).append(")");
            sb.append("\n");
            count++;
        }

        return sb.toString();
    }

    private String formatGp(long amount)
    {
        if (amount >= 1_000_000) return String.format("%.1fM", amount / 1_000_000.0);
        if (amount >= 1_000) return String.format("%.0fK", amount / 1_000.0);
        return NUM.format(amount);
    }

    static class MoneyMethod
    {
        final String name;
        final long gpHr;
        final String skill;
        final int level;
        final String category;
        final String intensity;
        final boolean members;

        MoneyMethod(String name, long gpHr, String skill, int level, String category, String intensity, boolean members)
        {
            this.name = name;
            this.gpHr = gpHr;
            this.skill = skill;
            this.level = level;
            this.category = category;
            this.intensity = intensity;
            this.members = members;
        }
    }
}
