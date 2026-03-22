package com.ihanuat.mod.modules.profitTracker;

import com.ihanuat.mod.MacroConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles bazaar and auction house price fetching, price resolution,
 * and Coflnet API item ID lookups.
 */
public class BazaarService {

    private static final Map<String, Double> bazaarPrices = new LinkedHashMap<>();
    private static final Map<String, Long> petLvl1Prices = new java.util.HashMap<>();
    private static final Map<String, Long> petMaxLvlPrices = new java.util.HashMap<>();
    private static final Map<String, String> idByNameCache = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static long lastBazaarFetchTime = 0;

    public static long getLastBazaarFetchTime() {
        return lastBazaarFetchTime;
    }

    /**
     * Resolves the coin value of a single unit of the given item.
     * Priority: special cases -> TRACKED_ITEMS hardcoded -> live bazaarPrices.
     */
    public static double getItemPrice(String itemName) {
        if (itemName.startsWith("[Visitor] ")) {
            String realName = itemName.substring(10);
            if ("Visitor Cost".equals(realName))
                return 1.0;
            if ("Copper".equals(realName)) {
                double greenThumbPrice = bazaarPrices.getOrDefault("ENCHANTMENT_GREEN_THUMB_1", 0.0);
                if (greenThumbPrice <= 0) {
                    greenThumbPrice = ItemConstants.TRACKED_ITEMS.getOrDefault("ENCHANTMENT_GREEN_THUMB_1", 0.0);
                }
                if (greenThumbPrice > 0) {
                    return greenThumbPrice / 1500.0;
                }
                return 0.0;
            }
            return getItemPrice(realName); // Recursive call for the actual item price
        }

        // Visitor cost: count IS the coin amount, so price = 1.0
        if ("[Spray] Sprayonator".equals(itemName) || "Purse".equals(itemName)) {
            return 1.0;
        }
        double price = ItemConstants.TRACKED_ITEMS.getOrDefault(itemName, 0.0);
        if (price == 0.0) {
            price = bazaarPrices.getOrDefault(itemName, 0.0);
        }
        return price;
    }

    public static void startStartupPriceFetch() {
        fetchBazaarPrices();
    }

    /**
     * Checks if a bazaar refresh is needed (every 1 hour) and triggers it.
     * Returns true if a fetch was initiated.
     */
    public static boolean checkAndRefresh() {
        long now = System.currentTimeMillis();
        if (now - lastBazaarFetchTime > 3600000L) {
            fetchBazaarPrices();
            return true;
        }
        return false;
    }

    /**
     * Rebuilds configured pet XP prices immediately from the current config.
     */
    public static synchronized void refreshConfiguredPetXpPrices() {
        updateConfiguredPetXpPrices();
        ProfitManager.markAllHudDirty();
    }

    public static double getConfiguredPetXpPrice(MacroConfig.PetInfo info) {
        long[] table = PetXpTracker.getXpTable(info.rarity, info.maxLevel);
        long totalXp = table[info.maxLevel];
        if (totalXp <= 0 || info.level1Price <= 0 || info.maxLevelPrice <= info.level1Price) {
            return 0.0;
        }
        return (double) (info.maxLevelPrice - info.level1Price) / totalXp;
    }

    /**
     * Sends the current pet XP price data to the player's chat.
     */
    public static void printPetXpPriceDebug(net.minecraft.client.Minecraft client) {
        if (client.player == null)
            return;
        if (MacroConfig.petXpTrackedPets == null || MacroConfig.petXpTrackedPets.isEmpty())
            return;

        client.player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("\u00a7b[Pet XP Tracker] \u00a7fCurrently tracking:"), false);

