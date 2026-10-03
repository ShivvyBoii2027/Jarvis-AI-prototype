package JarvisAI.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class MultiTaskParser {

    // Separators that indicate multiple tasks
    private static final String[] SEPARATORS = {
        " and then ", " then ", " after that ", " and also ", " and ", " also "
    };

    /**
     * Returns true if the input contains multiple tasks
     */
    public static boolean isMultiTask(String input) {
        String l = input.toLowerCase();
        for (String sep : SEPARATORS) {
            if (l.contains(sep)) return true;
        }
        return false;
    }

    /**
     * Splits input into individual task strings
     */
    public static List<String> split(String input) {
        List<String> tasks = new ArrayList<>();
        String l = input.toLowerCase();
        String remaining = input;

        // Find the earliest separator
        int earliestIdx = -1;
        String earliestSep = null;

        for (String sep : SEPARATORS) {
            int idx = l.indexOf(sep);
            if (idx != -1 && (earliestIdx == -1 || idx < earliestIdx)) {
                earliestIdx = idx;
                earliestSep = sep;
            }
        }

        if (earliestIdx == -1 || earliestSep == null) {
            tasks.add(remaining.trim());
            return tasks;
        }

        // Recursively split
        String first = remaining.substring(0, earliestIdx).trim();
        String rest  = remaining.substring(earliestIdx + earliestSep.length()).trim();

        if (!first.isEmpty()) tasks.add(first);
        tasks.addAll(split(rest));

        return tasks;
    }

    /**
     * Resolves context between tasks.
     * Example: "open chrome and then open youtube in it"
     * → task2 "open youtube in it" → becomes "open https://youtube.com in chrome"
     */
    public static List<String> resolveContext(List<String> tasks) {
        List<String> resolved = new ArrayList<>();
        String lastApp = null;
        String lastURL = null;

        for (String task : tasks) {
            String l = task.toLowerCase();

            // Detect app reference
            for (String app : new String[]{"chrome", "edge", "firefox", "notepad", "spotify", "discord"}) {
                if (l.contains(app)) { lastApp = app; break; }
            }

            // Resolve "in it" or "in that" to last app
            if ((l.contains(" in it") || l.contains(" in that")) && lastApp != null) {
                task = task.replaceAll("(?i) in it| in that", " in " + lastApp);
            }

            // Resolve website shortcuts
            task = resolveWebsite(task);

            resolved.add(task);
        }
        return resolved;
    }

    /**
     * Expand common website names to full URLs
     */
    public static String resolveWebsite(String task) {
        String l = task.toLowerCase();

        // Pattern: "open youtube in chrome" → open chrome then navigate
        // We handle this in JarvisCore by executing sub-steps
        if (l.contains("open youtube")) return task.replace("youtube", "https://youtube.com");
        if (l.contains("open google"))  return task.replace("google",  "https://google.com");
        if (l.contains("open gmail"))   return task.replace("gmail",   "https://mail.google.com");
        if (l.contains("open reddit"))  return task.replace("reddit",  "https://reddit.com");
        if (l.contains("open twitter") || l.contains("open x")) return task.replace("twitter", "https://x.com").replace(" x ", " https://x.com ");
        if (l.contains("open netflix")) return task.replace("netflix", "https://netflix.com");
        if (l.contains("open amazon"))  return task.replace("amazon",  "https://amazon.com");
        if (l.contains("open github"))  return task.replace("github",  "https://github.com");
        if (l.contains("open instagram")) return task.replace("instagram", "https://instagram.com");
        if (l.contains("open twitch"))  return task.replace("twitch",  "https://twitch.tv");

        return task;
    }
}