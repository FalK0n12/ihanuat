package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.mixin.AccessorInventory;
import com.ihanuat.mod.util.ClientUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class RodManager {
    public static volatile boolean isHoldingRodUse = false;

    public static void resetState() {
        isHoldingRodUse = false;
    }

    public static void stopHoldingRodUse() {
        isHoldingRodUse = false;
    }

    public static void executeRodSequence(Minecraft client) {
        ClientUtils.sendDebugMessage(client, "executeRodSequence called");
        if (EquipmentManager.isSwappingEquipment) {
            ClientUtils.sendDebugMessage(client, "Waiting for equipment swap before rod sequence...");
            long waitStart = System.currentTimeMillis();
            try {
                while (EquipmentManager.isSwappingEquipment && System.currentTimeMillis() - waitStart < 5000) {
                    Thread.sleep(50);
                }
                if (EquipmentManager.isSwappingEquipment) {
                    ClientUtils.sendDebugMessage(client,
                            "\u00A7cRod sequence: Equipment swap timed out, proceeding anyway.");
                } else {
                    ClientUtils.sendDebugMessage(client, "Equipment swap done! Starting rod sequence.");
                }
            } catch (InterruptedException ignored) {
            }
        }

        client.player.displayClientMessage(Component.literal("\u00A7eExecuting Rod Swap sequence..."), true);

        int rodSlot = -1;
        for (int i = 0; i < 9; i++) {
            String rodItemName = client.player.getInventory().getItem(i).getHoverName().getString().toLowerCase();
            if (rodItemName.contains("rod")) {
                rodSlot = i;
                break;
            }
        }

        if (rodSlot == -1) {
            client.player.displayClientMessage(Component.literal("\u00A7cRod not found in hotbar!"), true);
            return;
        }

        final int finalRodSlot = rodSlot;
        try {
            client.execute(() -> ((AccessorInventory) client.player.getInventory()).setSelected(finalRodSlot));

            long swapWaitStart = System.currentTimeMillis();
            while (System.currentTimeMillis() - swapWaitStart < 1500) {
                if (((AccessorInventory) client.player.getInventory()).getSelected() == finalRodSlot) {
                    ItemStack current = client.player.getInventory().getItem(finalRodSlot);
                    if (current.getHoverName().getString().toLowerCase().contains("rod")) {
                        break;
                    }
                }
                Thread.sleep(10);
            }

            if (MacroConfig.rodSwapDelay > 0) {
                Thread.sleep(MacroConfig.rodSwapDelay);
            }

            isHoldingRodUse = true;
            Thread.sleep(MacroConfig.rodSwapDelay);
            isHoldingRodUse = false;
        } catch (InterruptedException e) {
            isHoldingRodUse = false;
            e.printStackTrace();
        }
    }
}
