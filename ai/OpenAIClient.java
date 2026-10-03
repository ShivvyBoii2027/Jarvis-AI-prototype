package JarvisAI.ai;

import JarvisAI.config.Config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class OpenAIClient {

    public static String askAI(String userMessage, String memory) {
        try {
            String systemPrompt = "You are J.A.R.V.I.S, an advanced AI assistant. "
                + "You are helpful, concise, and slightly futuristic in tone. "
                + "Keep responses under 3 sentences unless detail is specifically needed. "
                + (memory != null && !memory.isEmpty()
                    ? "Recent conversation context: " + memory
                    : "");

            String json = "{"
                + "\"model\":\"llama-3.3-70b-versatile\","
                + "\"max_tokens\":500,"
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":" + toJson(systemPrompt) + "},"
                + "{\"role\":\"user\",\"content\":" + toJson(userMessage) + "}"
                + "]}";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + Config.GROQ_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

            String body = response.body();

            // The response looks like:
            // {"choices":[{"message":{"role":"assistant","content":"TEXT HERE"},...}],...}
            // We find "content": after "assistant" to get the right one
            return extractContent(body);

        } catch (Exception e) {
            System.out.println("AI error: " + e.getMessage());
            return "AI service unavailable: " + e.getMessage();
        }
    }

    private static String extractContent(String body) {
        // Find "role":"assistant" then grab the content after it
        int assistantIdx = body.indexOf("\"assistant\"");
        if (assistantIdx == -1) {
            // fallback: try finding content anywhere
            assistantIdx = 0;
        }

        // From that position, find the next "content":"
        int contentIdx = body.indexOf("\"content\":\"", assistantIdx);
        if (contentIdx == -1) {
            return "I couldn't parse the response.";
        }

        int start = contentIdx + 11; // skip past "content":"
        StringBuilder result = new StringBuilder();
        int i = start;

        // Walk character by character, handle escape sequences
        while (i < body.length()) {
            char c = body.charAt(i);
            if (c == '\\' && i + 1 < body.length()) {
                char next = body.charAt(i + 1);
                switch (next) {
                    case 'n': result.append('\n'); i += 2; break;
                    case 't': result.append('\t'); i += 2; break;
                    case 'r': i += 2; break; // skip carriage return
                    case '"': result.append('"'); i += 2; break;
                    case '\\': result.append('\\'); i += 2; break;
                    default: result.append(c); i++; break;
                }
            } else if (c == '"') {
                // End of content string
                break;
            } else {
                result.append(c);
                i++;
            }
        }

        String content = result.toString().trim();
        return content.isEmpty() ? "No response received." : content;
    }

    private static String toJson(String s) {
        return "\"" + s.replace("\\", "\\\\")
                       .replace("\"", "\\\"")
                       .replace("\n", "\\n")
                       .replace("\r", "\\r") + "\"";
    }
}