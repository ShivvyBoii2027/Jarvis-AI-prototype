package JarvisAI.voice;

import JarvisAI.config.Config;

import javax.sound.sampled.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;

public class SpeechRecognizer {

    private static final int SAMPLE_RATE       = 16000;
    private static final int RECORD_SECONDS     = 6;
    private static final int WAKE_WORD_SECONDS  = 3;
    private static final double SILENCE_THRESHOLD = 300.0;

    // Known Whisper hallucinations for silence
    private static final String[] FILLER_PHRASES = {
        "thank you.", "thank you", "you", ".", "thanks", "thanks.",
        "bye.", "bye", "yes.", "yes", "no.", "no", "okay.", "okay",
        "um.", "um", "uh.", "uh", "hmm.", "hmm"
    };

    /** Full 6-second listen for command input */
    public static String listen() {
        return recordAndTranscribe(RECORD_SECONDS, "jarvis_mic.wav");
    }

    /** Short 3-second listen for wake word detection */
    public static String listenShort() {
        return recordAndTranscribe(WAKE_WORD_SECONDS, "jarvis_wake.wav");
    }

    private static String recordAndTranscribe(int seconds, String filename) {
        File wavFile = new File(System.getProperty("java.io.tmpdir") + "\\" + filename);

        try {
            AudioFormat format = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                SAMPLE_RATE, 16, 1, 2, SAMPLE_RATE, false
            );

            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                System.out.println("Microphone not supported.");
                return "";
            }

            TargetDataLine mic = (TargetDataLine) AudioSystem.getLine(info);
            mic.open(format);
            mic.start();

            Thread.sleep(150); // brief delay to avoid button-click noise

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            long end = System.currentTimeMillis() + (seconds * 1000L);
            while (System.currentTimeMillis() < end) {
                int read = mic.read(buffer, 0, buffer.length);
                if (read > 0) baos.write(buffer, 0, read);
            }

            mic.stop();
            mic.close();

            byte[] audioData = baos.toByteArray();

            // Silence check
            double rms = calculateRMS(audioData);
            if (rms < SILENCE_THRESHOLD) return "";

            // Save WAV
            AudioInputStream ais = new AudioInputStream(
                new ByteArrayInputStream(audioData),
                format,
                audioData.length / format.getFrameSize()
            );
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, wavFile);

            // Transcribe
            String result = transcribeWithWhisper(wavFile);
            wavFile.delete();
            return result;

        } catch (Exception e) {
            System.out.println("SpeechRecognizer error: " + e.getMessage());
            return "";
        }
    }

    private static double calculateRMS(byte[] audioData) {
        long sumSquares = 0;
        int count = 0;
        for (int i = 0; i + 1 < audioData.length; i += 2) {
            short sample = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));
            sumSquares += (long) sample * sample;
            count++;
        }
        return count == 0 ? 0 : Math.sqrt((double) sumSquares / count);
    }

    private static String transcribeWithWhisper(File wavFile) throws Exception {
        String boundary = "----JarvisBoundary" + System.currentTimeMillis();
        byte[] fileBytes = Files.readAllBytes(wavFile.toPath());

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(body, "UTF-8"), true);

        pw.print("--" + boundary + "\r\n");
        pw.print("Content-Disposition: form-data; name=\"model\"\r\n\r\n");
        pw.print("whisper-large-v3\r\n");

        pw.print("--" + boundary + "\r\n");
        pw.print("Content-Disposition: form-data; name=\"language\"\r\n\r\n");
        pw.print("en\r\n");

        pw.print("--" + boundary + "\r\n");
        pw.print("Content-Disposition: form-data; name=\"response_format\"\r\n\r\n");
        pw.print("json\r\n");

        pw.print("--" + boundary + "\r\n");
        pw.print("Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n");
        pw.print("Content-Type: audio/wav\r\n\r\n");
        pw.flush();

        body.write(fileBytes);

        pw.print("\r\n--" + boundary + "--\r\n");
        pw.flush();

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.groq.com/openai/v1/audio/transcriptions"))
            .header("Authorization", "Bearer " + Config.GROQ_KEY)
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
            .build();

        HttpResponse<String> response = client.send(request,
            HttpResponse.BodyHandlers.ofString());

        String respBody = response.body();
        int textIdx = respBody.indexOf("\"text\":\"");
        if (textIdx != -1) {
            int start = textIdx + 8;
            int end = respBody.indexOf("\"", start);
            if (end != -1) {
                String text = respBody.substring(start, end).trim();
                String lower = text.toLowerCase();

                // Filter filler phrases
                for (String filler : FILLER_PHRASES) {
                    if (lower.equals(filler)) return "";
                }
                if (text.length() < 2) return "";

                return text;
            }
        }
        return "";
    }
}