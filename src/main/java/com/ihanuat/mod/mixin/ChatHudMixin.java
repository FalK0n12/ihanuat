package com.ihanuat.mod.mixin;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.modules.ChatRuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ChatHudMixin — the SOLE entry point for ChatRuleManager.
 *
 * Why a mixin instead of Fabric's ClientReceiveMessageEvents?
 * ──────────────────────────────────────────────────────────────
 * Fabric exposes two events for incoming chat:
 *   • GAME  — fires for server/system messages (Hypixel broadcasts, NPC text…)
 *   • CHAT  — fires for signed player-to-player chat
 *
 * Using both together still misses edge cases (e.g. messages injected directly
 * into the chat component by other mods, system messages that are routed
 * internally, etc.) and — critically — registering both meant every message
 * that matched BOTH event types fired the webhook twice (which was the
 * double-webhook bug).
 *
 * By injecting into ChatComponent.addMessage at HEAD we intercept every single
 * line that ever reaches the visible HUD — player chat, system/server messages,
 * NPC text, Hypixel game messages, death messages, etc.  Exactly one call
 * per displayed line, so no duplicates are possible at the source level.
 *
 * ChatRuleManager adds its own dedup guard as a belt-and-suspenders measure,
 * but this mixin is the primary fix.
 *
 * The IhanuatClient registrations for ClientReceiveMessageEvents.GAME and
 * ClientReceiveMessageEvents.CHAT that previously also called
 * ChatRuleManager.handleChatMessage() have been removed.
 */
@Mixin(ChatComponent.class)
public class ChatHudMixin {

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
    private void onAddMessage(Component message, CallbackInfo ci) {
        String rawText = message.getString();
        // Strip Minecraft colour/format codes before passing to rule matching
        String plainText = rawText.replaceAll("(?i)[\\u00A7&][0-9a-fk-or]", "").trim();

        // ── ChatRules ──────────────────────────────────────────────────────────────
        // This is the single authoritative call site — no other code calls
        // ChatRuleManager.handleChatMessage().  See class javadoc above.
        if (!plainText.isEmpty()) {
            ChatRuleManager.handleChatMessage(Minecraft.getInstance(), plainText);
        }

        // ── Chat cleanup filter ────────────────────────────────────────────────────
        // Suppress noisy Hypixel/script lines when Chat Cleanup is enabled.
        if (!MacroConfig.hideFilteredChat) {
            return;
        }

        if (rawText.contains("for killing a") ||
                rawText.contains("Starting script via command")) {
            ci.cancel();
        }
    }
}
