package JarvisAI.commands;

import JarvisAI.config.Config;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

public class ImageAnalysisCommand implements Command {

    @Override
    public boolean matches(String input) {
        String l = input.toLowerCase().trim();
        return l.startsWith("analyze image") || l.startsWith("analyse image")
            || l.startsWith("what is in")    || l.startsWith("describe image")
            || l.startsWith("look at image") || l.startsWith("what's in this image");
    }

    @Override
    public String execute(String input) {
        // Extract file path
        String path = null;
        String[] prefixes = {"analyze image ", "analyse image ", "describe image ",
                             "look at image ", "what is in ", "what's in this image "};
        String l = input.toLowerCase().trim();
        for (String p : prefixes) {
            if (l.startsWith(p)) {
                path = input.substring(p.length()).trim();
                break;
            }
        }

        if (path == null || path.isEmpty())
            return "Please provide a file path. Example: analyze image C:\\Users\\you\\photo.jpg";

        File file = new File(path);
        if (!file.exists()) return "File not found: " + path;

        // Determine media type
        String name = file.getName().toLowerCase();
        String mediaType;
        if      (name.endsWith(".jpg") || name.endsWith(".jpeg")) mediaType = "image/jpeg";
        else if (name.endsWith(".png"))  mediaType = "image/png";
        else if (name.endsWith(".gif"))  mediaType = "image/gif";
        else if (name.endsWith(".bmp"))  mediaType = "image/bmp";
        else if (name.endsWith(".webp")) mediaType = "image/webp";
        else return "Unsupported image type. Use JPG, PNG, GIF, BMP, or WEBP.";

        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] bytes = fis.readAllBytes();
            fis.close();
            String base64 = Base64.getEncoder().encodeToString(bytes);

            String json = "{"
                + "\"model\":\"meta-llama/llama-4-scout-17b-16e-instruct\","
                + "\"max_tokens\":1024,"
                + "\"messages\":[{"
                + "  \"role\":\"user\","
                + "  \"content\":["
                + "    {\"type\":\"image_url\","
                + "     \"image_url\":{\"url\":\"data:" + mediaType + ";base64," + base64 + "\"}},"
                + "    {\"type\":\"text\","
                + "     \"text\":\"Describe what you see in this image in detail. Include objects, people, text, colors, and anything notable.\"}"
                + "  ]"
                + "}]}";

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
            System.out.println("IMAGE ANALYSIS RESPONSE: " + body);

            String content = extractContent(body);
            return "Image Analysis:\n" + content;

        } catch (Exception e) {
            System.out.println("Image analysis error: " + e.getMessage());
            return "Image analysis failed: " + e.getMessage();
        }
    }

    private static String extractContent(String body) {
        int assistantIdx = body.indexOf("\"assistant\"");
        if (assistantIdx == -1) assistantIdx = 0;

        int contentIdx = body.indexOf("\"content\":\"", assistantIdx);
        if (contentIdx == -1) return "Could not parse image analysis response.";

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
            } else if (c == '"') {
                break;
            } else {
                result.append(c);
                i++;
            }
        }

        String content = result.toString().trim();
        return content.isEmpty() ? "No description returned." : content;
    }
}