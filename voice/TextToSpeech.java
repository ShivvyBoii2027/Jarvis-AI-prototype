package JarvisAI.voice;

public class TextToSpeech {

    public static void speak(String text) {
        if (text == null || text.trim().isEmpty()) return;

        // Sanitize — remove characters that break PowerShell string
        String clean = text
            .replace("'", " ")
            .replace("\"", " ")
            .replace("`", " ")
            .replace("\n", " ")
            .replace("\r", " ")
            .replaceAll("[^\\x20-\\x7E]", ""); // ASCII printable only

        // Keep TTS short — don't read massive AI responses aloud
        if (clean.length() > 300) {
            clean = clean.substring(0, 300);
        }

        try {
            String script =
                "Add-Type -AssemblyName System.Speech;" +
                "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer;" +
                "$s.Rate = 1;" +
                "$s.Volume = 100;" +
                "$s.Speak('" + clean + "');" +
                "$s.Dispose();";

            new ProcessBuilder(
                "powershell", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                "-Command", script
            ).start();

        } catch (Exception e) {
            System.out.println("TTS error: " + e.getMessage());
        }
    }
}