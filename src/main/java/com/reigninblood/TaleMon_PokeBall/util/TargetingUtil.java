package com.reigninblood.TaleMon_PokeBall.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import java.lang.reflect.Method;
import java.util.UUID;

public final class TargetingUtil {

    private static final double MAX_DISTANCE = 6.0;
    private static final double MIN_DOT = 0.7;

    private TargetingUtil() {}

    public static UUID findTargetNpcUuid(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        if (store == null || playerRef == null || !playerRef.isValid()) return null;

        TransformComponent playerTransform =
                store.getComponent(playerRef, TransformComponent.getComponentType());
        if (playerTransform == null) return null;

        Vector3d playerPos = new Vector3d(playerTransform.getPosition());

        Vector3f rotation = new Vector3f(playerTransform.getRotation());
        HeadRotation headRotation =
                store.getComponent(playerRef, HeadRotation.getComponentType());
        Vector3f headRot = headRotation != null ? headRotation.getRotation() : rotation;

        Vector3f forward = new Vector3f(Vector3f.FORWARD);
        forward.rotateY(headRot.getYaw());
        forward.rotateX(headRot.getPitch());
        forward.normalize();

        Vector3d forwardDir = new Vector3d(forward.x, forward.y, forward.z);

        BestCandidate best = new BestCandidate();

        store.forEachChunk(Query.any(), (chunk, buffer) -> {
            int size = chunk.size();
            for (int i = 0; i < size; i++) {
                NPCEntity npc = chunk.getComponent(i, NPCEntity.getComponentType());
                if (npc == null) continue;

                TransformComponent npcTransform =
                        chunk.getComponent(i, TransformComponent.getComponentType());
                if (npcTransform == null) continue;

                Vector3d toNpc = new Vector3d(npcTransform.getPosition()).subtract(playerPos);
                double dist = toNpc.length();
                if (dist <= 0.1 || dist > MAX_DISTANCE) continue;

                Vector3d dir = new Vector3d(toNpc).normalize();
                double dot = forwardDir.dot(dir);
                if (dot < MIN_DOT) continue;

                double score = dot / dist;
                if (score > best.score) {
                    UUID uuid = resolveUuid(npc);
                    if (uuid != null) {
                        best.score = score;
                        best.uuid = uuid;
                    }
                }
            }
        });

        return best.uuid;
    }

    private static UUID resolveUuid(NPCEntity npc) {
        Object value = call(npc, "getUniqueId");
        if (value == null) value = call(npc, "getEntityUuid");
        if (value == null) value = call(npc, "getId");
        if (value == null) value = call(npc, "getUuid"); // legacy fallback

        if (value instanceof UUID uuid) return uuid;
        if (value instanceof String s) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Object call(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static class BestCandidate {
        double score = 0;
        UUID uuid = null;
    }
}
