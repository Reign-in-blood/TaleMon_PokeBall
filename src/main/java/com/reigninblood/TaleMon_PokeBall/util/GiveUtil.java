package com.reigninblood.TaleMon_PokeBall.util;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.logging.Level;

public final class GiveUtil {

    private GiveUtil() {}

    public static boolean giveToStorage(Player player, ItemStack stack) {
        try {
            if (player == null || stack == null || stack == ItemStack.EMPTY) return false;

            Inventory inv = player.getInventory();
            if (inv == null) return false;

            // On tente des sections connues (les ids changent selon build)
            // Dans tes logs: section=-2 a déjà marché une fois. On essaye quelques candidates.
            int[] sectionIds = new int[]{-2, 2, 1, 0, 3, 4, 5};

            for (int sectionId : sectionIds) {
                Object container = null;
                try {
                    Method getSectionById = inv.getClass().getMethod("getSectionById", int.class);
                    container = getSectionById.invoke(inv, sectionId);
                } catch (Throwable ignored) {}

                if (container == null) continue;

                boolean ok = tryInsertIntoContainer(container, stack);
                if (ok) {
                    HytaleLogger.getLogger().at(Level.INFO).log(
                            "[TaleMon_PokeBall] GiveUtil: OK section=%s item=%s x%s container=%s",
                            sectionId, stack.getItemId(), stack.getQuantity(), container.getClass().getName()
                    );
                    player.sendInventory();
                    return true;
                }
            }

            // dernier fallback: tenter Inventory.putAll(int) si ça correspond à un "quickStack"
            try {
                Method putAll = inv.getClass().getMethod("putAll", int.class);
                Object tx = putAll.invoke(inv, 0);
                HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] GiveUtil: fallback putAll(0) tx=%s", String.valueOf(tx));
            } catch (Throwable ignored) {}

            HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] GiveUtil: FAILED: no container accepted the item");
            return false;

        } catch (Throwable t) {
            HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] GiveUtil: FAILED: %s", String.valueOf(t));
            return false;
        }
    }

    private static boolean tryInsertIntoContainer(Object container, ItemStack stack) {
        // On tente des méthodes communes d’insertion
        String[] methodNames = new String[]{
                "addItemStack",
                "tryAddItemStack",
                "insertItemStack",
                "add",
                "tryAdd",
                "offer",
                "put"
        };

        for (String name : methodNames) {
            for (Method m : container.getClass().getMethods()) {
                if (!m.getName().equals(name)) continue;
                Class<?>[] p = m.getParameterTypes();
                try {
                    if (p.length == 1 && p[0] == ItemStack.class) {
                        Object r = m.invoke(container, stack);
                        // si boolean -> OK
                        if (r instanceof Boolean b) return b;
                        // si transaction/object -> on considère OK si non null
                        return r != null;
                    }
                } catch (Throwable ignored) {}
            }
        }

        return false;
    }
}
