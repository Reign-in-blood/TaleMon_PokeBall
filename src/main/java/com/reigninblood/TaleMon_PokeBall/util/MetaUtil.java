package com.reigninblood.TaleMon_PokeBall.util;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;

import java.lang.reflect.Method;
import java.util.logging.Level;

public final class MetaUtil {

    private MetaUtil() {}

    /**
     * Retourne un NOUVEL ItemStack avec metadata CapturedEntity.
     * Si échec -> retourne stack original.
     */
    public static ItemStack withCapturedEntity(ItemStack stack,
                                               int roleIndex,
                                               String npcNameKey,
                                               String iconPath,
                                               String fullItemIcon) {

        try {
            // Sous-document CapturedEntity
            BsonDocument captured = new BsonDocument();
            captured.put("RoleIndex", new BsonInt32(roleIndex));
            if (npcNameKey != null) captured.put("NpcNameKey", new BsonString(npcNameKey));
            if (iconPath != null) captured.put("IconPath", new BsonString(iconPath));
            if (fullItemIcon != null) captured.put("FullItemIcon", new BsonString(fullItemIcon));

            // Document root metadata
            BsonDocument meta = new BsonDocument();
            meta.put("CapturedEntity", captured);

            // On veut appeler une des overloads withMetadata(...)
            ItemStack out = tryWithMetadata(stack, meta);
            if (out == null) {
                HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] MetaUtil: withMetadata(...) not found / failed");
                dumpWithMetadataMethods(stack);
                return stack;
            }

            // log du résultat
            try {
                Object md = stack.getClass().getMethod("getMetadata").invoke(out);
                HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] MetaUtil: metadata after set = %s", String.valueOf(md));
            } catch (Throwable ignored) {}

            return out;

        } catch (Throwable t) {
            HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] MetaUtil ERROR: %s", t.toString());
            return stack;
        }
    }

    private static ItemStack tryWithMetadata(ItemStack stack, BsonDocument meta) {
        try {
            // Cherche une méthode withMetadata(...) qui accepte un BsonDocument
            for (Method m : stack.getClass().getMethods()) {
                if (!m.getName().equals("withMetadata")) continue;

                int pc = m.getParameterCount();
                Class<?>[] p = m.getParameterTypes();

                // withMetadata(BsonDocument)
                if (pc == 1 && BsonDocument.class.isAssignableFrom(p[0])) {
                    Object r = m.invoke(stack, meta);
                    return (r instanceof ItemStack) ? (ItemStack) r : null;
                }

                // withMetadata(String, BsonDocument) ou (String, Object)
                if (pc == 2 && p[0] == String.class) {
                    // On essaie la variante "key + subdoc"
                    Object r = m.invoke(stack, "CapturedEntity", meta.get("CapturedEntity"));
                    return (r instanceof ItemStack) ? (ItemStack) r : null;
                }

                // withMetadata(String, String, BsonDocument) etc (pc==3)
                // On ignore tant qu’on n’a pas besoin.
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void dumpWithMetadataMethods(ItemStack stack) {
        HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] MetaUtil: available withMetadata overloads:");
        for (Method m : stack.getClass().getMethods()) {
            if (m.getName().equals("withMetadata")) {
                HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall]   withMetadata(%d) -> %s",
                        m.getParameterCount(), m.getReturnType().getName());
            }
        }
    }
}
