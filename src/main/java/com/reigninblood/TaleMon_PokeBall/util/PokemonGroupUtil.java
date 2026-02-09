package com.reigninblood.TaleMon_PokeBall.util;

import com.hypixel.hytale.builtin.tagset.config.NPCGroup;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.logging.Level;

public final class PokemonGroupUtil {

    // Doit correspondre au nom de ton asset: Pokemon.json => id "Pokemon"
    private static final String GROUP_ID = "Pokemon";

    private static volatile String[] cachedInclude = null;

    private PokemonGroupUtil() {}

    public static boolean isPokemonRole(@Nonnull String roleName) {
        String[] list = getIncludedRoles();
        if (list == null || list.length == 0) return false;

        for (String s : list) {
            if (roleName.equals(s)) return true;
        }
        return false;
    }

    /** (Optionnel) si tu veux forcer reload en dev */
    public static void reload() {
        cachedInclude = null;
    }

    private static String[] getIncludedRoles() {
        if (cachedInclude != null) return cachedInclude;

        NPCGroup group = loadGroupById(GROUP_ID);
        if (group == null) {
            HytaleLogger.getLogger().at(Level.INFO).log(
                    "[TaleMon_PokeBall] PokemonGroupUtil: group '%s' not found (Pokemon.json not loaded?)",
                    GROUP_ID
            );
            cachedInclude = new String[0];
            return cachedInclude;
        }

        String[] roles = group.getIncludedTags(); // IncludeRoles -> includedRoles :contentReference[oaicite:0]{index=0}
        cachedInclude = (roles != null) ? roles : new String[0];

        HytaleLogger.getLogger().at(Level.INFO).log(
                "[TaleMon_PokeBall] PokemonGroupUtil: loaded %s pokemon roles from group '%s'",
                cachedInclude.length, GROUP_ID
        );

        return cachedInclude;
    }

    /**
     * Charge un NPCGroup via NPCGroup.getAssetMap() sans dépendre d'une API précise.
     * On essaye plusieurs méthodes probables sur l'asset map (get, getOrNull, getByKey...).
     */
    private static NPCGroup loadGroupById(@Nonnull String id) {
        try {
            Object assetMap = NPCGroup.getAssetMap(); // existe dans la classe NPCGroup :contentReference[oaicite:1]{index=1}
            if (assetMap == null) return null;

            // Essaye les méthodes les plus probables sur l'asset map
            NPCGroup g;

            g = tryCall(assetMap, "get", id);
            if (g != null) return g;

            g = tryCall(assetMap, "getOrNull", id);
            if (g != null) return g;

            g = tryCall(assetMap, "getByKey", id);
            if (g != null) return g;

            g = tryCall(assetMap, "find", id);
            if (g != null) return g;

            // Si vraiment aucune méthode ne match, on dump pour t'aider
            dumpAssetMapMethods(assetMap);
            return null;

        } catch (Throwable t) {
            HytaleLogger.getLogger().at(Level.INFO).log(
                    "[TaleMon_PokeBall] PokemonGroupUtil: loadGroupById failed: %s",
                    String.valueOf(t)
            );
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static NPCGroup tryCall(Object target, String methodName, String id) {
        try {
            Method m = target.getClass().getMethod(methodName, String.class);
            Object r = m.invoke(target, id);
            if (r instanceof NPCGroup) return (NPCGroup) r;
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void dumpAssetMapMethods(Object assetMap) {
        try {
            HytaleLogger.getLogger().at(Level.INFO).log(
                    "[TaleMon_PokeBall] PokemonGroupUtil: AssetMap class=%s",
                    assetMap.getClass().getName()
            );
            for (Method m : assetMap.getClass().getMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class) {
                    HytaleLogger.getLogger().at(Level.INFO).log(
                            "[TaleMon_PokeBall] PokemonGroupUtil: AssetMap method candidate: %s(%s) -> %s",
                            m.getName(),
                            m.getParameterTypes()[0].getName(),
                            m.getReturnType().getName()
                    );
                }
            }
        } catch (Throwable ignored) {}
    }
}