        for (String petConfig : MacroConfig.petXpTrackedPets) {
            MacroConfig.PetInfo info = new MacroConfig.PetInfo(petConfig);
            long lvl1 = info.level1Price;
            long lvlMax = info.maxLevelPrice;
            double pricePerXp = getConfiguredPetXpPrice(info);

            String lvl1Str = lvl1 > 0 ? String.format("%,d", lvl1) : "not set";
            String lvlMaxStr = lvlMax > 0 ? String.format("%,d", lvlMax) : "not set";
            String marginStr = pricePerXp > 0 ? String.format("%.3f", pricePerXp) : "not configured";

            client.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            " \u00a78> \u00a7e" + info.name + "\u00a7f: \u00a77L1: \u00a76" + lvl1Str + " \u00a77Max: \u00a76" + lvlMaxStr + " \u00a77-> \u00a7a"
                                    + marginStr + " \u00a77C/XP"),
                    false);
        }
    }

    /**
     * Fetches the Coflnet item ID for a given item name, with caching.
     */
    public static String fetchIdByName(String name) {
        if (name == null || name.isEmpty())
            return null;
        if (idByNameCache.containsKey(name)) {
            String cached = idByNameCache.get(name);
            return cached.isEmpty() ? null : cached;
        }

        try {
            String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8);
            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3)).build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://sky.coflnet.com/api/items/search/" + encoded + "?limit=1"))
                    .GET().build();
            HttpResponse<String> resp = http.send(req,
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                String body = resp.body();
                JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
                if (arr.size() > 0) {
                    String tag = arr.get(0).getAsJsonObject().get("tag").getAsString();
                    idByNameCache.put(name, tag);
                    return tag;
                }
            }
        } catch (Exception e) {
            System.err.println("[Ihanuat] Cofl item ID lookup failed for '" + name + "': " + e.getMessage());
        }
        idByNameCache.put(name, "");
        return null;
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private static synchronized void fetchBazaarPrices() {
        lastBazaarFetchTime = System.currentTimeMillis();
        new Thread(() -> {
            HttpClient client = HttpClient.newHttpClient();
            performFetchInternal(client);
        }).start();
    }

    private static void performFetchInternal(HttpClient client) {
        for (Map.Entry<String, String> entry : ItemConstants.BAZAAR_MAPPING.entrySet()) {
            String itemName = entry.getKey();
            String itemTag = entry.getValue();

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://sky.coflnet.com/api/item/price/" + itemTag + "/current"))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request,
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    BazaarApiResponse data = GSON.fromJson(response.body(), BazaarApiResponse.class);
                    if (data != null && data.buy > 0) {
                        bazaarPrices.put(itemName, data.buy);
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to fetch bazaar price for " + itemName + ": " + e.getMessage());
            }
        }
        updateConfiguredPetXpPrices();
        ProfitManager.markAllHudDirty();
    }

    private static void updateConfiguredPetXpPrices() {
        bazaarPrices.keySet().removeIf(key -> key.startsWith("Pet XP (") && key.endsWith(")"));
        for (String petConfig : MacroConfig.petXpTrackedPets) {
            MacroConfig.PetInfo info = new MacroConfig.PetInfo(petConfig);
            double pricePerXp = getConfiguredPetXpPrice(info);
            if (pricePerXp <= 0) {
                bazaarPrices.remove("Pet XP (" + info.name + ")");
                continue;
            }
            bazaarPrices.put("Pet XP (" + info.name + ")", pricePerXp);
        }
    }

    /**
     * Fetches the lowest BIN for level-1 and level-max for all configured pets.
     */
    @SuppressWarnings("unused")
    private static void fetchPetXpPrice(HttpClient http) {
        for (String petConfig : MacroConfig.petXpTrackedPets) {
            MacroConfig.PetInfo info = new MacroConfig.PetInfo(petConfig);
            long[] table = PetXpTracker.getXpTable(info.rarity, info.maxLevel);
            final long TOTAL_XP = table[info.maxLevel];

            try {
                // Level 1
                long lvl1Price = 0;
                String url1 = "https://sky.coflnet.com/api/auctions/tag/" + info.tag
                        + "/active/overview?query%5BRarity%5D=" + info.rarity.name();

                HttpRequest req1 = HttpRequest.newBuilder()
                        .uri(URI.create(url1))
                        .GET().build();
                HttpResponse<String> resp1 = http.send(req1,
                        HttpResponse.BodyHandlers.ofString());

                if (resp1.statusCode() == 200) {
                    java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<List<OverviewEntry>>() {
                    }.getType();
                    List<OverviewEntry> listings = GSON.fromJson(resp1.body(), listType);
                    if (listings != null) {
                        for (OverviewEntry entry : listings) {
                            if (entry.price > 0 && (lvl1Price == 0 || entry.price < lvl1Price)) {
                                lvl1Price = entry.price;
                            }
                        }
                    }
                }

                // Max Level
                long lvlMaxPrice = 0;
                String urlMax = "https://sky.coflnet.com/api/auctions/tag/" + info.tag
                        + "/active/overview?query%5BRarity%5D=" + info.rarity.name()
                        + "&query%5BPetLevel%5D=" + info.maxLevel;

                HttpRequest reqMax = HttpRequest.newBuilder()
                        .uri(URI.create(urlMax))
                        .GET().build();
                HttpResponse<String> respMax = http.send(reqMax,
                        HttpResponse.BodyHandlers.ofString());

                if (respMax.statusCode() == 200) {
                    java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<List<OverviewEntry>>() {
                    }.getType();
                    List<OverviewEntry> listings = GSON.fromJson(respMax.body(), listType);
                    if (listings != null) {
                        for (OverviewEntry entry : listings) {
                            if (entry.price > 0 && (lvlMaxPrice == 0 || entry.price < lvlMaxPrice)) {
                                lvlMaxPrice = entry.price;
                            }
                        }
                    }
                }

                if (lvl1Price > 0)
                    petLvl1Prices.put(info.name, lvl1Price);
                if (lvlMaxPrice > 0)
                    petMaxLvlPrices.put(info.name, lvlMaxPrice);

                if (lvlMaxPrice > lvl1Price && lvl1Price > 0) {
                    double pricePerXp = (double) (lvlMaxPrice - lvl1Price) / TOTAL_XP;
                    if (pricePerXp > 0) {
                        bazaarPrices.put("Pet XP (" + info.name + ")", pricePerXp);
                    }
                }
            } catch (Exception e) {
                System.err.println("[Ihanuat] Failed to fetch Pet XP price for " + info.name + ": " + e.getMessage());
            }
        }
    }

    private static class BazaarApiResponse {
        double buy;
    }

    /** One entry from /api/auctions/tag/{tag}/active/overview */
    private static class OverviewEntry {
        long price;
        String uuid;
    }
}
