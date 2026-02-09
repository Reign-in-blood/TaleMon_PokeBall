package com.reigninblood.TaleMon_PokeBall.util;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.logging.Level;

public class ConsumeUtil {

    // ------------------------------------------------------------
    // 1) Read held item (safe-ish)
    // ------------------------------------------------------------
    @Nullable
    public static ItemStack getHeldItem(Player player) {
        try {
            Inventory inv = player.getInventory();
            if (inv == null) return null;

            // We try common patterns:
            // - getActiveHotbarSlot() + hotbar container
            // - active tools slot
            // We'll do reflection so it survives build differences.
            Object hotbar = tryGetHotbar(inv);
            if (hotbar != null) {
                short slot = tryGetActiveHotbarSlot(inv);
                if (slot >= 0) {
                    ItemStack s = tryGetItemStackFromSlot(hotbar, slot);
                    if (s != null && s != ItemStack.EMPTY) return s;
                }
            }

        } catch (Throwable ignored) {}
        return null;
    }

    // ------------------------------------------------------------
    // 2) Get aim/target position
    // ------------------------------------------------------------
    @Nullable
    public static Vector3d getAimPosition(InteractionContext context) {
        try {
            // Try built-in "hit/target" getters first (if exist in your build)
            for (String name : new String[]{
                    "getHitPosition",
                    "getTargetPosition",
                    "getInteractionPosition",
                    "getPosition",
                    "getImpactPosition"
            }) {
                try {
                    Method m = context.getClass().getMethod(name);
                    Object o = m.invoke(context);
                    if (o instanceof Vector3d v) return v;
                } catch (Throwable ignored) {}
            }

            // Fallback: player pos + forward vector
            CommandBuffer<EntityStore> buffer = context.getCommandBuffer();
            Ref<EntityStore> playerRef = context.getEntity();
            if (buffer == null || playerRef == null) return null;

            Object transform = getTransformComponent(buffer, playerRef);
            if (transform == null) return null;

            Vector3d playerPos = (Vector3d) transform.getClass().getMethod("getPosition").invoke(transform);
            Object rotObj = transform.getClass().getMethod("getRotation").invoke(transform); // Vector3f

            // Optional: HeadRotation
            Object headRotObj = null;
            try {
                Object head = getHeadRotationComponent(buffer, playerRef);
                if (head != null) headRotObj = head.getClass().getMethod("getRotation").invoke(head);
            } catch (Throwable ignored) {}

            Object useRot = (headRotObj != null) ? headRotObj : rotObj;

            float yaw = ((Number) useRot.getClass().getMethod("getYaw").invoke(useRot)).floatValue();
            float pitch = ((Number) useRot.getClass().getMethod("getPitch").invoke(useRot)).floatValue();

            // Vector3f.FORWARD rotated yaw/pitch
            Class<?> v3fClass = Class.forName("com.hypixel.hytale.math.vector.Vector3f");
            Object forward = v3fClass.getField("FORWARD").get(null);
            forward = v3fClass.getConstructor(v3fClass).newInstance(forward);

            forward.getClass().getMethod("rotateY", float.class).invoke(forward, yaw);
            forward.getClass().getMethod("rotateX", float.class).invoke(forward, pitch);
            forward.getClass().getMethod("normalize").invoke(forward);

            float fx = ((Number) forward.getClass().getField("x").get(forward)).floatValue();
            float fy = ((Number) forward.getClass().getField("y").get(forward)).floatValue();
            float fz = ((Number) forward.getClass().getField("z").get(forward)).floatValue();

            // Spawn in front and slightly above to avoid spawning inside ground
            return new Vector3d(
                    playerPos.x + (double) fx * 2.2,
                    playerPos.y + 1.0 + (double) fy * 0.2,
                    playerPos.z + (double) fz * 2.2
            );

        } catch (Throwable t) {
            HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] getAimPosition failed: %s", String.valueOf(t));
            return null;
        }
    }

    // ------------------------------------------------------------
    // 3) Spawn role at position
    // ------------------------------------------------------------
    public static boolean spawnRoleAt(InteractionContext context, int roleIndex, Vector3d pos) {
        try {
            if (pos == null) return false;

            World world = context.getWorld(); // IMPORTANT: this exists in your build (you had it working earlier)
            if (world == null) return false;

            // World spawn API differs by build: we try common patterns via reflection.
            // We try a "spawnRole" / "spawnNpc" style method.
            for (Method m : world.getClass().getMethods()) {
                String n = m.getName().toLowerCase();
                Class<?>[] p = m.getParameterTypes();

                boolean looksLikeSpawn =
                        (n.contains("spawn") && (n.contains("role") || n.contains("npc") || n.contains("entity")));

                if (!looksLikeSpawn) continue;

                // Try signatures:
                // (int roleIndex, Vector3d pos)
                if (p.length == 2 && p[0] == int.class && p[1] == Vector3d.class) {
                    Object r = m.invoke(world, roleIndex, pos);
                    return r != null; // some return Ref, some boolean
                }

                // (int roleIndex, double x, double y, double z)
                if (p.length == 4 && p[0] == int.class && p[1] == double.class && p[2] == double.class && p[3] == double.class) {
                    Object r = m.invoke(world, roleIndex, pos.x, pos.y, pos.z);
                    return r != null;
                }
            }

            HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] RELEASE_FAIL spawn: no world spawn method matched");
            return false;

        } catch (Throwable t) {
            HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] RELEASE_FAIL spawn exception: %s", String.valueOf(t));
            return false;
        }
    }

    // ------------------------------------------------------------
    // 4) Consume held item (remove 1 full ball)
    // ------------------------------------------------------------
    public static boolean consumeHeldItem(Player player, ItemStack used) {
        try {
            if (player == null || used == null) return false;

            Inventory inv = player.getInventory();
            if (inv == null) return false;

            Object hotbar = tryGetHotbar(inv);
            if (hotbar == null) return false;

            short slot = tryGetActiveHotbarSlot(inv);
            if (slot < 0) return false;

            ItemStack current = tryGetItemStackFromSlot(hotbar, slot);
            if (current == null || current == ItemStack.EMPTY) return false;

            // If stack > 1, decrement. If 1, clear slot.
            int qty = current.getQuantity();
            if (qty > 1) {
                ItemStack dec = new ItemStack(current.getItemId(), qty - 1);
                // keep metadata if needed
                dec = dec.withMetadata(current.getMetadata());
                return trySetItemStackInSlot(hotbar, slot, dec);
            } else {
                return trySetItemStackInSlot(hotbar, slot, ItemStack.EMPTY);
            }

        } catch (Throwable t) {
            HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] consumeHeldItem failed: %s", String.valueOf(t));
            return false;
        }
    }

    // ------------------------------------------------------------
    // Reflection helpers: components
    // ------------------------------------------------------------
    private static Object getTransformComponent(CommandBuffer<EntityStore> buffer, Ref<EntityStore> ref) throws Exception {
        Class<?> tcClass = Class.forName("com.hypixel.hytale.server.core.modules.entity.component.TransformComponent");
        Object tcType = tcClass.getMethod("getComponentType").invoke(null);
        return invokeGetComponent(buffer, ref, tcType);
    }

    private static Object getHeadRotationComponent(CommandBuffer<EntityStore> buffer, Ref<EntityStore> ref) throws Exception {
        Class<?> hrClass = Class.forName("com.hypixel.hytale.server.core.modules.entity.component.HeadRotation");
        Object hrType = hrClass.getMethod("getComponentType").invoke(null);
        return invokeGetComponent(buffer, ref, hrType);
    }

    private static Object invokeGetComponent(CommandBuffer<EntityStore> buffer, Ref<EntityStore> ref, Object componentType) {
        try {
            for (Method m : buffer.getClass().getMethods()) {
                if (!m.getName().equals("getComponent")) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length != 2) continue;
                if (!p[0].isAssignableFrom(ref.getClass())) continue;
                if (!p[1].isAssignableFrom(componentType.getClass())) continue;

                try {
                    return m.invoke(buffer, ref, componentType);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // ------------------------------------------------------------
    // Reflection helpers: inventory/hotbar access
    // ------------------------------------------------------------
    private static Object tryGetHotbar(Inventory inv) {
        try {
            for (Method m : inv.getClass().getMethods()) {
                if (m.getName().equals("getHotbar") && m.getParameterCount() == 0) {
                    return m.invoke(inv);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static short tryGetActiveHotbarSlot(Inventory inv) {
        try {
            for (Method m : inv.getClass().getMethods()) {
                if (m.getName().equals("getActiveHotbarSlot") && m.getParameterCount() == 0) {
                    Object o = m.invoke(inv);
                    if (o instanceof Byte b) return (short) (b & 0xFF);
                    if (o instanceof Number n) return n.shortValue();
                }
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    private static ItemStack tryGetItemStackFromSlot(Object hotbar, short slot) {
        try {
            // common: getItemStackInSlot(short)
            for (Method m : hotbar.getClass().getMethods()) {
                if (m.getName().toLowerCase().contains("getitemstackinslot") && m.getParameterCount() == 1) {
                    Object r = m.invoke(hotbar, slot);
                    if (r instanceof ItemStack s) return s;
                }
            }
            // fallback: getItem(short)
            for (Method m : hotbar.getClass().getMethods()) {
                if (m.getName().equals("getItem") && m.getParameterCount() == 1) {
                    Object r = m.invoke(hotbar, slot);
                    if (r instanceof ItemStack s) return s;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean trySetItemStackInSlot(Object hotbar, short slot, ItemStack stack) {
        try {
            // common: replaceItemStackInSlot(short, ItemStack, ItemStack)
            for (Method m : hotbar.getClass().getMethods()) {
                if (m.getName().equals("replaceItemStackInSlot") && m.getParameterCount() == 3) {
                    Class<?>[] p = m.getParameterTypes();
                    if (p[0] == short.class && p[1] == ItemStack.class && p[2] == ItemStack.class) {
                        m.invoke(hotbar, slot, stack, ItemStack.EMPTY);
                        return true;
                    }
                }
            }

            // common: setItemStackInSlot(short, ItemStack)
            for (Method m : hotbar.getClass().getMethods()) {
                if (m.getName().toLowerCase().contains("setitemstackinslot") && m.getParameterCount() == 2) {
                    Class<?>[] p = m.getParameterTypes();
                    if (p[0] == short.class && p[1] == ItemStack.class) {
                        m.invoke(hotbar, slot, stack);
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
