package JarvisAI.database;

import JarvisAI.config.Config;

import java.sql.*;

public class DatabaseManager {

    private static boolean dbAvailable = false;

    public static void initializeDatabase() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(
                    Config.DB_URL, Config.DB_USER, Config.DB_PASS);
            Statement stmt = conn.createStatement();
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS conversations(
                id INT AUTO_INCREMENT PRIMARY KEY,
                user_message TEXT,
                jarvis_response TEXT,
                timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            conn.close();
            dbAvailable = true;
            System.out.println("Database connected successfully.");
        } catch (Exception e) {
            System.out.println("Database unavailable, running without memory.");
        }
    }

    public static void saveConversation(String user, String jarvis) {
        if (!dbAvailable) return;
        try (Connection conn = DriverManager.getConnection(
                Config.DB_URL, Config.DB_USER, Config.DB_PASS)) {
            String sql = "INSERT INTO conversations(user_message, jarvis_response) VALUES (?,?)";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, user);
            stmt.setString(2, jarvis);
            stmt.executeUpdate();
        } catch (Exception e) {
            System.out.println("Could not save conversation: " + e.getMessage());
        }
    }

    public static String getRecentMemory() {
        if (!dbAvailable) return "";
        StringBuilder memory = new StringBuilder();
        try (Connection conn = DriverManager.getConnection(
                Config.DB_URL, Config.DB_USER, Config.DB_PASS)) {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                "SELECT user_message, jarvis_response FROM conversations ORDER BY id DESC LIMIT 6"
            );
            while (rs.next()) {
                memory.append("User: ")
                      .append(rs.getString("user_message"))
                      .append(" Jarvis: ")
                      .append(rs.getString("jarvis_response"))
                      .append(" ");
            }
        } catch (Exception e) {
            System.out.println("Could not load memory: " + e.getMessage());
        }
        return memory.toString();
    }
}