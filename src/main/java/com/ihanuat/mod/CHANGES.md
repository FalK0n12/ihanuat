# Ihanuat Mod — Developer Change Log

This document records every change made to the mod, why it was made, and how
each feature works. Future engineers: read this before touching anything.

---

## Change Set: Multi-fix + New Features (2025)

### 1. Chat Rule webhook firing twice — FIXED
**Files:** `IhanuatClient.java`, `ChatHudMixin.java`, `ChatRuleManager.java`

**Root cause:** `ChatRuleManager.handleChatMessage()` was called from three places
simultaneously:
- `ChatHudMixin.onAddMessage()` (the Mixin — fires for all HUD messages)
- `IhanuatClient` — `ClientReceiveMessageEvents.GAME.register()` (duplicate)
- `IhanuatClient` — `ClientReceiveMessageEvents.CHAT.register()` (duplicate)

Player messages that matched both GAME and the mixin triggered the webhook 3×.

**Fix:** Removed both `ClientReceiveMessageEvents` registrations from
`IhanuatClient`. The mixin is the only call site. It fires exactly once per
displayed line for every message type (player chat, system, NPC, Hypixel).

**Defence-in-depth:** `ChatRuleManager` also has a `ConcurrentHashMap` dedup
guard keyed on `ruleName + first-80-chars-of-message` with a 3-second window,
preventing any accidental double-fire even if the pipeline changes in future.

---

### 2. Chat listener buggy / misses messages — FIXED
**File:** `ChatHudMixin.java`

**Root cause:** Fabric's `ClientReceiveMessageEvents.GAME/CHAT` do not capture
all message types (some system messages bypass them).

**Fix:** `ChatHudMixin` injects into `ChatComponent.addMessage` at HEAD. This
intercepts every single line that reaches the HUD, regardless of origin. The
mixin is now documented as the single authoritative entry point. See the class
javadoc in `ChatHudMixin.java` for full rationale.

---

### 3. Debug tab was missing — RESTORED + EXPANDED
**File:** `ConfigScreenFactory.java`

Debug settings (Show Debug Messages, Log Debug to File, Open Log Folder) were
buried inside the QOL tab. They have been moved to a dedicated **Debug** tab.

All new experimental features are added to the Debug tab first. They are only
promoted to other tabs once confirmed stable by the user.

---

### 4. Discord tab — NEW
**File:** `ConfigScreenFactory.java`

All Discord-related settings (Webhook URL, Send Status Updates, Update Interval)
are now in a dedicated **Discord** tab. Removed from QOL tab. Includes tooltip
explanations for both Chat Rule Alerts and Session Done Alerts.

---

### 5. Chat Rule Discord embed — REDESIGNED
**File:** `ChatRuleManager.java`

New format:
- Top-level content: `||@here||` (Discord mention ping)
- Red embed (color `#ED4245`)
- Title: `Chat Alert`
- Description: `'<full triggering message>'` (message wrapped in single quotes)
- Screenshot crop: 40% trimmed from top, 40% from right, 20% from bottom
  (focuses on the bottom-left chat area)

The crop ratios in code: `CROP_X_START=0`, `CROP_WIDTH=0.60`,
`CROP_Y_START=0.40`, `CROP_Y_END=0.80`.

---

### 6. Session Done Discord embed — NEW
**File:** `QuitThresholdManager.java`

When the Quit Threshold is reached, before disconnecting, a Discord webhook is
fired:
- Top-level content: `||@here||`
- Green embed (color `#57F287`)
- Title: `Session Done`
- Description:
  - `"You've farmed X, disconnecting safely."` if `forceQuitMinecraft = false`
  - `"You've farmed X, disconnecting and closing your instance safely."` if true
  - Where X is the total active session time (formatted as "Xh Ym" or "Ym Zs")
- Full screenshot attached

The webhook fires 3.5 s before the actual disconnect to give the screenshot
time to be taken and uploaded.

---

### 7. Status Manager embed — REDESIGNED
**File:** `DiscordStatusManager.java`

