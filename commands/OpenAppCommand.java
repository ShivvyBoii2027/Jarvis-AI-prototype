package JarvisAI.commands;

public class OpenAppCommand implements Command {

    public boolean matches(String input) {
        return input.contains("open chrome");
    }

    public String execute(String input) {

        try {

            Runtime.getRuntime().exec(
                    "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe"
            );

            return "Opening Chrome.";

        } catch(Exception e) {
            return "Failed to open application.";
        }

    }
}