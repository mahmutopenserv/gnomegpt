package com.gnomegpt.ironman;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Tracks progress through the BRUHsailer ironman guide.
 * Progress is saved to a JSON file in the RuneLite directory.
 */
public class IronmanGuide
{
    private static final Logger log = LoggerFactory.getLogger(IronmanGuide.class);
    private static final String PROGRESS_FILE = "gnomegpt-ironman-progress.json";

    private final List<Chapter> chapters = new ArrayList<>();
    private final Set<String> completedSteps = new HashSet<>();
    private boolean loaded = false;

    public IronmanGuide()
    {
        loadGuideData();
        loadProgress();
    }

    private void loadGuideData()
    {
        try (InputStream is = getClass().getResourceAsStream("/data/bruhsailer_guide.json"))
        {
            if (is == null)
            {
                log.warn("bruhsailer_guide.json not found");
                return;
            }

            JsonObject root = JsonParser.parseReader(
                new InputStreamReader(is, StandardCharsets.UTF_8)
            ).getAsJsonObject();

            JsonArray chaptersArr = root.getAsJsonArray("chapters");
            if (chaptersArr == null) return;

            int globalStep = 0;
            for (JsonElement chEl : chaptersArr)
            {
                JsonObject chObj = chEl.getAsJsonObject();
                String chTitle = chObj.get("title").getAsString();
                Chapter chapter = new Chapter(chTitle);

                JsonArray sections = chObj.getAsJsonArray("sections");
                if (sections != null)
                {
                    for (JsonElement secEl : sections)
                    {
                        JsonObject secObj = secEl.getAsJsonObject();
                        String secTitle = secObj.get("title").getAsString();
                        Section section = new Section(secTitle);

                        JsonArray steps = secObj.getAsJsonArray("steps");
                        if (steps != null)
                        {
                            for (JsonElement stepEl : steps)
                            {
                                JsonObject stepObj = stepEl.getAsJsonObject();
                                String stepId = "step_" + globalStep;

                                // Extract text content
                                StringBuilder text = new StringBuilder();
                                JsonArray content = stepObj.getAsJsonArray("content");
                                if (content != null)
                                {
                                    for (JsonElement cEl : content)
                                    {
                                        JsonObject cObj = cEl.getAsJsonObject();
                                        if (cObj.has("text"))
                                        {
                                            text.append(cObj.get("text").getAsString());
                                        }
                                    }
                                }

                                // Extract metadata
                                String itemsNeeded = "";
                                String time = "";
                                JsonObject meta = stepObj.getAsJsonObject("metadata");
                                if (meta != null)
                                {
                                    if (meta.has("items_needed"))
                                        itemsNeeded = meta.get("items_needed").getAsString();
                                    if (meta.has("total_time"))
                                        time = meta.get("total_time").getAsString();
                                }

                                Step step = new Step(stepId, text.toString().trim(), itemsNeeded, time);
                                section.steps.add(step);
                                globalStep++;
                            }
                        }
                        chapter.sections.add(section);
                    }
                }
                chapters.add(chapter);
            }

            loaded = true;
            log.info("Loaded BRUHsailer guide: {} chapters, {} total steps",
                chapters.size(), globalStep);
        }
        catch (Exception e)
        {
            log.error("Failed to load ironman guide", e);
        }
    }

    /**
     * Get the current step (first uncompleted).
     */
    public String getCurrentStep()
    {
        if (!loaded) return "Ironman guide data not loaded.";

        for (Chapter ch : chapters)
        {
            for (Section sec : ch.sections)
            {
                for (Step step : sec.steps)
                {
                    if (!completedSteps.contains(step.id))
                    {
                        StringBuilder sb = new StringBuilder();
                        sb.append("📍 **").append(ch.title).append("**\n");
                        sb.append("Section: ").append(sec.title).append("\n\n");

                        String content = step.content;
                        if (content.length() > 500)
                        {
                            content = content.substring(0, 500) + "...";
                        }
                        sb.append(content);

                        if (!step.itemsNeeded.isEmpty() && !step.itemsNeeded.equals("none"))
                        {
                            sb.append("\n\n🎒 Items: ").append(step.itemsNeeded);
                        }
                        if (!step.time.isEmpty())
                        {
                            sb.append("\n⏱️ Est. time: ").append(step.time);
                        }

                        sb.append("\n\nType /iron next to mark complete, /iron status for progress.");
                        return sb.toString();
                    }
                }
            }
        }

        return "🎉 You've completed the entire BRUHsailer guide! Congrats!";
    }

