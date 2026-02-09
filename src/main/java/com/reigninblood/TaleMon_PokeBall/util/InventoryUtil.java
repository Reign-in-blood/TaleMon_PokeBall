package com.reigninblood.TaleMon_PokeBall.util;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.logging.Level;

public final class InventoryUtil {

    private InventoryUtil() {}

    /** Retire 1 item tenu si son itemId == expectedId */
    public static boolean decrementHeldItem(Player player, String expectedId) {
        try {
            ItemStack held = getHeldItem(player);
            if (held == null) {
                HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] InventoryUtil: held item is null");
                return false;
            }

            String itemId = getItemId(held);
            int qty = getQuantity(held);

            if (itemId == null || !itemId.equals(expectedId)) {
                HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] InventoryUtil: held item mismatch id=%s expected=%s", String.valueOf(itemId), expectedId);
                return false;
            }

            if (qty <= 0) return false;

            // crée un nouveau stack avec quantity-1
            ItemStack newHeld = new ItemStack(itemId, qty - 1);
            copyDurabilityIfPossible(held, newHeld);

            return setHeldItem(player, newHeld);
        } catch (Throwable t) {
            HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] InventoryUtil: decrementHeldItem ERROR %s", t.toString());
            dumpPlayerInventoryMethods(player);
            return false;
        }
    }

    /** Ajoute un ItemStack à l'inventaire du joueur (best effort via reflection). */
    public static boolean addToInventory(Player player, ItemStack stack) {
        try {
            Object inv = getInventoryObject(player);
            if (inv == null) {
                HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] InventoryUtil: inventory object is null");
                return false;
            }

            // méthodes possibles: addItem, add, give, tryAdd, addToInventory...
            String[] names = {"addItem", "add", "give", "tryAdd", "addToInventory", "tryAddItem"};
            for (String name : names) {
                Method ok = findMethod(inv.getClass(), name, ItemStack.class);
                if (ok != null) {
                    Object r = ok.invoke(inv, stack);
                    // si retourne boolean -> on l'utilise, sinon on considère OK
                    if (r instanceof Boolean) return (Boolean) r;
                    return true;
                }
            }

            // fallback: player.giveItem(stack) ?
            Method give = findMethod(player.getClass(), "giveItem", ItemStack.class);
            if (give != null) {
                Object r = give.invoke(player, stack);
                if (r instanceof Boolean) return (Boolean) r;
                return true;
            }

            HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] InventoryUtil: no add method found on inventory/player");
            dumpInventoryMethods(inv);
            return false;

        } catch (Throwable t) {
            HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] InventoryUtil: addToInventory ERROR %s", t.toString());
            return false;
        }
    }

    // ------------------- internals -------------------

    private static ItemStack getHeldItem(Player player) {
        // On cherche un getter plausible (ta build n'a pas getActiveItem())
        try {
            Method m = findAnyNoArgMethod(player.getClass(), "getHeldItem", "getActiveItem", "getMainHandItem", "getWieldedItem");
            if (m != null) {
                Object r = m.invoke(player);
                return (r instanceof ItemStack) ? (ItemStack) r : null;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean setHeldItem(Player player, ItemStack newStack) {
        try {
            Method m = findMethod(player.getClass(), "setHeldItem", ItemStack.class);
            if (m != null) {
                m.invoke(player, newStack);
                return true;
            }

            // alternative: setActiveItem / setMainHandItem
            Method alt = findAnyOneArgMethod(player.getClass(), ItemStack.class, "setActiveItem", "setMainHandItem", "setWieldedItem");
            if (alt != null) {
                alt.invoke(player, newStack);
                return true;
            }

            HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] InventoryUtil: cannot set held item (no setter found)");
            dumpPlayerInventoryMethods(player);
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object getInventoryObject(Player player) {
        try {
            Method m = findAnyNoArgMethod(player.getClass(), "getInventory", "inventory", "getPlayerInventory");
            if (m != null) return m.invoke(player);
        } catch (Throwable ignored) {}
        return null;
    }

    private static String getItemId(ItemStack stack) {
        try {
            Method m = stack.getClass().getMethod("getItemId");
            Object r = m.invoke(stack);
            return (r instanceof String) ? (String) r : null;
        } catch (Throwable ignored) {}
        // fallback reflection field
        try {
            var f = stack.getClass().getDeclaredField("itemId");
            f.setAccessible(true);
            Object r = f.get(stack);
            return (r instanceof String) ? (String) r : null;
        } catch (Throwable ignored) {}
        return null;
    }

    private static int getQuantity(ItemStack stack) {
        try {
            Method m = stack.getClass().getMethod("getQuantity");
            Object r = m.invoke(stack);
            return (r instanceof Integer) ? (Integer) r : 0;
        } catch (Throwable ignored) {}
        try {
            var f = stack.getClass().getDeclaredField("quantity");
            f.setAccessible(true);
            Object r = f.get(stack);
            return (r instanceof Integer) ? (Integer) r : 0;
        } catch (Throwable ignored) {}
        return 0;
    }

    private static void copyDurabilityIfPossible(ItemStack from, ItemStack to) {
        try {
            Method gm = from.getClass().getMethod("getDurability");
            Method sm = to.getClass().getMethod("setDurability", double.class);
            Object d = gm.invoke(from);
            if (d instanceof Double) sm.invoke(to, (Double) d);
        } catch (Throwable ignored) {}
    }

    private static Method findMethod(Class<?> c, String name, Class<?>... params) {
        try { return c.getMethod(name, params); } catch (Throwable ignored) { return null; }
    }

    private static Method findAnyNoArgMethod(Class<?> c, String... names) {
        for (String n : names) {
            try { return c.getMethod(n); } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Method findAnyOneArgMethod(Class<?> c, Class<?> param, String... names) {
        for (String n : names) {
            try { return c.getMethod(n, param); } catch (Throwable ignored) {}
        }
        return null;
    }

    private static void dumpInventoryMethods(Object inv) {
        HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] InventoryUtil: inventory class=%s methods:", inv.getClass().getName());
        for (Method m : inv.getClass().getMethods()) {
            String n = m.getName().toLowerCase();
            if (n.contains("add") || n.contains("give") || n.contains("insert") || n.contains("put")) {
                HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall]   %s(%d) -> %s",
                        m.getName(), m.getParameterCount(), m.getReturnType().getName());
            }
        }
    }

    private static void dumpPlayerInventoryMethods(Player player) {
        HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] InventoryUtil: Player methods containing held/inventory:");
        for (Method m : player.getClass().getMethods()) {
            String n = m.getName().toLowerCase();
            if (n.contains("inventory") || n.contains("held") || n.contains("hand") || n.contains("active") || n.contains("wield")) {
                HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall]   %s(%d) -> %s",
                        m.getName(), m.getParameterCount(), m.getReturnType().getName());
            }
        }
    }
}
