package JarvisAI.commands;

import JarvisAI.config.Config;
import JarvisAI.vision.CameraManager;

import java.net.URI;
import java.net.http.*;
import java.util.Base64;

public class VisionCommand implements Command {

    @Override
    public boolean matches(String input) {
        String l = input.toLowerCase();
        return l.contains("what is this") || l.contains("what do you see")
            || l.contains("what am i holding") || l.contains("identify this")
            || l.contains("look at this") || l.contains("can you see")
            || l.contains("describe what you see") || l.contains("what's in front")
            || l.contains("whats in front") || l.contains("analyse what")
            || l.contains("analyze what") || l.contains("look at me")
            || l.contains("what do i look like") || l.contains("see me");
    }

    @Override
    public String execute(String input) {
        byte[] frame = CameraManager.captureFrame();
        if (frame == null) {
            return "Camera is not available. Make sure the focus tracker is running.";
        }
        return analyzeFrame(frame, input);
    }

    public static String analyzeFrame(byte[] jpegBytes, String question) {
        try {
            String base64 = Base64.getEncoder().encodeToString(jpegBytes);

            String prompt = question.trim().isEmpty()
                ? "Describe in detail what you see in this image. Identify any objects, people, text, or notable features."
                : question;

            String body = "{"
                + "\"model\": \"meta-llama/llama-4-scout-17b-16e-instruct\","
                + "\"messages\": [{"
                + "  \"role\": \"user\","
                + "  \"content\": ["
                + "    {\"type\": \"image_url\", \"image_url\": {\"url\": \"data:image/jpeg;base64," + base64 + "\"}},"
                + "    {\"type\": \"text\", \"text\": \"" + escapeJson(prompt) + "\"}"
                + "  ]"
                + "}],"
                + "\"max_tokens\": 300"
                + "}";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + Config.GROQ_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            if (response.statusCode() != 200) {
                return "Vision API error " + response.statusCode();
            }

            int contentIndex = responseBody.indexOf("\"content\":");
            if (contentIndex == -1) return "Could not parse vision response.";
            int start = responseBody.indexOf("\"", contentIndex + 10) + 1;
            int end = start;
            while (end < responseBody.length()) {
                if (responseBody.charAt(end) == '"' && responseBody.charAt(end - 1) != '\\') break;
                end++;
            }
            return responseBody.substring(start, end)
                    .replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");

        } catch (Exception e) {
            return "Vision error: " + e.getMessage();
        }
    }

    private static String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
                   .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}