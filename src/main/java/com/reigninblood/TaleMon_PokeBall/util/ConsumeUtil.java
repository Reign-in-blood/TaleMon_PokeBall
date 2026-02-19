package com.reigninblood.TaleMon_PokeBall.util;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.logging.Level;

public final class ConsumeUtil {

    private ConsumeUtil() {
    }

    /**
     * Consomme 1 item de la hotbar active si l'item tenu est une PokéBall et que le joueur n'est pas en créatif.
     * Compatible Update 3 + fallback versions précédentes via reflection.
     */
    public static boolean consumeActiveHotbarItem(Player player, ItemStack expectedHeld) {
        if (player == null || expectedHeld == null || expectedHeld.isEmpty()) return false;

        if (isCreative(player)) {
            return false;
        }

        ItemStack active = readActiveHeld(player);
        if (active == null || active.isEmpty()) return false;

        String expectedId = getItemId(expectedHeld);
        String activeId = getItemId(active);
        if (!isPokeballId(expectedId) || !isPokeballId(activeId)) {
            return false;
        }

        if (!expectedId.equals(activeId)) {
            HytaleLogger.getLogger().at(Level.INFO).log(
                    "[TaleMon_PokeBall] consume skipped (held mismatch): expected=%s active=%s",
                    expectedId, activeId
            );
            return false;
        }

        int qty = getQuantity(active);
        if (qty <= 0) return false;

        ItemStack replacement;
        if (qty == 1) {
            replacement = new ItemStack(activeId, 0);
        } else {
            replacement = active.withQuantity(qty - 1);
        }

        return writeActiveHeld(player, replacement);
    }

    private static boolean isPokeballId(String itemId) {
        return itemId != null && itemId.startsWith("PokeBall");
    }

    private static ItemStack readActiveHeld(Player player) {
        Object value = invokeNoArg(player, "getHeldItem");
        if (!(value instanceof ItemStack)) value = invokeNoArg(player, "getActiveItem");
        if (!(value instanceof ItemStack)) value = invokeNoArg(player, "getMainHandItem");
        return value instanceof ItemStack stack ? stack : null;
    }

    private static boolean writeActiveHeld(Player player, ItemStack stack) {
        if (invokeSetter(player, stack, "setHeldItem")) return true;
        if (invokeSetter(player, stack, "setActiveItem")) return true;
        if (invokeSetter(player, stack, "setMainHandItem")) return true;
        HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] consume failed: no setter for active held item");
        return false;
    }

    private static boolean isCreative(Player player) {
        Object value = invokeNoArg(player, "isCreativeMode");
        if (!(value instanceof Boolean)) value = invokeNoArg(player, "isCreative");
        if (!(value instanceof Boolean)) {
            value = invokeNoArg(player, "getGameMode");
            if (value != null && "CREATIVE".equalsIgnoreCase(String.valueOf(value))) {
                return true;
            }
        }
        return value instanceof Boolean b && b;
    }

    private static String getItemId(ItemStack stack) {
        try {
            return stack.getItemId();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int getQuantity(ItemStack stack) {
        try {
            return stack.getQuantity();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static Object invokeNoArg(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean invokeSetter(Object target, ItemStack value, String method) {
        try {
            Method m = target.getClass().getMethod(method, ItemStack.class);
            m.invoke(target, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
