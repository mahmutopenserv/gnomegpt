package com.gnomegpt.wiki;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class GePriceClient
{
    private static final Logger log = LoggerFactory.getLogger(GePriceClient.class);
    private static final String PRICES_API = "https://prices.runescape.wiki/api/v1/osrs";
    private static final String USER_AGENT = "GnomeGPT/1.0 (RuneLite Plugin; https://github.com/gnomegpt/gnomegpt)";
    private static final NumberFormat NUM_FORMAT = NumberFormat.getNumberInstance(Locale.US);

    private final OkHttpClient httpClient;
    private JsonObject mappingById;
    private volatile boolean mappingLoaded = false;

    public GePriceClient()
    {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();
    }

    public String lookup(String itemName) throws IOException
    {
        int itemId = findItemId(itemName);
        if (itemId == -1)
        {
            return "Couldn't find '" + itemName + "' — try the exact in-game name.";
        }

        String url = PRICES_API + "/latest?id=" + itemId;
        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null)
            {
                return "Failed to fetch price data.";
            }

            String body = response.body().string();
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            JsonObject data = json.getAsJsonObject("data");

            if (data == null || !data.has(String.valueOf(itemId)))
            {
                return "No GE data for '" + itemName + "'.";
            }

            JsonObject itemData = data.getAsJsonObject(String.valueOf(itemId));

            StringBuilder result = new StringBuilder();
            result.append("💰 ").append(itemName);

            if (itemData.has("high") && !itemData.get("high").isJsonNull())
            {
                result.append("\n  Buy: ").append(formatGp(itemData.get("high").getAsLong())).append(" gp");
            }

            if (itemData.has("low") && !itemData.get("low").isJsonNull())
            {
                result.append("\n  Sell: ").append(formatGp(itemData.get("low").getAsLong())).append(" gp");
            }

            if (itemData.has("highTime") && !itemData.get("highTime").isJsonNull())
            {
                long minutesAgo = (System.currentTimeMillis() / 1000 - itemData.get("highTime").getAsLong()) / 60;
                result.append("\n  Last trade: ").append(minutesAgo).append("m ago");
            }

            String wikiUrl = "https://oldschool.runescape.wiki/w/" +
                URLEncoder.encode(itemName.replace(" ", "_"), StandardCharsets.UTF_8.toString());
            result.append("\n  Wiki: ").append(wikiUrl);

            return result.toString();
        }
    }

    private int findItemId(String itemName) throws IOException
    {
        ensureMappingLoaded();

        if (mappingById != null)
        {
            String searchLower = itemName.toLowerCase().trim();
            for (Map.Entry<String, JsonElement> entry : mappingById.entrySet())
            {
                JsonObject item = entry.getValue().getAsJsonObject();
                if (item.has("name") && item.get("name").getAsString().toLowerCase().equals(searchLower))
                {
                    return Integer.parseInt(entry.getKey());
                }
            }
            for (Map.Entry<String, JsonElement> entry : mappingById.entrySet())
            {
                JsonObject item = entry.getValue().getAsJsonObject();
                if (item.has("name"))
                {
                    String name = item.get("name").getAsString().toLowerCase();
                    if (name.contains(searchLower) || searchLower.contains(name))
                    {
                        return Integer.parseInt(entry.getKey());
                    }
                }
            }
        }

        return -1;
    }

    private synchronized void ensureMappingLoaded() throws IOException
    {
        if (mappingLoaded) return;

        String url = PRICES_API + "/mapping";
        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (response.isSuccessful() && response.body() != null)
            {
                String body = response.body().string();
                mappingById = new JsonObject();
                for (JsonElement el : JsonParser.parseString(body).getAsJsonArray())
                {
                    JsonObject item = el.getAsJsonObject();
                    String id = String.valueOf(item.get("id").getAsInt());
                    mappingById.add(id, item);
                }
                mappingLoaded = true;
                log.info("Loaded {} item mappings", mappingById.size());
            }
        }
    }

    private String formatGp(long amount)
    {
        if (amount >= 1_000_000_000) return String.format("%.2fB", amount / 1_000_000_000.0);
        if (amount >= 1_000_000) return String.format("%.2fM", amount / 1_000_000.0);
        if (amount >= 1_000) return String.format("%.1fK", amount / 1_000.0);
        return NUM_FORMAT.format(amount);
    }
}
