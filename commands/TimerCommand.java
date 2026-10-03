package JarvisAI.commands;

import JarvisAI.voice.TextToSpeech;
import javafx.application.Platform;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Consumer;

public class TimerCommand implements Command {

    private static Consumer<String> uiCallback = null;
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    public static void setUICallback(Consumer<String> callback) {
        uiCallback = callback;
    }

    @Override
    public boolean matches(String input) {
        String l = input.toLowerCase().trim();
        return l.contains("timer") || l.contains("alarm") || l.contains("remind me")
            || l.contains("set a timer") || l.contains("wait for")
            || l.contains("in _ minutes") || (l.contains("remind") && l.contains("minute"))
            || (l.contains("remind") && l.contains("second"))
            || (l.contains("remind") && l.contains("hour"));
    }

    @Override
    public String execute(String input) {
        String l = input.toLowerCase().trim();

        // Extract time
        int seconds = 0;
        int hours   = extractUnit(input, "hour");
        int minutes = extractUnit(input, "minute") + extractUnit(input, "min");
        int secs    = extractUnit(input, "second") + extractUnit(input, "sec");
        seconds = hours * 3600 + minutes * 60 + secs;

        // If no time found, try bare number + "minutes" default
        if (seconds == 0) {
            Matcher m = Pattern.compile("(\\d+)").matcher(input);
            if (m.find()) {
                seconds = Integer.parseInt(m.group(1)) * 60; // assume minutes
            }
        }

        if (seconds <= 0) return "Please specify a time. Example: 'set a timer for 5 minutes'.";

        final int finalSeconds = seconds;
        String label = formatTime(seconds);

        scheduler.schedule(() -> {
            String msg = "⏰ Timer complete! " + label + " has elapsed.";
            TextToSpeech.speak("Timer complete. " + label + " has elapsed.");
            if (uiCallback != null) {
                Platform.runLater(() -> uiCallback.accept(msg));
            }
        }, finalSeconds, TimeUnit.SECONDS);

        return "Timer set for " + label + ". I'll alert you when it's done.";
    }

    private int extractUnit(String input, String unit) {
        Pattern p = Pattern.compile("(\\d+)\\s*" + unit);
        Matcher m = p.matcher(input.toLowerCase());
        if (m.find()) return Integer.parseInt(m.group(1));
        return 0;
    }

    private String formatTime(int seconds) {
        if (seconds >= 3600) {
            int h = seconds / 3600;
            int m = (seconds % 3600) / 60;
            return h + " hour" + (h > 1 ? "s" : "") + (m > 0 ? " " + m + " minutes" : "");
        } else if (seconds >= 60) {
            int m = seconds / 60;
            int s = seconds % 60;
            return m + " minute" + (m > 1 ? "s" : "") + (s > 0 ? " " + s + " seconds" : "");
        } else {
            return seconds + " second" + (seconds > 1 ? "s" : "");
        }
    }
}