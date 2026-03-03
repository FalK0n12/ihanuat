package com.ihanuat.mod.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

public class CommandUtils {
    private static final Queue<String> chatMessageQueue = new LinkedList<>();
    private static final long MESSAGE_TIMEOUT_MS = 10000; // 10 second timeout
    private static final Set<String> awaitingMessages = new HashSet<>();

    /**
     * Register a chat message to the queue.
     * This is called from IhanuatClient when a chat message is received.
     */
    public static void onChatMessage(String message) {
        synchronized (chatMessageQueue) {
            chatMessageQueue.add(message);
            chatMessageQueue.notifyAll();
        }
    }

    /**
     * Wait for a chat message containing the specified substring.
     * Blocks until the message is found or timeout is reached.
     *
     * @param client           The Minecraft instance
     * @param messageSubstring The substring to search for in chat messages
     * @return true if the message was found, false if timeout occurred
     */
    public static boolean waitForChatMessage(Minecraft client, String messageSubstring) {
        long startTime = System.currentTimeMillis();

        synchronized (chatMessageQueue) {
            while (System.currentTimeMillis() - startTime < MESSAGE_TIMEOUT_MS) {
                // Check existing messages
                for (String msg : chatMessageQueue) {
                    if (msg.contains(messageSubstring)) {
                        chatMessageQueue.clear(); // Clear processed messages
                        return true;
                    }
                }

                try {
                    // Wait for new messages
                    long remainingTime = MESSAGE_TIMEOUT_MS - (System.currentTimeMillis() - startTime);
                    if (remainingTime > 0) {
                        chatMessageQueue.wait(Math.min(remainingTime, 100));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        return false;
    }

    /**
     * Non-blocking method to check if a message has been received.
     * Returns immediately with true if the message was found, false otherwise.
     *
     * @param messageSubstring The substring to search for
     * @return true if the message has been received, false otherwise
     */
    public static boolean hasReceivedMessage(String messageSubstring) {
        synchronized (chatMessageQueue) {
            for (String msg : chatMessageQueue) {
                if (msg.contains(messageSubstring)) {
                    chatMessageQueue.clear(); // Clear processed messages
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if a message receipt is being awaited (non-blocking check for async
     * operations).
     *
     * @param messageSubstring The substring to check
     * @return true if the message has been received, false otherwise
     */
    public static boolean isMessageReceived(String messageSubstring) {
        return hasReceivedMessage(messageSubstring);
    }

    /**
     * Execute /setspawn and wait for the confirmation message.
     * Blocks until the spawn location is confirmed or timeout occurs.
     *
     * @param client The Minecraft instance
     * @return true if spawn was set successfully, false if timeout occurred
     */
    public static boolean setSpawn(Minecraft client) {
        ClientUtils.sendCommand(client, "/setspawn");
        boolean success = waitForChatMessage(client, "Your spawn location has been set!");

        if (success && client.player != null) {
            client.player.displayClientMessage(
                    Component.literal("§9[Debug] Spawn set has been detected"),
                    false);
        }

        return success;
    }

    /**
     * Initiate /setspawn command (non-blocking).
     * Check result with hasSpawnBeenSet().
     *
     * @param client The Minecraft instance
     */
    public static void initiateSetSpawn(Minecraft client) {
        ClientUtils.sendCommand(client, "/setspawn");
    }

    /**
     * Check if /setspawn has been confirmed (non-blocking).
     *
     * @return true if the spawn confirmation message was received
     */
    public static boolean hasSpawnBeenSet() {
        return hasReceivedMessage("Your spawn location has been set!");
    }

    /**
     * Execute /warp garden and wait for the confirmation message.
     * Blocks until the warp is confirmed or timeout occurs.
     *
     * @param client The Minecraft instance
     * @return true if warp was successful, false if timeout occurred
     */
    public static boolean warpGarden(Minecraft client) {
        ClientUtils.sendCommand(client, "/warp garden");
        boolean success = waitForChatMessage(client, "Warping...");

        if (success && client.player != null) {
            client.player.displayClientMessage(
                    Component.literal("§9[Debug] /warp garden success"),
                    false);
        }

        return success;
    }

    /**
     * Initiate /warp garden command (non-blocking).
     * Check result with hasWarpedGarden().
     *
     * @param client The Minecraft instance
     */
    public static void initiateWarpGarden(Minecraft client) {
        ClientUtils.sendCommand(client, "/warp garden");
    }

    /**
     * Check if /warp garden has been confirmed (non-blocking).
     *
     * @return true if the warp confirmation message was received
     */
    public static boolean hasWarpedGarden() {
        return hasReceivedMessage("Warping...");
    }

    /**
     * Execute /plottp and wait for the confirmation message.
     * Blocks until the warp is confirmed or timeout occurs.
     *
     * @param client     The Minecraft instance
     * @param plotNumber The plot number to warp to
     * @return true if warp was successful, false if timeout occurred
     */
    public static boolean plotTp(Minecraft client, String plotNumber) {
        ClientUtils.sendCommand(client, "/plottp " + plotNumber);
        boolean success = waitForChatMessage(client, "Teleported you to Plot");

        if (success && client.player != null) {
            client.player.displayClientMessage(
                    Component.literal("§9[Debug] plottp success"),
                    false);
        }

        return success;
    }

    /**
     * Initiate /plottp command (non-blocking).
     * Check result with hasPlotTp().
     *
     * @param client     The Minecraft instance
     * @param plotNumber The plot number to warp to
     */
    public static void initiatePlotTp(Minecraft client, String plotNumber) {
        ClientUtils.sendCommand(client, "/plottp " + plotNumber);
    }

    /**
     * Check if /plottp has been confirmed (non-blocking).
     *
     * @return true if the warp confirmation message was received
     */
    public static boolean hasPlotTp() {
        return hasReceivedMessage("Teleported you to Plot");
    }

    /**
     * Stop the current ez script and wait for a specified delay.
     * 
     * @param client  The Minecraft instance
     * @param delayMs Delay in milliseconds
     */
    public static void stopScript(Minecraft client, long delayMs) {
        ClientUtils.sendCommand(client, ".ez-stopscript");
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Stop the current ez script and wait for a default 250ms delay.
     * 
     * @param client The Minecraft instance
     */
    public static void stopScript(Minecraft client) {
        stopScript(client, 250);
    }

    /**
     * Start an ez script and wait for a specified delay.
     * 
     * @param client        The Minecraft instance
     * @param scriptCommand The full command or script string
     * @param delayMs       Delay in milliseconds
     */
    public static void startScript(Minecraft client, String scriptCommand, long delayMs) {
        ClientUtils.sendCommand(client, scriptCommand);
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Start an ez script and wait for a default 250ms delay.
     * 
     * @param client        The Minecraft instance
     * @param scriptCommand The full command or script string
     */
    public static void startScript(Minecraft client, String scriptCommand) {
        startScript(client, scriptCommand, 250);
    }

    /**
     * Clear any pending chat messages from the queue.
     */
    public static void clearMessageQueue() {
        synchronized (chatMessageQueue) {
            chatMessageQueue.clear();
        }
    }
}
