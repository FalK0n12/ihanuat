package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.mixin.AccessorInventory;
import com.ihanuat.mod.util.ClientUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.item.ItemStack;

public class GearManager {
    public static void reset() {
        WardrobeManager.resetState();
        EquipmentManager.resetState();
        RodManager.resetState();
    }

    public static void triggerPrepSwap(Minecraft client) {
        RodManager.stopHoldingRodUse();
    }

    public static void triggerWardrobeSwap(Minecraft client, int slot) {
        WardrobeManager.triggerWardrobeSwap(client, slot);
    }

    public static void ensureWardrobeSlot(Minecraft client, int slot) {
        WardrobeManager.ensureWardrobeSlot(client, slot);
    }

    public static void handleWardrobeMenu(Minecraft client, AbstractContainerScreen<?> screen) {
        WardrobeManager.handleWardrobeMenu(client, screen);
    }

    public static void ensureEquipment(Minecraft client, boolean toFarming) {
        EquipmentManager.ensureEquipment(client, toFarming);
    }

    public static void handleEquipmentMenu(Minecraft client, AbstractContainerScreen<?> screen) {
        EquipmentManager.handleEquipmentMenu(client, screen);
    }

    public static void finalResume(Minecraft client) {
        if (PestManager.isCleaningInProgress)
            return;

        ClientUtils.waitForGearAndGui(client);
        swapToFarmingToolSync(client);

        if (PestManager.isCleaningInProgress)
            return;

        client.execute(() -> {
            if (PestManager.isCleaningInProgress)
                return;
            com.ihanuat.mod.MacroStateManager.setCurrentState(com.ihanuat.mod.MacroState.State.FARMING);
            ClientUtils.sendDebugMessage(client, "Finalizing gear swap. Restarting farming script...");
            com.ihanuat.mod.util.CommandUtils.startScript(client, MacroConfig.getFullRestartCommand(), 0);
        });
    }

    public static int findFarmingToolSlot(Minecraft client) {
        if (client.player == null)
            return -1;
        String[] keywords = { "hoe", "dicer", "knife", "chopper", "cutter" };
        for (int i = 0; i < 9; i++) {
            ItemStack stack = client.player.getInventory().getItem(i);
            String name = stack.getHoverName().getString().toLowerCase();
            for (String kw : keywords) {
                if (name.contains(kw)) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static void swapToFarmingTool(Minecraft client) {
        int slot = findFarmingToolSlot(client);
        if (slot != -1) {
            ItemStack stack = client.player.getInventory().getItem(slot);
            String name = stack.getHoverName().getString();
            ((AccessorInventory) client.player.getInventory()).setSelected(slot);
            client.player.displayClientMessage(Component.literal("\u00A7aEquipped Farming Tool: " + name), true);
        }
    }

    public static void swapToFarmingToolSync(Minecraft client) {
        int slot = findFarmingToolSlot(client);
        if (slot != -1) {
            client.execute(() -> {
                ((AccessorInventory) client.player.getInventory()).setSelected(slot);
                ItemStack stack = client.player.getInventory().getItem(slot);
                client.player.displayClientMessage(
                        Component.literal("\u00A7aEquipped Farming Tool: " + stack.getHoverName().getString()), true);
            });
            try {
                long start = System.currentTimeMillis();
                while (System.currentTimeMillis() - start < 1500) {
                    if (((AccessorInventory) client.player.getInventory()).getSelected() == slot)
                        break;
                    Thread.sleep(20);
                }
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
        }
    }

    public static void executeRodSequence(Minecraft client) {
        RodManager.executeRodSequence(client);
    }

    public static void cleanupTick(Minecraft client) {
        if (WardrobeManager.wardrobeCleanupTicks > 0) {
            WardrobeManager.wardrobeCleanupTicks--;
            if (client.player != null) {
                try {
                    if (client.player.containerMenu != null) {
                        client.player.containerMenu.setCarried(ItemStack.EMPTY);
                        client.player.containerMenu.broadcastChanges();
                    }
                    if (client.player.inventoryMenu != null) {
                        client.player.inventoryMenu.setCarried(ItemStack.EMPTY);
                        client.player.inventoryMenu.broadcastChanges();
                    }
                    client.player.connection.send(new ServerboundContainerClosePacket(0));
                } catch (Exception ignored) {
                }
            }
            if (client.mouseHandler != null) {
                client.mouseHandler.releaseMouse();
            }
        }
    }
}
