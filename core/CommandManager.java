package JarvisAI.core;

import JarvisAI.commands.*;

import java.util.ArrayList;
import java.util.List;

public class CommandManager {

    private List<Command> commands = new ArrayList<>();

    public CommandManager() {
        commands.add(new PCAccessCommand());
        commands.add(new ImageAnalysisCommand());
        commands.add(new VolumeCommand());
        commands.add(new SystemStatsCommand());
        commands.add(new TimerCommand());
        commands.add(new NotesCommand());
        commands.add(new ClipboardCommand());
        commands.add(new WebSearchCommand());
        commands.add(new TimeCommand());
        commands.add(new SearchCommand());
        commands.add(new SystemCommand());
    }

    public String handleCommand(String input) {
        for (Command cmd : commands) {
            if (cmd.matches(input)) {
                return cmd.execute(input);
            }
        }
        return null;
    }
}