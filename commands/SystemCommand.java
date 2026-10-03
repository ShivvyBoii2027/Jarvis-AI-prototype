package JarvisAI.commands;

public class SystemCommand implements Command {

    public boolean matches(String input) {
        String lower = input.toLowerCase();
        return lower.contains("shutdown") || lower.contains("restart") || lower.contains("lock");
    }

    public String execute(String input) {

        String lower = input.toLowerCase();

        try {

            if (lower.contains("shutdown")) {
                Runtime.getRuntime().exec("shutdown /s /t 60");
                return "Shutting down in 60 seconds. Type 'shutdown /a' in cmd to cancel.";
            }

            if (lower.contains("restart")) {
                Runtime.getRuntime().exec("shutdown /r /t 60");
                return "Restarting in 60 seconds. Type 'shutdown /a' in cmd to cancel.";
            }

            if (lower.contains("lock")) {
                Runtime.getRuntime().exec("rundll32.exe user32.dll,LockWorkStation");
                return "Locking workstation.";
            }

        } catch (Exception e) {
            return "Failed to execute system command.";
        }

        return "Unknown system command.";
    }
}