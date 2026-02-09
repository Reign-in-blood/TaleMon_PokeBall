package com.reigninblood.TaleMon_PokeBall.util;

import com.hypixel.hytale.builtin.tagset.config.NPCGroup;
import com.hypixel.hytale.logger.HytaleLogger;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public final class NpcGroupDebugUtil {

    private NpcGroupDebugUtil() {}

    public static void dumpAllNpcGroups() {
        try {
            Object map = NPCGroup.getAssetMap();
            if (map == null) {
                HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] NPCGroup assetMap = null");
                return;
            }

            HytaleLogger.getLogger().at(Level.INFO).log(
                    "[TaleMon_PokeBall] NPCGroup assetMap class=%s",
                    map.getClass().getName()
            );

            // 1) Essaye des méthodes classiques: keySet(), keys(), getKeys(), ids(), getIds()
            Object keysObj = tryNoArg(map, "keySet");
            if (keysObj == null) keysObj = tryNoArg(map, "keys");
            if (keysObj == null) keysObj = tryNoArg(map, "getKeys");
            if (keysObj == null) keysObj = tryNoArg(map, "ids");
            if (keysObj == null) keysObj = tryNoArg(map, "getIds");

            if (keysObj instanceof Set<?> set) {
                HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] NPCGroups loaded (count=%s): %s", set.size(), set);
                return;
            }
            if (keysObj instanceof Collection<?> col) {
                HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] NPCGroups loaded (count=%s): %s", col.size(), col);
                return;
            }
            if (keysObj instanceof String[] arr) {
                HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] NPCGroups loaded (count=%s): %s", arr.length, java.util.Arrays.toString(arr));
                return;
            }

            // 2) Certains maps exposent un Map interne via asMap()/getMap()
            Object asMap = tryNoArg(map, "asMap");
            if (asMap == null) asMap = tryNoArg(map, "getMap");
            if (asMap instanceof Map<?,?> m) {
                HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] NPCGroups loaded (count=%s): %s", m.size(), m.keySet());
                return;
            }

            // 3) Si rien ne marche: on dump les méthodes no-arg candidates
            HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] Could not extract keys from assetMap. Methods:");
            for (Method mm : map.getClass().getMethods()) {
                if (mm.getParameterCount() == 0) {
                    String n = mm.getName().toLowerCase();
                    if (n.contains("key") || n.contains("id") || n.contains("map") || n.contains("all")) {
                        HytaleLogger.getLogger().at(Level.INFO).log(
                                "[TaleMon_PokeBall]  %s() -> %s",
                                mm.getName(),
                                mm.getReturnType().getName()
                        );
                    }
                }
            }

        } catch (Throwable t) {
            HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] dumpAllNpcGroups failed: %s", String.valueOf(t));
        }
    }

    private static Object tryNoArg(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
