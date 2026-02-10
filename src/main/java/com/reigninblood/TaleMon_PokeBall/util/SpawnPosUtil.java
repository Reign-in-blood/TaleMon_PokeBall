package com.reigninblood.TaleMon_PokeBall.util;

import com.hypixel.hytale.math.vector.Vector3d;

public final class SpawnPosUtil {

    private SpawnPosUtil() {}

    // ✅ Ajuste ça si besoin :
    // 1.6 -> souvent suffisant
    // 2.01 -> très safe (au-dessus de presque tout)
    private static final double SAFE_Y_OFFSET = 1.6;

    /**
     * Déplace la position vers un endroit plus safe :
     * - centre du bloc (x+0.5, z+0.5)
     * - y au-dessus du bloc (y + SAFE_Y_OFFSET)
     *
     * Objectif : éviter spawn dans le sol / dans un bloc -> mort instant.
     */
    public static Vector3d makeSafe(Vector3d raw) {
        if (raw == null) return null;

        double bx = Math.floor(raw.x);
        double by = Math.floor(raw.y);
        double bz = Math.floor(raw.z);

        return new Vector3d(bx + 0.5, by + SAFE_Y_OFFSET, bz + 0.5);
    }

    /**
     * Variante si tu veux forcer encore plus haut ponctuellement.
     */
    public static Vector3d makeSafe(Vector3d raw, double extraY) {
        Vector3d base = makeSafe(raw);
        if (base == null) return null;
        return new Vector3d(base.x, base.y + extraY, base.z);
    }
}
