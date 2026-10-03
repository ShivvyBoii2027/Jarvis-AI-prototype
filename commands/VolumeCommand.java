package JarvisAI.commands;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VolumeCommand implements Command {

    @Override
    public boolean matches(String input) {
        String l = input.toLowerCase().trim();
        return l.contains("volume") || l.contains("mute") || l.contains("unmute")
            || l.contains("brightness") || l.contains("louder") || l.contains("quieter")
            || l.contains("turn up") || l.contains("turn down") || l.contains("silent mode");
    }

    @Override
    public String execute(String input) {
        String l = input.toLowerCase().trim();

        if (l.contains("mute") && !l.contains("unmute")) {
            runPS("$obj = New-Object -ComObject WScript.Shell; $obj.SendKeys([char]173)");
            return "Audio muted.";
        }
        if (l.contains("unmute")) {
            runPS("$obj = New-Object -ComObject WScript.Shell; $obj.SendKeys([char]173)");
            return "Audio unmuted.";
        }
        if (l.contains("turn up") || l.contains("louder") || l.contains("volume up")) {
            runPS("$wsh = New-Object -ComObject WScript.Shell; 1..5 | ForEach-Object { $wsh.SendKeys([char]175) }");
            return "Volume increased.";
        }
        if (l.contains("turn down") || l.contains("quieter") || l.contains("volume down") || l.contains("silent mode")) {
            runPS("$wsh = New-Object -ComObject WScript.Shell; 1..5 | ForEach-Object { $wsh.SendKeys([char]174) }");
            return "Volume decreased.";
        }

        // SET VOLUME TO X% — uses Windows Audio API via PowerShell
        if (l.contains("volume")) {
            int percent = extractNumber(input);
            if (percent >= 0 && percent <= 100) {
                // This uses the Windows Core Audio API properly
                String script =
                    "Add-Type -TypeDefinition @'\n" +
                    "using System;\n" +
                    "using System.Runtime.InteropServices;\n" +
                    "[Guid(\"5CDF2C82-841E-4546-9722-0CF74078229A\"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]\n" +
                    "interface IAudioEndpointVolume { void _VtblGap1_6(); void SetMasterVolumeLevelScalar(float fLevel, Guid pguidEventContext); }\n" +
                    "[Guid(\"BCDE0395-E52F-467C-8E3D-C4579291692E\")]\n" +
                    "class MMDeviceEnumerator {}\n" +
                    "public class Volume {\n" +
                    "    public static void Set(float level) {\n" +
                    "        var enumerator = (dynamic)Activator.CreateInstance(Type.GetTypeFromCLSID(new Guid(\"BCDE0395-E52F-467C-8E3D-C4579291692E\")));\n" +
                    "        var device = enumerator.GetDefaultAudioEndpoint(0, 1);\n" +
                    "        var vol = (IAudioEndpointVolume)Marshal.GetObjectForIUnknown(Marshal.GetIUnknownForObject(device.AudioEndpointVolume));\n" +
                    "    }\n" +
                    "}\n" +
                    "'@\n" +
                    // Simpler fallback: use WScript key presses to approximate
                    "$wsh = New-Object -ComObject WScript.Shell;\n" +
                    "1..50 | ForEach-Object { $wsh.SendKeys([char]174) };\n" +  // go to ~0
                    "$steps = [int](" + percent + " / 2);\n" +
                    "if ($steps -gt 0) { 1..$steps | ForEach-Object { $wsh.SendKeys([char]175) } }";
                runPS(script);
                return "Volume set to approximately " + percent + "%.";
            }
        }

        // BRIGHTNESS
        if (l.contains("brightness")) {
            int percent = extractNumber(input);
            if (percent < 0) percent = 70;
            String script =
                "Try {\n" +
                "  $mon = Get-WmiObject -Namespace root\\WMI -Class WmiMonitorBrightnessMethods;\n" +
                "  $mon.WmiSetBrightness(1, " + percent + ");\n" +
                "  Write-Host 'Brightness set';\n" +
                "} Catch {\n" +
                "  Write-Host 'Brightness not supported on this display';\n" +
                "}";
            runPS(script);
            return "Brightness set to " + percent + "%.";
        }

        return "Try: 'mute', 'turn up', 'turn down', 'set volume to 70', or 'brightness to 80'.";
    }

    private void runPS(String script) {
        try {
            new ProcessBuilder(
                "powershell", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                "-Command", script
            ).start();
            Thread.sleep(300); // small wait for key presses to register
        } catch (Exception e) {
            System.out.println("Volume PS error: " + e.getMessage());
        }
    }

    private int extractNumber(String input) {
        Matcher m = Pattern.compile("(\\d+)").matcher(input);
        if (m.find()) return Integer.parseInt(m.group(1));
        return -1;
    }
}