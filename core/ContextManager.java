package JarvisAI.core;

import JarvisAI.database.DatabaseManager;

public class ContextManager {

    public static String buildContext(String userInput) {

        String history = DatabaseManager.getRecentMemory();

        return history + " User: " + userInput;

    }

}