    /**
     * Mark the current step as complete and advance.
     */
    public String markCurrentComplete()
    {
        if (!loaded) return "Guide not loaded.";

        for (Chapter ch : chapters)
        {
            for (Section sec : ch.sections)
            {
                for (Step step : sec.steps)
                {
                    if (!completedSteps.contains(step.id))
                    {
                        completedSteps.add(step.id);
                        saveProgress();
                        return "✅ Step completed! " + getCurrentStep();
                    }
                }
            }
        }
        return "All steps complete!";
    }

    /**
     * Go back one step.
     */
    public String undoLastStep()
    {
        if (!loaded) return "Guide not loaded.";

        // Find the last completed step
        String lastCompleted = null;
        for (Chapter ch : chapters)
        {
            for (Section sec : ch.sections)
            {
                for (Step step : sec.steps)
                {
                    if (completedSteps.contains(step.id))
                    {
                        lastCompleted = step.id;
                    }
                }
            }
        }

        if (lastCompleted != null)
        {
            completedSteps.remove(lastCompleted);
            saveProgress();
            return "↩️ Undid last step. " + getCurrentStep();
        }
        return "Nothing to undo.";
    }

    /**
     * Get progress overview.
     */
    public String getStatus()
    {
        if (!loaded) return "Guide not loaded.";

        int totalSteps = 0;
        int currentChapter = 0;
        String currentSection = "";
        boolean foundCurrent = false;

        for (Chapter ch : chapters)
        {
            for (Section sec : ch.sections)
            {
                for (Step step : sec.steps)
                {
                    totalSteps++;
                    if (!completedSteps.contains(step.id) && !foundCurrent)
                    {
                        currentChapter = chapters.indexOf(ch) + 1;
                        currentSection = sec.title;
                        foundCurrent = true;
                    }
                }
            }
        }

        int completed = completedSteps.size();
        double pct = totalSteps > 0 ? (completed * 100.0 / totalSteps) : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("📊 **BRUHsailer Ironman Progress**\n\n");
        sb.append(String.format("%.1f%% complete (%d / %d steps)\n", pct, completed, totalSteps));
        if (foundCurrent)
        {
            sb.append("Currently on: Chapter ").append(currentChapter);
            sb.append(" — ").append(currentSection);
        }
        else
        {
            sb.append("🎉 All steps completed!");
        }

        return sb.toString();
    }

    /**
     * Reset all progress.
     */
    public String reset()
    {
        completedSteps.clear();
        saveProgress();
        return "Progress reset. " + getCurrentStep();
    }

    private void loadProgress()
    {
        try
        {
            Path path = getProgressPath();
            if (Files.exists(path))
            {
                String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
                for (JsonElement el : arr)
                {
                    completedSteps.add(el.getAsString());
                }
                log.info("Loaded {} completed ironman steps", completedSteps.size());
            }
        }
        catch (Exception e)
        {
            log.warn("Could not load ironman progress", e);
        }
    }

    private void saveProgress()
    {
        try
        {
            Path path = getProgressPath();
            JsonArray arr = new JsonArray();
            for (String id : completedSteps) arr.add(id);
            Files.write(path, arr.toString().getBytes(StandardCharsets.UTF_8));
        }
        catch (Exception e)
        {
            log.warn("Could not save ironman progress", e);
        }
    }

    private Path getProgressPath()
    {
        String home = System.getProperty("user.home");
        Path dir = Paths.get(home, ".runelite");
        if (!Files.exists(dir))
        {
            try { Files.createDirectories(dir); } catch (Exception e) {}
        }
        return dir.resolve(PROGRESS_FILE);
    }

    public boolean isLoaded()
    {
        return loaded;
    }

    // Data classes
    static class Chapter
    {
        final String title;
        final List<Section> sections = new ArrayList<>();
        Chapter(String title) { this.title = title; }
    }

    static class Section
    {
        final String title;
        final List<Step> steps = new ArrayList<>();
        Section(String title) { this.title = title; }
    }

    static class Step
    {
        final String id;
        final String content;
        final String itemsNeeded;
        final String time;
        Step(String id, String content, String itemsNeeded, String time)
        {
            this.id = id;
            this.content = content;
            this.itemsNeeded = itemsNeeded;
            this.time = time;
        }
    }
}