Old format had a generic description ("Here is your latest Ihanuat status
update! 🚀"), a "Current State" field, and "Time Until Next Rest" label.

New format:
- No description text
- Blue embed (color `#5865F2`)
- Title: `Status Update`
- Three inline fields (left to right):
  1. **Session Time** — HH:MM:SS or MM:SS
  2. **Next Rest** — countdown, "Resting now…", or "—"
  3. **Profit/Hour** — compact coins-per-hour from session profit tracker
     (e.g. 55,300,120 → "55.3m", 980,000 → "980k")

`compactCoins(long)` is `public static` so it can be reused elsewhere.

---

### 8. wardrobePostSwapDelay not applying to AOTV path — FIXED
**File:** `PestCleaningSequencer.java`

`restoreGearForCleaning()` now takes a boolean `aotvPath` parameter.
- When `aotvPath = true`: uses a **static 250 ms** delay (pest-spawn response
  must be fast; the user-tuned delay is for the slower farm-return path).
- When `aotvPath = false`: uses `MacroConfig.wardrobePostSwapDelay`.

The AOTV/non-AOTV decision is made before calling `restoreGearForCleaning()` so
the correct delay is passed in. No other code paths are affected.

---

### 9. [DEBUG] Pest Clean → /warp garden fast-path — NEW
**Files:** `PestReturnManager.java`, `MacroConfig.java`, `ConfigScreenFactory.java`

Toggle: **Debug → [Exp] Warp Garden After Pest Clean** (`pestWarpGardenAfterClean`)

When enabled AND macro has been running for >30 seconds (guards against fresh
K-press starts), `finalizeReturnToFarm()` skips the full `swapToFarmingToolSync`
+ `waitForGearAndGui` dance and immediately:
1. Sets state to FARMING
2. `/warp garden`
3. `swapToFarmingTool` + restart farming script

This eliminates the "briefly switches to Aspect of the End" artefact that
appeared between pest cleaning finishing and farming resuming.

Visitor flow is always checked first and is unaffected by this toggle.

---

### 10. [DEBUG] Skip farming tool check after wardrobe swap — NEW
**Files:** `PestPrepSwapManager.java`, `MacroConfig.java`, `ConfigScreenFactory.java`

Toggle: **Debug → [Exp] Skip Farming Tool Check After Wardrobe Swap**
(`skipFarmingToolCheckAfterRun`)

When enabled AND macro has been running >30 seconds, after the pest-cooldown
prep-swap sequence completes, `GearManager.finalResume()` is skipped. Instead,
the farming script restarts directly with `swapToFarmingTool` + `startScript`.

Rationale: if the macro has been farming continuously and we just swapped back
to the farming wardrobe, the farming tool was never changed. `finalResume()`'s
tool-equipped check is redundant and adds hundreds of milliseconds of delay.

---

### 11. Timer pegging: Current Session + Next Rest — FIXED
**Files:** `IhanuatClient.java`, `MacroStateManager.java`

**Old behaviour:** `DynamicRestManager.reset()` was called unconditionally on
every server join (including rejoining after leaving the garden mid-session),
wiping the current session timer and the next-rest countdown.

**New behaviour:** `DynamicRestManager.reset()` on join is now guarded by
`!MacroStateManager.isMacroRunning()`. If the macro is running when the join
event fires (e.g. user left garden and came back), the timers are preserved and
continue counting from where they left off.

Timers are only zeroed by:
- Pressing K to stop the macro
- A scheduled Dynamic Rest re-arm after a reconnect break

---

### 12. getMacroActiveSinceMs() — NEW
**File:** `MacroStateManager.java`

`public static long getMacroActiveSinceMs()` returns the epoch-ms when the
macro last transitioned out of the OFF state (i.e. the last K press). Returns 0
if the macro is not running.

Used by the 30-second guard in both debug toggles so that fresh K-press startups
always take the full safe path, regardless of whether experimental toggles are on.

---

## Architecture Notes

### Chat message pipeline
```
Minecraft net layer
       │
       ▼
ChatComponent.addMessage()  ← ChatHudMixin injects HERE (sole entry point)
       │
       ├─► ChatRuleManager.handleChatMessage()
       │         │
       │         └─► dedup check → sendAlertAsync() → Discord webhook
       │
       └─► filter (hideFilteredChat) → ci.cancel() if noisy message
```

### Pest cleaning state machine
```
Pest timer fires
       │
       ▼
PestPrepSwapManager.triggerPrepSwap()   (wardrobe swap while still farming)
  └─ [DEBUG] skipFarmingToolCheckAfterRun → skip finalResume if >30s uptime
       │
       ▼  (on pest spawn in chat / threshold hit)
PestCleaningSequencer.startCleaningSequence()
  └─ restoreGearForCleaning(aotvPath)
       ├─ aotvPath=true  → 250ms static delay
       └─ aotvPath=false → wardrobePostSwapDelay (user configured)
  └─ AOTV to roof OR /tptoplot
  └─ start pestCleaner script
       │
       ▼
PestReturnManager.handlePestCleaningFinished()  (script exits)
  └─ finalizeReturnToFarm()
       ├─ visitor check (always full path)
       ├─ [DEBUG] pestWarpGardenAfterClean → /warp garden directly if >30s uptime
       └─ standard path: swapToFarmingToolSync + waitForGearAndGui + restart
```

### Discord webhook types
| Event | Color | Ping | Content |
|-------|-------|------|---------|
| Status Update | Blue `#5865F2` | none | Session Time, Next Rest, Profit/Hour + screenshot |
| Chat Alert | Red `#ED4245` | `\|\|@here\|\|` | `'message'` + cropped chat screenshot |
| Session Done | Green `#57F287` | `\|\|@here\|\|` | farmed time text + full screenshot |
