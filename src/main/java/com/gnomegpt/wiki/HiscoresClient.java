package com.gnomegpt.wiki;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Fetches player stats from the OSRS Hiscores.
 */
public class HiscoresClient
{
    private static final Logger log = LoggerFactory.getLogger(HiscoresClient.class);
    private static final String HISCORES_URL = "https://secure.runescape.com/m=hiscore_oldschool/index_lite.ws";
    private static final String USER_AGENT = "GnomeGPT/1.0 (RuneLite Plugin)";

    private static final String[] SKILL_NAMES = {
        "Overall", "Attack", "Defence", "Strength", "Hitpoints", "Ranged", "Prayer",
        "Magic", "Cooking", "Woodcutting", "Fletching", "Fishing", "Firemaking",
        "Crafting", "Smithing", "Mining", "Herblore", "Agility", "Thieving",
        "Slayer", "Farming", "Runecraft", "Hunter", "Construction"
    };

    private final OkHttpClient httpClient;

    // Cache to avoid hammering hiscores
    private String cachedRsn;
    private String cachedResult;
    private long cachedAt;
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

    public HiscoresClient()
    {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();
    }

    /**
     * Fetch and format player stats. Returns a formatted string for the LLM context,
     * or empty string if lookup fails.
     */
    public String getPlayerStats(String rsn) throws IOException
    {
        if (rsn == null || rsn.trim().isEmpty())
        {
            return "";
        }

        rsn = rsn.trim();

        // Check cache
        if (rsn.equalsIgnoreCase(cachedRsn) && cachedResult != null
            && (System.currentTimeMillis() - cachedAt) < CACHE_TTL_MS)
        {
            return cachedResult;
        }

        String encoded = URLEncoder.encode(rsn, StandardCharsets.UTF_8.toString());
        String url = HISCORES_URL + "?player=" + encoded;

        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null)
            {
                if (response.code() == 404)
                {
                    return "Player '" + rsn + "' not found on hiscores.";
                }
                return "";
            }

            String body = response.body().string();
            String[] lines = body.split("\n");

            Map<String, int[]> stats = new LinkedHashMap<>();
            for (int i = 0; i < Math.min(lines.length, SKILL_NAMES.length); i++)
            {
                String[] parts = lines[i].split(",");
                if (parts.length >= 3)
                {
                    try
                    {
                        int rank = Integer.parseInt(parts[0].trim());
                        int level = Integer.parseInt(parts[1].trim());
                        int xp = Integer.parseInt(parts[2].trim());
                        stats.put(SKILL_NAMES[i], new int[]{level, xp});
                    }
                    catch (NumberFormatException e)
                    {
                        // skip
                    }
                }
            }

            if (stats.isEmpty())
            {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Player: ").append(rsn).append("\n");

            int[] overall = stats.get("Overall");
            if (overall != null)
            {
                sb.append("Total Level: ").append(overall[0]);
                sb.append(" | Total XP: ").append(formatXp(overall[1])).append("\n");
            }

            sb.append("Skills: ");
            boolean first = true;
            for (Map.Entry<String, int[]> entry : stats.entrySet())
            {
                if (entry.getKey().equals("Overall")) continue;
                if (!first) sb.append(", ");
                sb.append(entry.getKey()).append(": ").append(entry.getValue()[0]);
                first = false;
            }

            String result = sb.toString();
            cachedRsn = rsn;
            cachedResult = result;
            cachedAt = System.currentTimeMillis();

            return result;
        }
    }

    private String formatXp(int xp)
    {
        if (xp >= 1_000_000) return String.format("%.1fM", xp / 1_000_000.0);
        if (xp >= 1_000) return String.format("%.1fK", xp / 1_000.0);
        return String.valueOf(xp);
    }
}
