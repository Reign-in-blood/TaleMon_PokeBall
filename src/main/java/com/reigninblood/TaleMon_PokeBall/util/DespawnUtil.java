package com.reigninblood.TaleMon_PokeBall.util;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Level;

public final class DespawnUtil {

    private DespawnUtil() {}

    public static boolean forceDespawn(CommandBuffer<EntityStore> buffer, Ref<EntityStore> targetRef) {
        Object reason = findAnyRemoveReasonEnumValue();
        if (reason == null) {
            HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] DESPAWN_FAIL: cannot resolve RemoveReason enum value");
            dumpRemoveReasonEnum();
            return false;
        }

        // Try tryRemoveEntity(ref, reason)
        if (invoke(buffer, "tryRemoveEntity", targetRef, reason)) {
            HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] DESPAWN_OK via=tryRemoveEntity(ref,RemoveReason=%s)", reason.toString());
            return true;
        }

        // Fallback removeEntity(ref, reason)
        if (invoke(buffer, "removeEntity", targetRef, reason)) {
            HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] DESPAWN_OK via=removeEntity(ref,RemoveReason=%s)", reason.toString());
            return true;
        }

        HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] DESPAWN_FAIL: removeEntity/tryRemoveEntity invocation failed (reason=%s)", reason.toString());
        dumpBufferRemoveSignatures(buffer);
        return false;
    }

    private static boolean invoke(Object obj, String methodName, Object arg0, Object arg1) {
        try {
            for (Method m : obj.getClass().getMethods()) {
                if (!m.getName().equals(methodName)) continue;
                if (m.getParameterCount() != 2) continue;

                Class<?> p0 = m.getParameterTypes()[0];
                Class<?> p1 = m.getParameterTypes()[1];

                if (!p0.isAssignableFrom(arg0.getClass())) continue;
                if (!p1.isAssignableFrom(arg1.getClass())) continue;

                m.invoke(obj, arg0, arg1);
                return true;
            }
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * On récupère n'importe quelle valeur de l'enum RemoveReason (la première),
     * juste pour faire fonctionner l'appel. Ensuite on pourra choisir la "bonne".
     */
    private static Object findAnyRemoveReasonEnumValue() {
        try {
            Class<?> enumCls = Class.forName("com.hypixel.hytale.component.RemoveReason");
            if (!enumCls.isEnum()) return null;
            Object[] values = enumCls.getEnumConstants();
            if (values == null || values.length == 0) return null;
            return values[0];
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void dumpRemoveReasonEnum() {
        try {
            Class<?> enumCls = Class.forName("com.hypixel.hytale.component.RemoveReason");
            if (!enumCls.isEnum()) {
                HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] RemoveReason is not enum?? cls=%s", enumCls.getName());
                return;
            }
            Object[] values = enumCls.getEnumConstants();
            HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] RemoveReason values:");
            if (values != null) {
                for (Object v : values) {
                    HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall]   %s", v.toString());
                }
            }
        } catch (Throwable t) {
            HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] RemoveReason dump failed: %s", t.toString());
        }
    }

    private static void dumpBufferRemoveSignatures(Object buffer) {
        for (Method m : buffer.getClass().getMethods()) {
            String n = m.getName();
            if (!n.equals("removeEntity") && !n.equals("tryRemoveEntity")) continue;
            Class<?>[] p = m.getParameterTypes();
            HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] buffer.%s(%s,%s) -> %s",
                    n,
                    p.length > 0 ? p[0].getName() : "",
                    p.length > 1 ? p[1].getName() : "",
                    m.getReturnType().getName()
            );
        }
    }
}
