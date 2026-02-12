package com.gnomegpt.wiki;

import com.google.gson.JsonArray;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class OsrsWikiClient
{
    private static final Logger log = LoggerFactory.getLogger(OsrsWikiClient.class);
    private static final String WIKI_API = "https://oldschool.runescape.wiki/api.php";
    private static final String USER_AGENT = "GnomeGPT/1.0 (RuneLite Plugin; https://github.com/gnomegpt/gnomegpt)";
    private static final int MAX_CONTENT_LENGTH = 3000;

    private final OkHttpClient httpClient;

    public OsrsWikiClient()
    {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();
    }

    public List<String> search(String query, int maxResults) throws IOException
    {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
        String url = WIKI_API + "?action=opensearch&search=" + encoded
            + "&limit=" + maxResults + "&format=json";

        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null)
            {
                return new ArrayList<>();
            }

            String body = response.body().string();
            JsonArray arr = JsonParser.parseString(body).getAsJsonArray();

            if (arr.size() < 2)
            {
                return new ArrayList<>();
            }

            JsonArray titles = arr.get(1).getAsJsonArray();
            List<String> results = new ArrayList<>();
            for (JsonElement el : titles)
            {
                results.add(el.getAsString());
            }
            return results;
        }
    }

    public String getPageContent(String title) throws IOException
    {
        String encoded = URLEncoder.encode(title, StandardCharsets.UTF_8.toString());
        String url = WIKI_API + "?action=query&titles=" + encoded
            + "&prop=extracts&exintro=false&explaintext=true&format=json";

        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null)
            {
                return "";
            }

            String body = response.body().string();
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            JsonObject pages = json.getAsJsonObject("query").getAsJsonObject("pages");

            for (String key : pages.keySet())
            {
                JsonObject page = pages.getAsJsonObject(key);
                if (page.has("extract"))
                {
                    String extract = page.get("extract").getAsString();
                    if (extract.length() > MAX_CONTENT_LENGTH)
                    {
                        extract = extract.substring(0, MAX_CONTENT_LENGTH) + "\n...[truncated]";
                    }
                    return extract;
                }
            }
            return "";
        }
    }

    public String searchAndFetch(String query, int maxResults) throws IOException
    {
        List<String> titles = search(query, maxResults);
        if (titles.isEmpty())
        {
            return "";
        }

        StringBuilder context = new StringBuilder();
        for (String title : titles)
        {
            try
            {
                String content = getPageContent(title);
                if (!content.isEmpty())
                {
                    context.append("=== ").append(title).append(" ===\n");
                    context.append(content).append("\n\n");
                }
            }
            catch (IOException e)
            {
                log.warn("Failed to fetch wiki page: {}", title, e);
            }
        }
        return context.toString();
    }

    /**
     * Try to fetch a boss/monster strategy page directly.
     * Strategy pages are typically at "Boss_name/Strategies".
     */
    public String fetchStrategyPage(String bossName) throws IOException
    {
        // Try "Name/Strategies" format
        String strategyTitle = bossName.trim() + "/Strategies";
        String content = getPageContent(strategyTitle);
        if (!content.isEmpty())
        {
            return "=== " + strategyTitle + " ===\n" + content + "\n\n";
        }

        // Try searching for "boss strategy"
        List<String> results = search(bossName + " strategy", 1);
        if (!results.isEmpty())
        {
            content = getPageContent(results.get(0));
            if (!content.isEmpty())
            {
                return "=== " + results.get(0) + " ===\n" + content + "\n\n";
            }
        }

        return "";
    }
}
