import javafx.application.Application;
import JarvisAI.database.DatabaseManager;
import JarvisAI.ui.JarvisUI;

public class Main {

    public static void main(String[] args) {
        DatabaseManager.initializeDatabase();
        Application.launch(JarvisUI.class, args);
        
        
    }
}