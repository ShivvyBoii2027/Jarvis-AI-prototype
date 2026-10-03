package JarvisAI.commands;

import java.awt.Desktop;
import java.net.URI;

public class SearchCommand implements Command {

    public boolean matches(String input) {
        return input.toLowerCase().contains("search for") || input.toLowerCase().contains("google");
    }

    public String execute(String input) {

        try {

            String query = input.toLowerCase()
                    .replace("search for", "")
                    .replace("google", "")
                    .trim()
                    .replace(" ", "+");

            Desktop.getDesktop().browse(
                    new URI("https://www.google.com/search?q=" + query)
            );

            return "Searching for: " + query.replace("+", " ");

        } catch (Exception e) {
            return "Failed to open search.";
        }
    }
}