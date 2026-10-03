package JarvisAI.commands;

import JarvisAI.config.Config;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;

public class WebSearchCommand implements Command {

    @Override
    public boolean matches(String input) {
        String l = input.toLowerCase().trim();
        return l.startsWith("search for") || l.startsWith("search ")
            || l.startsWith("look up") || l.startsWith("google ")
            || l.startsWith("find information") || l.startsWith("what is the latest")
            || l.startsWith("who is ") || l.startsWith("what is ")
            || l.startsWith("how do ") || l.startsWith("how does ")
            || l.startsWith("tell me about") || l.startsWith("news about")
            || l.startsWith("latest news");
    }

    @Override
    public String execute(String input) {
        String query = extractQuery(input);
        if (query.isEmpty()) return "What would you like me to search for?";

        try {
            // Use DuckDuckGo instant answer API (no key needed)
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://api.duckduckgo.com/?q=" + encoded + "&format=json&no_html=1&skip_disambig=1";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "JarvisAI/1.0")
                .GET()
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            // Extract AbstractText from DDG response
            String abstractText = extractField(body, "AbstractText");
            String answer = extractField(body, "Answer");
            String definition = extractField(body, "Definition");

            String result = "";
            if (!answer.isEmpty())       result = answer;
            else if (!abstractText.isEmpty()) result = abstractText;
            else if (!definition.isEmpty())   result = definition;

            if (!result.isEmpty()) {
                // Trim to reasonable length
                if (result.length() > 600) result = result.substring(0, 600) + "...";
                return "Search result for \"" + query + "\":\n" + result;
            }

            // Fallback: ask Groq AI about it directly
            return askGroqAbout(query);

        } catch (Exception e) {
            System.out.println("Web search error: " + e.getMessage());
            return askGroqAbout(query);
        }
    }

    private String askGroqAbout(String query) {
        try {
            String json = "{"
                + "\"model\":\"llama-3.3-70b-versatile\","
                + "\"max_tokens\":400,"
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":\"You are J.A.R.V.I.S. Answer the user's question concisely and accurately in 2-3 sentences.\"},"
                + "{\"role\":\"user\",\"content\":" + toJson(query) + "}"
                + "]}";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + Config.GROQ_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return extractContent(response.body());
        } catch (Exception e) {
            return "Could not retrieve search results: " + e.getMessage();
        }
    }

    private String extractQuery(String input) {
        String[] prefixes = {"search for ", "search ", "look up ", "google ",
                             "find information about ", "tell me about ", "news about ",
                             "latest news on ", "what is the latest on "};
        String l = input.toLowerCase();
        for (String p : prefixes) {
            if (l.startsWith(p)) return input.substring(p.length()).trim();
        }
        return input.trim();
    }

    private String extractField(String json, String field) {
        String key = "\"" + field + "\":\"";
        int idx = json.indexOf(key);
        if (idx == -1) return "";
        int start = idx + key.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return "";
        String val = json.substring(start, end);
        return val.replace("\\n", "\n").replace("\\\"", "\"");
    }

    private String extractContent(String body) {
        int assistantIdx = body.indexOf("\"assistant\"");
        if (assistantIdx == -1) assistantIdx = 0;
        int contentIdx = body.indexOf("\"content\":\"", assistantIdx);
        if (contentIdx == -1) return "No response.";
        int start = contentIdx + 11;
        StringBuilder result = new StringBuilder();
        int i = start;
        while (i < body.length()) {
            char c = body.charAt(i);
            if (c == '\\' && i + 1 < body.length()) {
                char next = body.charAt(i + 1);
                switch (next) {
                    case 'n': result.append('\n'); i += 2; break;
                    case 't': result.append('\t'); i += 2; break;
                    case 'r': i += 2; break;
                    case '"': result.append('"'); i += 2; break;
                    case '\\': result.append('\\'); i += 2; break;
                    default: result.append(c); i++; break;
                }
            } else if (c == '"') break;
            else { result.append(c); i++; }
        }
        return result.toString().trim();
    }

    private String toJson(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                       .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}