package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroWorkerThread;
import com.ihanuat.mod.util.ClientUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class EquipmentManager {
    public static volatile boolean isSwappingEquipment = false;
    public static volatile int equipmentInteractionStage = 0;
    public static volatile long equipmentInteractionTime = 0;
    public static volatile boolean swappingToFarmingGear = false;
    public static volatile int equipmentTargetIndex = 0;
    public static volatile Boolean trackedIsPestGear = null;
    public static volatile boolean equipmentGuiDetected = false;

    public static void resetState() {
        isSwappingEquipment = false;
        equipmentInteractionStage = 0;
        equipmentInteractionTime = 0;
        swappingToFarmingGear = false;
        equipmentTargetIndex = 0;
        trackedIsPestGear = null;
        equipmentGuiDetected = false;
    }

    public static void ensureEquipment(Minecraft client, boolean toFarming) {
        if (!MacroConfig.autoEquipment)
            return;

        if (Minecraft.getInstance().isSameThread()) {
            if (WardrobeManager.isSwappingWardrobe) {
                ClientUtils.sendDebugMessage(client, "§eEquipment swap deferred: Wardrobe busy.");
                return;
            }
        } else {
            try {
                int timeout = 0;
                boolean waited = false;
                while (WardrobeManager.isSwappingWardrobe && timeout < 100) {
                    Thread.sleep(50);
                    timeout++;
                    waited = true;
                }
                if (WardrobeManager.isSwappingWardrobe) {
                    ClientUtils.sendDebugMessage(client,
                            "§c[GearManager] Auto-Equipment aborted: Wardrobe swap timed out.");
                    return;
                }
                if (waited) {
                    ClientUtils.sendDebugMessage(client, "§eWardrobe swap done! Triggering equipment swap");
                }
            } catch (InterruptedException ignored) {
            }
        }

        swappingToFarmingGear = toFarming;
        equipmentGuiDetected = false;
        equipmentInteractionTime = 0;
        isSwappingEquipment = true;
        equipmentInteractionStage = 0;
        equipmentTargetIndex = 0;
        ClientUtils.sendCommand(client, "/equipment");
    }

    public static void handleEquipmentMenu(Minecraft client, AbstractContainerScreen<?> screen) {
        if (!isSwappingEquipment)
            return;

        if (WardrobeManager.isSwappingWardrobe) {
            isSwappingEquipment = false;
            ClientUtils.sendDebugMessage(client, "§eAborting equipment menu for wardrobe priority.");
            return;
        }

        long now = System.currentTimeMillis();
        if (now - equipmentInteractionTime < MacroConfig.getRandomizedDelay(MacroConfig.equipmentSwapDelay))
            return;

        String title = screen.getTitle().getString().toLowerCase();
        if (!title.contains("equipment"))
            return;

        int[] guiSlots = { 10, 19, 28, 37 };

        boolean foundValidSlot = false;
        for (int slotIdx : guiSlots) {
            if (slotIdx < screen.getMenu().slots.size()) {
                Slot slot = screen.getMenu().slots.get(slotIdx);
                if (slot != null && slot.hasItem()) {
                    ItemStack stack = slot.getItem();
                    if (!stack.isEmpty() && !stack.getItem().toString().toLowerCase().contains("gray_dye")
                            && !stack.getHoverName().getString().toLowerCase().contains("gray dye")) {
                        foundValidSlot = true;
                        break;
                    }
                }
            }
        }

        if (!foundValidSlot) {
            ClientUtils.sendDebugMessage(client,
                    "Equipment GUI open but data not loaded yet (no valid equipment slots detected)");
            return;
        }

        if (!equipmentGuiDetected) {
            equipmentGuiDetected = true;
            ClientUtils.sendDebugMessage(client, "Equipment GUI detected AND VALIDATED as functional");
            equipmentInteractionTime = System.currentTimeMillis();
        }

        String[] keywords = { "necklace", "cloak|vest|cape", "belt", "gloves|bracelet|gauntlet" };

        int totalSlots = screen.getMenu().slots.size();
        int playerInvStart = totalSlots - 36;
        ItemStack carried = client.player.containerMenu.getCarried();

        if (equipmentTargetIndex >= guiSlots.length) {
            boolean targetRemaining = false;
            for (int i = playerInvStart; i < totalSlots; i++) {
                if (i >= screen.getMenu().slots.size())
                    break;
                Slot invSlot = screen.getMenu().slots.get(i);
                if (invSlot != null && invSlot.hasItem()) {
                    String invItemName = invSlot.getItem().getHoverName().getString().toLowerCase();
                    boolean invIsFarming = invItemName.contains("lotus") || invItemName.contains("blossom")
                            || invItemName.contains("zorro");
                    boolean invIsPest = invItemName.contains("pest");
                    boolean matchesTarget = swappingToFarmingGear ? invIsFarming : invIsPest;

                    if (matchesTarget) {
                        boolean isEquipmentType = false;
                        for (String kwGroup : keywords) {
                            for (String kw : kwGroup.split("\\|")) {
                                if (invItemName.contains(kw)) {
                                    isEquipmentType = true;
                                    break;
                                }
                            }
                            if (isEquipmentType)
                                break;
                        }

                        if (isEquipmentType) {
                            int slotGroup = -1;
                            for (int k = 0; k < keywords.length; k++) {
                                for (String kw : keywords[k].split("\\|")) {
                                    if (invItemName.contains(kw)) {
                                        slotGroup = k;
                                        break;
                                    }
                                }
                                if (slotGroup != -1)
                                    break;
                            }

                            if (slotGroup != -1) {
                                Slot targetSlot = screen.getMenu().getSlot(guiSlots[slotGroup]);
                                if (targetSlot != null && targetSlot.hasItem()) {
                                    String targetName = targetSlot.getItem().getHoverName().getString().toLowerCase();
                                    boolean targetIsFarming = targetName.contains("lotus")
                                            || targetName.contains("blossom")
                                            || targetName.contains("zorro");
                                    boolean targetIsPest = targetName.contains("pest");
                                    boolean targetMatches = swappingToFarmingGear ? targetIsFarming : targetIsPest;
                                    if (targetMatches)
                                        continue;
                                }
                            }

                            targetRemaining = true;
                            break;
                        }
                    }
                }
            }

            if (targetRemaining) {
                ClientUtils.sendDebugMessage(client,
                        "§eEquipment swap: Target items still in inventory after attempt, retrying sequence...");
                equipmentTargetIndex = 0;
                equipmentInteractionStage = 0;
                equipmentInteractionTime = now;
                return;
            }

            ClientUtils.sendDebugMessage(client, "§aEquipment swap successful!");
            trackedIsPestGear = !swappingToFarmingGear;
            isSwappingEquipment = false;
            int containerId = screen.getMenu().containerId;
            client.setScreen(null);
            WardrobeManager.wardrobeCleanupTicks = 10;
            equipmentInteractionStage = 0;
            MacroWorkerThread.getInstance().submit("Equipment-ClosePacket", () -> {
                MacroWorkerThread.sleep(100);
                client.execute(() -> {
                    if (client.player != null && client.getConnection() != null)
                        client.getConnection().send(new ServerboundContainerClosePacket(containerId));
                });
            });
            return;
        }

        if (equipmentInteractionStage == 0) {
            if (!carried.isEmpty()) {
                String carriedName = carried.getHoverName().getString().toLowerCase();
                boolean isFarming = carriedName.contains("lotus") || carriedName.contains("blossom")
                        || carriedName.contains("zorro");
                boolean isPest = carriedName.contains("pest");
                boolean matchesTarget = swappingToFarmingGear ? isFarming : isPest;

                if (matchesTarget) {
                    for (int i = 0; i < keywords.length; i++) {
                        for (String type : keywords[i].split("\\|")) {
                            if (carriedName.contains(type)) {
                                ClientUtils.sendDebugMessage(client, "§eEquipment swap: Item " + carriedName
                                        + " stuck in cursor, trying to equip to slot " + (i + 1));
                                equipmentTargetIndex = i;
                                equipmentInteractionStage = 1;
                                return;
                            }
                        }
                    }
                }

                ClientUtils.sendDebugMessage(client,
                        "§cEquipment swap: Waiting for cursor to clear (" + carriedName + ")");
                return;
            }

            Slot equipmentSlot = screen.getMenu().getSlot(guiSlots[equipmentTargetIndex]);
            if (equipmentSlot != null && equipmentSlot.hasItem()) {
                String itemName = equipmentSlot.getItem().getHoverName().getString().toLowerCase();
                boolean isFarming = itemName.contains("lotus") || itemName.contains("blossom")
                        || itemName.contains("zorro");
                boolean isPest = itemName.contains("pest");
                boolean matches = swappingToFarmingGear ? isFarming : isPest;

                if (matches) {
                    ClientUtils.sendDebugMessage(client,
                            "§7Slot " + (equipmentTargetIndex + 1) + " already has correct gear.");
                    equipmentTargetIndex++;
                    equipmentInteractionTime = now;
                    return;
                }
            }

            String targetTypePattern = keywords[equipmentTargetIndex];
            for (int i = playerInvStart; i < totalSlots; i++) {
                Slot invSlot = screen.getMenu().slots.get(i);
                if (invSlot.hasItem()) {
                    String invItemName = invSlot.getItem().getHoverName().getString().toLowerCase();
                    boolean invIsFarming = invItemName.contains("lotus") || invItemName.contains("blossom")
                            || invItemName.contains("zorro");
                    boolean invIsPest = invItemName.contains("pest");
                    boolean matchesTarget = swappingToFarmingGear ? invIsFarming : invIsPest;

                    if (matchesTarget) {
                        boolean typeMatch = false;
                        for (String type : targetTypePattern.split("\\|")) {
                            if (invItemName.contains(type)) {
                                typeMatch = true;
                                break;
                            }
                        }

                        if (typeMatch) {
                            ClientUtils.sendDebugMessage(client, "§bPicking up target item: " + invItemName);
                            client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, invSlot.index, 0,
                                    ClickType.PICKUP, client.player);
                            equipmentInteractionTime = now;
                            equipmentInteractionStage = 1;
                            return;
                        }
                    }
                }
            }

            equipmentTargetIndex++;
            equipmentInteractionTime = now;
            return;
        }

        if (equipmentInteractionStage == 1) {
            if (carried.isEmpty()) {
                ClientUtils.sendDebugMessage(client, "§cEquipment swap: Cursor empty in stage 1, resetting to search.");
                equipmentInteractionStage = 0;
                return;
            }
            int gearSlotIdx = guiSlots[equipmentTargetIndex];
            ClientUtils.sendDebugMessage(client,
                    "§bEquipping " + carried.getHoverName().getString() + " into slot " + (equipmentTargetIndex + 1));
            client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, gearSlotIdx, 0,
                    ClickType.PICKUP, client.player);
            equipmentInteractionTime = now;
            equipmentInteractionStage = 0;
            equipmentTargetIndex++;
            return;
        }
    }
}
