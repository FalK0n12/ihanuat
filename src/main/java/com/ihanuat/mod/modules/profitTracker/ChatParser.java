package com.ihanuat.mod.modules.profitTracker;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroState;
import com.ihanuat.mod.MacroStateManager;
import com.ihanuat.mod.util.ClientUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses chat messages for drop notifications, pest rewards, bazaar buys,
 * visitor rewards, and spray usage -- routing results to ProfitManager.
 */
public class ChatParser {

    private static final Pattern PEST_PATTERN = Pattern.compile("received\\s+(\\d+)x\\s+(.+?)\\s+for\\s+killing",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RARE_DROP_PATTERN = Pattern.compile(
            "(?:UNCOMMON|RARE|CRAZY RARE|PRAY TO RNGESUS) DROP!\\s+(?:You dropped\\s+)?(?:an?\\s+)?(?:(\\d+)x\\s+)?(.+?)(?=\\s*(?:\u00a7[0-9a-fk-or])*\\s*[\\(!]|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PET_DROP_PATTERN = Pattern.compile(
            "PET DROP!\\s+.*?\u00a7([0-9a-f])(?:\u00a7[0-9a-fk-or])*\\s*(?:(?:COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC)\\s+(?:\u00a7[0-9a-fk-or])*)?(.+?)(?=\\s*(?:\u00a7[0-9a-fk-or])*\\s*[\\(!]|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RARE_CROP_PATTERN = Pattern.compile(
            "RARE CROP!\\s+(.+?)(?=\\s*(?:\u00a7[0-9a-fk-or])*\\s*[\\(!]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern OVERFLOW_DROP_PATTERN = Pattern.compile(
            "OVERFLOW!\\s+.*?\\s+has\\s+just\\s+dropped\\s+(?:an?\\s+)?(?:(\\d+)x\\s+)?(.+?)(?=\\s*\\(!|!|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PEST_SHARD_PATTERN = Pattern.compile(
            "charmed\\s+a\\s+Pest\\s+and\\s+captured\\s+(?:its\\s+Shard|(\\d+)\\s+Shards)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BAZAAR_BUY_PATTERN = Pattern.compile(
            "\\[Bazaar\\] Bought (\\d+)x (.+?) for [\\d,]+ coins!",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SPRAY_PATTERN = Pattern.compile(
            "SPRAYONATOR! You sprayed Plot - \\d+ with (.+?)(?:!|$)",
            Pattern.CASE_INSENSITIVE);

    private static long lastBazaarSprayBuyTime = 0;
    private static boolean isTrackingVisitorRewards = false;
    private static boolean copperSeenInRewards = false;

    public static void handleChatMessage(Component component) {
        String text = toLegacyText(component);
        // PET DROP needs raw text to detect color-coded rarity
        Matcher petMatcher = PET_DROP_PATTERN.matcher(text);
        if (petMatcher.find()) {
            String colorCode = petMatcher.group(1).toLowerCase(); // 5 = Epic, 6 = Legendary, 9 = Rare
            String petName = petMatcher.group(2).trim();
            String finalName = petName;

            if (petName.equalsIgnoreCase("Slug")) {
                if (colorCode.equals("5") || colorCode.equals("d")) {
                    finalName = "Epic Slug";
                } else if (colorCode.equals("6")) {
                    finalName = "Legendary Slug";
                }
            } else if (petName.equalsIgnoreCase("Rat")) {
                finalName = "Rat";
            }
            ProfitManager.addDrop(finalName, 1);
            return;
        }

        Matcher cropMatcher = RARE_CROP_PATTERN.matcher(text);
        if (cropMatcher.find()) {
            ProfitManager.addDrop(cropMatcher.group(1).trim(), 1);
            return;
        }

        // Plain text processing for standard drops
        String plainText = ClientUtils.stripColor(text).trim();

        Matcher overflowMatcher = OVERFLOW_DROP_PATTERN.matcher(plainText);
        if (overflowMatcher.find()) {
            try {
                String countStr = overflowMatcher.group(1);
                int count = (countStr != null) ? Integer.parseInt(countStr) : 1;
                String itemName = overflowMatcher.group(2).trim();
                ProfitManager.addDrop(itemName, count);
                return;
            } catch (Exception ignored) {
            }
        }

        Matcher pestMatcher = PEST_PATTERN.matcher(plainText);
        if (pestMatcher.find()) {
            try {
                int count = Integer.parseInt(pestMatcher.group(1));
                String itemName = pestMatcher.group(2).trim();
                ProfitManager.addDrop(itemName, count);
                return;
            } catch (Exception ignored) {
            }
        }

        Matcher rareMatcher = RARE_DROP_PATTERN.matcher(plainText);
        if (rareMatcher.find()) {
            try {
                String countStr = rareMatcher.group(1);
                int count = (countStr != null) ? Integer.parseInt(countStr) : 1;
                String itemName = rareMatcher.group(2).trim();
                ProfitManager.addDrop(itemName, count);
            } catch (Exception ignored) {
            }
        }

        Matcher shardMatcher = PEST_SHARD_PATTERN.matcher(plainText);
        if (shardMatcher.find()) {
            try {
                String countStr = shardMatcher.group(1);
                int count = (countStr != null) ? Integer.parseInt(countStr) : 1;
                ProfitManager.addDrop("Pest Shard", count);
            } catch (Exception ignored) {
            }
        }

        Matcher bazaarMatcher = BAZAAR_BUY_PATTERN.matcher(plainText);
        if (bazaarMatcher.find()) {
            if (MacroStateManager.getCurrentState() == MacroState.State.VISITING) {
                ClientUtils.sendDebugMessage(Minecraft.getInstance(), "Bazaar buy ignored (Visiting state)");
                return;
            }
            try {
                int count = Integer.parseInt(bazaarMatcher.group(1));
                String itemName = bazaarMatcher.group(2).trim();
                ClientUtils.sendDebugMessage(Minecraft.getInstance(),
                        "Bazaar buy detected: " + count + "x " + itemName);
                ProfitManager.addDrop(itemName, -count);
                lastBazaarSprayBuyTime = System.currentTimeMillis();
            } catch (Exception ignored) {
            }
        }

        // ── Visitor Rewards Tracking ──
        if (plainText.equalsIgnoreCase("REWARDS")) {
            isTrackingVisitorRewards = true;
            copperSeenInRewards = false;
            return;
        }

        if (isTrackingVisitorRewards) {
            if (plainText.isEmpty()) {
                isTrackingVisitorRewards = false;
                return;
            }

            if (plainText.contains("Farming XP") || plainText.contains("Garden Experience")) {
                return;
            }

            if (plainText.contains("Copper")) {
                copperSeenInRewards = true;
            }

            if (copperSeenInRewards) {
                // Parse reward line
                Matcher m = Pattern.compile("^\\+?([\\d,.]+)[xX]?\\s+(.+)").matcher(plainText);
                if (m.find()) {
                    String item = m.group(2).trim();
                    String countStr = m.group(1).replace(",", "");
                    long count = 1;
                    try {
                        if (countStr.toLowerCase().endsWith("k")) {
                            count = (long) (Double.parseDouble(countStr.substring(0, countStr.length() - 1)) * 1000);
                        } else {
                            count = Long.parseLong(countStr);
                        }
                    } catch (Exception ignored) {
                    }
                    ProfitManager.addVisitorGain(item, count);
                } else {
                    ProfitManager.addVisitorGain(plainText, 1);
                }
            }
            return;
        }

        Matcher sprayMatcher = SPRAY_PATTERN.matcher(plainText);
        if (sprayMatcher.find()) {
            String baitName = sprayMatcher.group(1).trim();
            long now = System.currentTimeMillis();
            if (now - lastBazaarSprayBuyTime < 15000) {
                ClientUtils.sendDebugMessage(Minecraft.getInstance(),
                        "Sprayonator use ignored due to recent Bazaar buy.");
            } else {
                ClientUtils.sendDebugMessage(Minecraft.getInstance(), "Sprayonator use detected (" + baitName + ").");
                ProfitManager.addDrop(baitName, -1);
            }
        }
    }

    public static long getLastBazaarSprayBuyTime() {
        return lastBazaarSprayBuyTime;
    }

    public static void setLastBazaarSprayBuyTime(long time) {
        lastBazaarSprayBuyTime = time;
    }

    static String toLegacyText(Component component) {
        StringBuilder sb = new StringBuilder();
        component.visit((style, part) -> {
            net.minecraft.network.chat.TextColor color = style.getColor();
            if (color != null) {
                int rgb = color.getValue();
                String code = "f";
                if (rgb == 16755200)
                    code = "6"; // Gold
                else if (rgb == 11141290)
                    code = "5"; // Dark Purple
                else if (rgb == 5636095)
                    code = "b"; // Aqua
                else if (rgb == 16733695)
                    code = "d"; // Light Purple
                else if (rgb == 5592405)
                    code = "8"; // Dark Gray
                else if (rgb == 11184810)
                    code = "7"; // Gray
                else if (rgb == 5592575)
                    code = "9"; // Blue
                else if (rgb == 5635925)
                    code = "a"; // Green
                else if (rgb == 16711680)
                    code = "c"; // Red
                else if (rgb == 16777045)
                    code = "e"; // Yellow
                sb.append("\u00a7").append(code);
            }
            if (style.isBold())
                sb.append("\u00a7l");
            if (style.isItalic())
                sb.append("\u00a7o");
            sb.append(part);
            return Optional.empty();
        }, Style.EMPTY);
        return sb.toString();
    }
}
