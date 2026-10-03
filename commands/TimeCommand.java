package JarvisAI.commands;

import java.time.LocalTime;

public class TimeCommand implements Command {

    public boolean matches(String input) {
        return input.contains("time");
    }

    public String execute(String input) {
        return "Current time is " + LocalTime.now();
    }
}
