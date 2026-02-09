package com.reigninblood.TaleMon_PokeBall.util;

import com.hypixel.hytale.math.vector.Vector3d;

public final class SpawnPosUtil {

    private SpawnPosUtil() {}

    /**
     * Déplace la position visée vers un endroit plus safe (au centre d'un bloc + au-dessus).
     * Sans accès direct au block/world API ici, on fait un "best effort" :
     * - centre du bloc (x+0.5 z+0.5)
     * - y + 1.01 pour éviter l'intérieur du bloc
     */
    public static Vector3d makeSafe(Vector3d raw) {
        if (raw == null) return null;

        double bx = Math.floor(raw.x);
        double by = Math.floor(raw.y);
        double bz = Math.floor(raw.z);

        // centre + au dessus
        return new Vector3d(bx + 0.5, by + 1.01, bz + 0.5);
    }
}
