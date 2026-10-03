package JarvisAI.commands;

import java.awt.Robot;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class PCAccessCommand implements Command {

    @Override
    public boolean matches(String input) {
        String l = input.toLowerCase().trim();
        return l.startsWith("open ")     || l.startsWith("launch ")
            || l.startsWith("start ")    || l.startsWith("run ")
            || l.contains("list files")  || l.contains("show files")
            || l.contains("what files")  || l.contains("files in")
            || l.contains("delete file") || l.contains("screenshot")
            || l.contains("take a screenshot") || l.contains("read file")
            || l.contains("create file") || l.contains("what's on my desktop")
            || l.contains("whats on my desktop") || l.contains("show desktop files")
            || l.contains("go to ") || l.contains("navigate to ")
            || l.contains("type ") || l.contains("press enter")
            || l.contains("close tab") || l.contains("new tab");
    }

    @Override
    public String execute(String input) {
        String l = input.toLowerCase().trim();

        // SCREENSHOT
        if (l.contains("screenshot") || l.contains("take a screenshot")) {
            return takeScreenshot();
        }

        // LIST FILES
        if (l.contains("list files") || l.contains("show files") || l.contains("what files")
                || l.contains("what's on my desktop") || l.contains("whats on my desktop")
                || l.contains("show desktop files")) {
            String target = extractPath(input);
            return listFiles(target != null ? target : System.getProperty("user.home") + "\\Desktop");
        }

        if (l.contains("files in")) {
            int idx = l.indexOf("files in") + 8;
            return listFiles(input.substring(idx).trim());
        }

        // DELETE FILE
        if (l.contains("delete file") || l.contains("delete the file")) {
            String path = extractPath(input);
            if (path == null) return "Specify a file path to delete.";
            File f = new File(path);
            if (!f.exists()) return "File not found: " + path;
            return f.delete() ? "Deleted: " + path : "Could not delete: " + path;
        }

        // READ FILE
        if (l.contains("read file")) {
            String path = extractPath(input);
            if (path == null) return "Specify a file path to read.";
            try {
                String content = new String(Files.readAllBytes(Paths.get(path)));
                if (content.length() > 800) content = content.substring(0, 800) + "\n...(truncated)";
                return content;
            } catch (Exception e) {
                return "Could not read: " + e.getMessage();
            }
        }

        // CREATE FILE
        if (l.contains("create file")) {
            String path = extractPath(input);
            if (path == null) return "Specify a file path to create.";
            try {
                Files.createFile(Paths.get(path));
                return "Created: " + path;
            } catch (Exception e) {
                return "Could not create: " + e.getMessage();
            }
        }

        // TYPE TEXT
        if (l.startsWith("type ")) {
            String text = input.substring(5).trim();
            typeText(text);
            return "Typed: " + text;
        }

        // PRESS ENTER
        if (l.contains("press enter")) {
            typeText("\n");
            return "Pressed Enter.";
        }

        // OPEN URL IN BROWSER
        if (l.contains("go to ") || l.contains("navigate to ") || l.contains("open http")) {
            String url = extractURL(input);
            if (url != null) return openURL(url);
        }

        // NEW TAB
        if (l.contains("new tab")) {
            try {
                Robot robot = new Robot();
                robot.keyPress(java.awt.event.KeyEvent.VK_CONTROL);
                robot.keyPress(java.awt.event.KeyEvent.VK_T);
                robot.keyRelease(java.awt.event.KeyEvent.VK_T);
                robot.keyRelease(java.awt.event.KeyEvent.VK_CONTROL);
                return "Opened new tab.";
            } catch (Exception e) {
                return "Could not open new tab: " + e.getMessage();
            }
        }

        // CLOSE TAB
        if (l.contains("close tab")) {
            try {
                Robot robot = new Robot();
                robot.keyPress(java.awt.event.KeyEvent.VK_CONTROL);
                robot.keyPress(java.awt.event.KeyEvent.VK_W);
                robot.keyRelease(java.awt.event.KeyEvent.VK_W);
                robot.keyRelease(java.awt.event.KeyEvent.VK_CONTROL);
                return "Closed tab.";
            } catch (Exception e) {
                return "Could not close tab: " + e.getMessage();
            }
        }

        // OPEN APP
        String appName = extractAppName(input);
        if (appName == null || appName.isEmpty()) return "Specify an app to open.";
        return openApp(appName);
    }

    public String openURL(String url) {
        if (!url.startsWith("http")) url = "https://" + url;
        try {
            Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", url});
            return "Opening " + url;
        } catch (Exception e) {
            return "Could not open URL: " + e.getMessage();
        }
    }

    private void typeText(String text) {
        try {
            // Use PowerShell to type text
            String script = "$wsh = New-Object -ComObject WScript.Shell; $wsh.SendKeys('" + text.replace("'", "''") + "')";
            new ProcessBuilder("powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command", script).start();
            Thread.sleep(300);
        } catch (Exception e) {
            System.out.println("Type error: " + e.getMessage());
        }
    }

    private String takeScreenshot() {
        try {
            String desktop = System.getProperty("user.home") + "\\Desktop";
            File dir = new File(desktop + "\\JarvisScreenshots");
            if (!dir.exists()) dir.mkdirs();
            String filename = "screenshot_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()) + ".png";
            File outFile = new File(dir, filename);
            Robot robot = new Robot();
            Rectangle screen = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage image = robot.createScreenCapture(screen);
            ImageIO.write(image, "png", outFile);
            Runtime.getRuntime().exec("explorer.exe " + dir.getAbsolutePath());
            return "Screenshot saved to Desktop\\JarvisScreenshots\\" + filename + "\nOpening folder now.";
        } catch (Exception e) {
            return "Screenshot failed: " + e.getMessage();
        }
    }

    private String listFiles(String dirPath) {
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) return "Directory not found: " + dirPath;
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) return "No files in: " + dirPath;
        StringBuilder sb = new StringBuilder("Files in " + dirPath + ":\n");
        int count = 0;
        for (File f : files) {
            if (count++ > 30) { sb.append("...and more."); break; }
            sb.append(f.isDirectory() ? "[DIR] " : "      ").append(f.getName()).append("\n");
        }
        return sb.toString().trim();
    }

    public String openApp(String app) {
        Map<String, String> quick = new HashMap<>();
        quick.put("chrome",        "chrome");
        quick.put("google chrome", "chrome");
        quick.put("edge",          "msedge");
        quick.put("microsoft edge","msedge");
        quick.put("firefox",       "firefox");
        quick.put("notepad",       "notepad");
        quick.put("calculator",    "calc");
        quick.put("calc",          "calc");
        quick.put("paint",         "mspaint");
        quick.put("explorer",      "explorer");
        quick.put("file explorer", "explorer");
        quick.put("cmd",           "cmd");
        quick.put("command prompt","cmd");
        quick.put("terminal",      "cmd");
        quick.put("task manager",  "taskmgr");
        quick.put("discord",       "discord");
        quick.put("spotify",       "spotify");
        quick.put("steam",         "steam");
        quick.put("vscode",        "code");
        quick.put("visual studio code","code");
        quick.put("word",          "winword");
        quick.put("excel",         "excel");
        quick.put("powerpoint",    "powerpnt");
        quick.put("vlc",           "vlc");
        quick.put("zoom",          "zoom");
        quick.put("settings",      "ms-settings:");
        quick.put("control panel", "control");
        quick.put("obs",           "obs64");
        quick.put("photoshop",     "photoshop");

        String key = app.toLowerCase().trim();
        if (quick.containsKey(key)) {
            try {
                Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", quick.get(key)});
                return "Opening " + capitalize(app) + ".";
            } catch (Exception e) {
                return "Failed to open " + app + ".";
            }
        }

        // Search program files
        String[] searchDirs = {
            "C:\\Program Files", "C:\\Program Files (x86)",
            System.getProperty("user.home") + "\\AppData\\Local",
            System.getProperty("user.home") + "\\AppData\\Roaming"
        };
        for (String dir : searchDirs) {
            String found = searchForExe(dir, key, 3);
            if (found != null) {
                try {
                    Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "", found});
                    return "Opening " + capitalize(app) + ".";
                } catch (Exception ignored) {}
            }
        }

        // PowerShell fallback
        try {
            new ProcessBuilder("powershell", "-NoProfile", "-WindowStyle", "Hidden",
                "-Command", "Start-Process '" + app + "'").start();
            return "Attempting to open " + capitalize(app) + ".";
        } catch (Exception e) {
            return "Could not open " + app + ". Make sure it's installed.";
        }
    }

    private String searchForExe(String dir, String appName, int depth) {
        if (depth <= 0) return null;
        File folder = new File(dir);
        if (!folder.exists() || !folder.isDirectory()) return null;
        File[] files = folder.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase().contains(appName) && f.getName().endsWith(".exe"))
                return f.getAbsolutePath();
        }
        for (File f : files) {
            if (f.isDirectory()) {
                String r = searchForExe(f.getAbsolutePath(), appName, depth - 1);
                if (r != null) return r;
            }
        }
        return null;
    }

    private String extractAppName(String input) {
        String l = input.toLowerCase().trim();
        for (String p : new String[]{"open ", "launch ", "start ", "run "}) {
            if (l.startsWith(p)) return input.substring(p.length()).trim();
        }
        return input.trim();
    }

    private String extractURL(String input) {
        String l = input.toLowerCase();
        for (String prefix : new String[]{"go to ", "navigate to ", "open "}) {
            int idx = l.indexOf(prefix);
            if (idx != -1) {
                String after = input.substring(idx + prefix.length()).trim();
                if (after.contains(".") || after.startsWith("http")) return after;
            }
        }
        return null;
    }

    private String extractPath(String input) {
        for (String drive : new String[]{"C:\\", "c:\\", "D:\\", "d:\\"}) {
            int idx = input.indexOf(drive);
            if (idx >= 0) return input.substring(idx).trim();
        }
        return null;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}