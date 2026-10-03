package JarvisAI.commands;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class NotesCommand implements Command {

    private static final String NOTES_FILE =
        System.getProperty("user.home") + "\\Desktop\\JarvisNotes.txt";

    @Override
    public boolean matches(String input) {
        String l = input.toLowerCase().trim();
        return l.startsWith("remember") || l.startsWith("note that") || l.startsWith("make a note")
            || l.startsWith("save note") || l.startsWith("write down")
            || l.contains("show my notes") || l.contains("read my notes")
            || l.contains("what do i have noted") || l.contains("show notes")
            || l.startsWith("forget") || l.contains("clear notes") || l.contains("delete notes");
    }

    @Override
    public String execute(String input) {
        String l = input.toLowerCase().trim();

        // SHOW NOTES
        if (l.contains("show my notes") || l.contains("read my notes")
                || l.contains("what do i have noted") || l.contains("show notes")) {
            return readNotes();
        }

        // CLEAR NOTES
        if (l.contains("clear notes") || l.contains("delete notes")) {
            try {
                Files.write(Paths.get(NOTES_FILE), new byte[0]);
                return "All notes cleared.";
            } catch (Exception e) {
                return "Could not clear notes: " + e.getMessage();
            }
        }

        // SAVE NOTE
        String noteText = extractNote(input);
        if (noteText == null || noteText.isEmpty()) return "What would you like me to note?";
        return saveNote(noteText);
    }

    private String extractNote(String input) {
        String[] prefixes = {"remember that ", "remember ", "note that ", "make a note ",
                             "make a note that ", "save note ", "write down "};
        String l = input.toLowerCase();
        for (String p : prefixes) {
            if (l.startsWith(p)) return input.substring(p.length()).trim();
        }
        return input.trim();
    }

    private String saveNote(String note) {
        try {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date());
            String entry = "[" + timestamp + "] " + note + "\n";
            Files.write(Paths.get(NOTES_FILE), entry.getBytes(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return "Noted: \"" + note + "\"\nSaved to Desktop\\JarvisNotes.txt";
        } catch (Exception e) {
            return "Could not save note: " + e.getMessage();
        }
    }

    private String readNotes() {
        try {
            File f = new File(NOTES_FILE);
            if (!f.exists() || f.length() == 0) return "No notes saved yet.";
            String content = new String(Files.readAllBytes(Paths.get(NOTES_FILE)));
            if (content.length() > 1000) content = content.substring(content.length() - 1000); // last 1000 chars
            return "Your notes:\n" + content.trim();
        } catch (Exception e) {
            return "Could not read notes: " + e.getMessage();
        }
    }
}