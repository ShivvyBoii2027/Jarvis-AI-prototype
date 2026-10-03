package JarvisAI.core;

import JarvisAI.ai.OpenAIClient;
import JarvisAI.database.DatabaseManager;

public class JarvisCore {

    private static CommandManager manager = new CommandManager();

    public static String process(String input) {
        System.out.println("JarvisCore received: '" + input + "'");

        String commandResult = manager.handleCommand(input);
        if (commandResult != null) {
            System.out.println("Handled by command.");
            return commandResult;
        }

        System.out.println("No command matched, sending to AI...");
        try {
            String memory = DatabaseManager.getRecentMemory();
            return OpenAIClient.askAI(input, memory);
        } catch (Exception e) {
            return "AI service unavailable.";
        }
    }
}