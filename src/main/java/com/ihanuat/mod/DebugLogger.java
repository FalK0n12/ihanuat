package com.ihanuat.mod;

import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Writes debug messages to a timestamped log file inside the Minecraft
 * game directory (the same folder as saves/, mods/, etc.).
 *
 * <p>Activated by {@link MacroConfig#logDebugToFile}. A new file is created
 * each time the game launches (or the logger is first used), named:
 * {@code ihanuat_debug_YYYY-MM-DD_HH-mm-ss.log}
 *
 * <p>Usage: call {@link #log(String)} from {@code ClientUtils.sendDebugMessage}
 * whenever debug logging is enabled. Everything else is handled internally.
 */
public final class DebugLogger {

    // singleton

    private static DebugLogger instance;

    public static synchronized DebugLogger getInstance() {
        if (instance == null) {
            instance = new DebugLogger();
        }
        return instance;
    }

    // state 

    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter LINE_STAMP =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private BufferedWriter writer;
    private Path logFile;
    private boolean initialised = false;
    private boolean broken = false; // stop retrying after a hard failure


    /**
     * Write a single debug line to the log file.
     * Opens (creates) the file on the first call.
     * Does nothing if file logging is disabled or if a hard I/O error occurred.
     *
     * @param message raw message text (Minecraft colour codes are stripped)
     */
    public synchronized void log(String message) {
        if (!MacroConfig.logDebugToFile || broken) return;

        if (!initialised) {
            open();
            if (broken) return;
        }

        try {
            String stripped = message.replaceAll("(?i)\u00A7[0-9a-fk-or]", "");
            String line = "[" + LocalDateTime.now().format(LINE_STAMP) + "] " + stripped;
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            broken = true;
            System.err.println("[ihanuat] DebugLogger write failed: " + e.getMessage());
        }
    }

    /**
     * Flush and close the current log file.
     * Resets the logger so a fresh file is created on the next {@link #log} call.
     * Safe to call even if the logger was never opened.
     */
    public synchronized void close() {
        if (writer != null) {
            try {
                writer.flush();
                writer.close();
            } catch (IOException ignored) {
            } finally {
                writer = null;
            }
        }
        initialised = false;
        broken = false;
    }

    /** @return the path of the currently open log file, or {@code null} if not open. */
    public Path getLogFile() {
        return logFile;
    }

    private void open() {
        try {
            Path logDir = resolveLogDir();
            logDir.toFile().mkdirs();

            // ─ Rotate: keep only the 1 most recent existing log, delete the rest ──
            File[] existing = logDir.toFile().listFiles(
                    (dir, name) -> name.startsWith("ihanuat_debug_") && name.endsWith(".log"));
            if (existing != null && existing.length > 0) {
                // Sort oldestto newest by last-modified
                java.util.Arrays.sort(existing, java.util.Comparator.comparingLong(File::lastModified));
                // Delete all but the single newest (we are about to create a new one, so
                // after this session there will be exactly 2: the previous + this one)
                for (int i = 0; i < existing.length - 1; i++) {
                    existing[i].delete();
                }
            }

            String filename = "ihanuat_debug_" + LocalDateTime.now().format(FILE_STAMP) + ".log";
            logFile = logDir.resolve(filename);

            writer = new BufferedWriter(new FileWriter(logFile.toFile(), true));
            writer.write("=== Ihanuat Debug Log started " + LocalDateTime.now() + " ===");
            writer.newLine();
            writer.flush();
            initialised = true;

            Ihanuat.LOGGER.info("[ihanuat] Debug log file: {}", logFile.toAbsolutePath());
        } catch (IOException e) {
            broken = true;
            Ihanuat.LOGGER.error("[ihanuat] Could not open debug log file: {}", e.getMessage());
        }
    }

    /**
     * Resolves the Minecraft game directory using the Fabric loader.
     * This is the same folder that contains saves/, mods/, config/, etc.
     * e.g. C:\Users\radek\AppData\Roaming\.minecraft
     * yay :D
     */
    private static Path resolveLogDir() {
        return FabricLoader.getInstance().getGameDir();
    }
}
