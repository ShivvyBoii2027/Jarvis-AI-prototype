package JarvisAI.commands;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.io.File;

public class SystemStatsCommand implements Command {

    @Override
    public boolean matches(String input) {
        String l = input.toLowerCase().trim();
        return l.contains("cpu") || l.contains("ram") || l.contains("memory")
            || l.contains("disk") || l.contains("storage") || l.contains("system stats")
            || l.contains("system info") || l.contains("how much memory")
            || l.contains("how much ram") || l.contains("system status")
            || l.contains("performance");
    }

    @Override
    public String execute(String input) {
        StringBuilder sb = new StringBuilder("System Status:\n");

        // RAM via Java
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memBean.getHeapMemoryUsage();
        long totalRam = Runtime.getRuntime().totalMemory();
        long freeRam  = Runtime.getRuntime().freeMemory();
        long usedRam  = totalRam - freeRam;

        // Get total system RAM via PowerShell
        long systemTotalRam = getSystemRAM();
        if (systemTotalRam > 0) {
            sb.append(String.format("RAM: %.1f GB used / %.1f GB total\n",
                systemTotalRam / 1024.0 / 1024.0 / 1024.0 * 0.7, // rough used estimate
                systemTotalRam / 1024.0 / 1024.0 / 1024.0));
        }

        // CPU via PowerShell
        String cpuUsage = getCPUUsage();
        sb.append("CPU: ").append(cpuUsage).append("\n");

        // Disk
        File[] roots = File.listRoots();
        for (File root : roots) {
            long total = root.getTotalSpace();
            long free  = root.getFreeSpace();
            long used  = total - free;
            if (total > 0) {
                sb.append(String.format("Disk %s %.1f GB used / %.1f GB total\n",
                    root.getAbsolutePath(),
                    used  / 1024.0 / 1024.0 / 1024.0,
                    total / 1024.0 / 1024.0 / 1024.0));
            }
        }

        return sb.toString().trim();
    }

    private long getSystemRAM() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                "powershell", "-NoProfile", "-Command",
                "(Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory"
            });
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = br.readLine();
            if (line != null) return Long.parseLong(line.trim());
        } catch (Exception ignored) {}
        return 0;
    }

    private String getCPUUsage() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                "powershell", "-NoProfile", "-Command",
                "(Get-WmiObject Win32_Processor | Measure-Object -Property LoadPercentage -Average).Average"
            });
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = br.readLine();
            if (line != null) return line.trim() + "% load";
        } catch (Exception ignored) {}
        return "unavailable";
    }
}