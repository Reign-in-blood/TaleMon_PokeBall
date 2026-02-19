package com.reigninblood.TaleMon_PokeBall.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockFace;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.metadata.CapturedNPCMetadata;
import com.reigninblood.TaleMon_PokeBall.util.ConsumeUtil;
import com.reigninblood.TaleMon_PokeBall.util.MetaReadUtil;
import org.bson.BsonDocument;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class PokeBallReleaseInteraction extends SimpleInstantInteraction {
    public static final String ID = "talemon:pokeball_release";
    public static final BuilderCodec<PokeBallReleaseInteraction> CODEC =
            BuilderCodec.builder(PokeBallReleaseInteraction.class, PokeBallReleaseInteraction::new, SimpleInstantInteraction.CODEC)
                    .documentation("Release a captured NPC from a PokéBall (consumes the ball).")
                    .build();

    private static final long RELEASE_DELAY_MS = 1000L;

    public PokeBallReleaseInteraction(String id) {
        super(id);
    }

    protected PokeBallReleaseInteraction() {
        super(ID);
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        final CommandBuffer<EntityStore> buffer = context.getCommandBuffer();
        if (buffer == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        final ItemStack held = context.getHeldItem();
        if (held == null || held.isEmpty()) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        final Ref<EntityStore> playerRef = context.getEntity();
        final Entity ent = EntityUtils.getEntity(playerRef, buffer);
        if (!(ent instanceof LivingEntity living) || !(living instanceof Player player)) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        final CapturedData capturedData = readCapturedData(held);
        if (capturedData == null || capturedData.roleIndex() == null || capturedData.roleIndex() < 0) {
            HytaleLogger.getLogger().at(Level.INFO).log(
                    "[TaleMon_PokeBall] RELEASE_FAIL invalid captured metadata roleIndex=%s roleName=%s",
                    capturedData == null ? "null" : String.valueOf(capturedData.roleIndex()),
                    capturedData == null ? "null" : String.valueOf(capturedData.roleName())
            );
            context.getState().state = InteractionState.Failed;
            return;
        }

        final BlockPosition target = context.getTargetBlock();
        if (target == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        Vector3d spawnPos = new Vector3d((double) target.x + 0.5D, (double) target.y + 0.5D, (double) target.z + 0.5D);
        BlockFace face = null;
        if (context.getClientState() != null) {
            face = BlockFace.fromProtocolFace(context.getClientState().blockFace);
        }

        if (face != null) {
            Vector3d dir = new Vector3d(face.getDirection());
            spawnPos.add(dir);
            spawnPos.y = (double) target.y + 1.01D;
        } else {
            spawnPos.y = (double) target.y + 1.01D;
        }

        spawnPos.x += 0.01D;
        spawnPos.z += 0.01D;

        final Vector3d finalSpawnPos = spawnPos;
        final int roleIndex = capturedData.roleIndex();
        final String roleName = capturedData.roleName();
        final NPCPlugin npcPlugin = NPCPlugin.get();

        HytaleLogger.getLogger().at(Level.INFO).log(
                "[TaleMon_PokeBall] RELEASE_USE delay=%sms roleIndex=%s roleName=%s pos=%s face=%s",
                RELEASE_DELAY_MS, roleIndex, roleName, finalSpawnPos, face == null ? "null" : face.name()
        );

        CompletableFuture.runAsync(() -> buffer.run(store -> {
            try {
                npcPlugin.spawnEntity(store, roleIndex, finalSpawnPos, Vector3f.ZERO, (Model) null, null);
            } catch (Throwable t) {
                HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] RELEASE_FAIL spawn exception=%s", String.valueOf(t));
                return;
            }

            boolean consumed = false;
            try {
                consumed = ConsumeUtil.consumeActiveHotbarItem(player, held);
            } catch (Throwable t) {
                HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] RELEASE_FAIL consume exception=%s", String.valueOf(t));
            }

            HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] RELEASE_OK spawned=true consume=%s", consumed);
        }), CompletableFuture.delayedExecutor(RELEASE_DELAY_MS, TimeUnit.MILLISECONDS));

        context.getState().state = InteractionState.Finished;
    }

    private static CapturedData readCapturedData(ItemStack held) {
        CapturedNPCMetadata typed = null;
        try {
            typed = (CapturedNPCMetadata) held.getFromMetadataOrNull("CapturedEntity", CapturedNPCMetadata.CODEC);
        } catch (Throwable ignored) {
        }

        Integer roleIndex = extractRoleIndex(typed);
        String roleName = extractRoleName(typed);

        BsonDocument fullMeta = tryGetMetadata(held);
        MetaReadUtil.Captured raw = MetaReadUtil.readCapturedEntity(fullMeta);

        if (roleIndex == null && raw != null) {
            roleIndex = raw.roleIndex();
        }
        if ((roleName == null || roleName.isEmpty()) && raw != null) {
            roleName = raw.npcNameKey();
        }

        if (roleIndex == null && roleName == null) {
            return null;
        }
        return new CapturedData(roleIndex, roleName);
    }

    private static BsonDocument tryGetMetadata(ItemStack held) {
        try {
            Method getMetadata = held.getClass().getMethod("getMetadata");
            Object value = getMetadata.invoke(held);
            return value instanceof BsonDocument doc ? doc : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Integer extractRoleIndex(CapturedNPCMetadata typed) {
        if (typed == null) return null;

        Object extracted = invokeNoArg(typed, "getRoleIndex"); // old API
        if (extracted == null) extracted = invokeNoArg(typed, "getNpcRoleIndex"); // possible U3 rename
        if (extracted instanceof Number n) return n.intValue();
        return null;
    }

    private static String extractRoleName(CapturedNPCMetadata typed) {
        if (typed == null) return null;

        Object extracted = invokeNoArg(typed, "getRoleName");
        if (extracted == null) extracted = invokeNoArg(typed, "getNpcNameKey");
        if (extracted == null) extracted = invokeNoArg(typed, "getRoleIdentifier");
        return extracted instanceof String s ? s : null;
    }

    private static Object invokeNoArg(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private record CapturedData(Integer roleIndex, String roleName) {
    }
}
