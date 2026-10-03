package JarvisAI.commands;

import java.awt.*;
import java.awt.datatransfer.*;

public class ClipboardCommand implements Command {

    @Override
    public boolean matches(String input) {
        String l = input.toLowerCase().trim();
        return l.contains("clipboard") || l.contains("what did i copy")
            || l.contains("what's in my clipboard") || l.contains("whats in my clipboard")
            || l.contains("read clipboard") || l.contains("copy to clipboard")
            || l.contains("paste") && l.contains("jarvis");
    }

    @Override
    public String execute(String input) {
        String l = input.toLowerCase().trim();

        if (l.contains("read clipboard") || l.contains("what did i copy")
                || l.contains("what's in my clipboard") || l.contains("whats in my clipboard")
                || l.contains("clipboard")) {
            try {
                Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
                Transferable t = cb.getContents(null);
                if (t != null && t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    String text = (String) t.getTransferData(DataFlavor.stringFlavor);
                    if (text.length() > 500) text = text.substring(0, 500) + "...";
                    return "Clipboard contains:\n" + text;
                }
                return "Clipboard is empty or contains non-text content.";
            } catch (Exception e) {
                return "Could not read clipboard: " + e.getMessage();
            }
        }

        return "I can read your clipboard. Try 'what's in my clipboard'.";
    }
}