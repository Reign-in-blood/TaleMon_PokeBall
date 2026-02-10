package com.reigninblood.TaleMon_PokeBall.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec.Builder;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.function.consumer.TriConsumer;
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
import com.reigninblood.TaleMon_PokeBall.util.SpawnPosUtil;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.annotation.Nonnull;

public class PokeBallReleaseInteraction extends SimpleInstantInteraction {
    public static final String ID = "talemon:pokeball_release";
    public static final BuilderCodec<PokeBallReleaseInteraction> CODEC;

    // Delay demandé (ms)
    private static final long RELEASE_DELAY_MS = 1000L;

    /**
     * true  = comportement actuel (async + delay)
     * false = mode test (pas d'async, pas de delay)
     *
     * IMPORTANT: teste d'abord false pour savoir si l'async est la cause.
     */
    private static final boolean USE_ASYNC_DELAY_RELEASE = false;

    public PokeBallReleaseInteraction(String id) {
        super(id);
    }

    protected PokeBallReleaseInteraction() {
        super("talemon:pokeball_release");
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
        if (!(ent instanceof LivingEntity)) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        final LivingEntity living = (LivingEntity) ent;
        if (!(living instanceof Player)) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        final Player player = (Player) living;

        final CapturedNPCMetadata meta =
                (CapturedNPCMetadata) held.getFromMetadataOrNull("CapturedEntity", CapturedNPCMetadata.CODEC);
        if (meta == null) {
            HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] RELEASE_FAIL no CapturedEntity metadata");
            context.getState().state = InteractionState.Failed;
            return;
        }

        final BlockPosition target = context.getTargetBlock();
        if (target == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Base spawn position = centre du bloc visé
        Vector3d spawnPos = new Vector3d((double) target.x + 0.5D, (double) target.y + 0.5D, (double) target.z + 0.5D);

        // Face cliquée (optionnel)
        BlockFace face = null;
        if (context.getClientState() != null) {
            face = BlockFace.fromProtocolFace(context.getClientState().blockFace);
        }

        // Si on clique une face, on pousse d'un bloc dans cette direction
        if (face != null) {
            Vector3d dir = new Vector3d(face.getDirection());
            spawnPos.add(dir);
        }

        // Spawn safe (évite l'entité dans le sol)
        final Vector3d finalSpawnPos = SpawnPosUtil.makeSafe(spawnPos);

        final int roleIndex = meta.getRoleIndex();
        final String nameKey = meta.getNpcNameKey();
        final NPCPlugin npcPlugin = NPCPlugin.get();

        HytaleLogger.getLogger().at(Level.INFO).log(
                "[TaleMon_PokeBall] RELEASE_USE delay=%sms roleIndex=%s npc=%s pos=%s face=%s",
                RELEASE_DELAY_MS, roleIndex, nameKey, finalSpawnPos, face == null ? "null" : face.name()
        );

        // Code serveur (spawn + consume)
        Runnable doRelease = () -> buffer.run((store) -> {
            boolean spawned = spawnNpcSafe(store, npcPlugin, roleIndex, nameKey, finalSpawnPos);
            if (!spawned) {
                HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] RELEASE_FAIL spawnNpcSafe returned false");
                return;
            }

            boolean consumed = false;
            try {
                consumed = ConsumeUtil.consumeActiveHotbarItem(player, held);
            } catch (Throwable t) {
                HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] RELEASE_FAIL consume exception=%s", String.valueOf(t));
            }

            HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] RELEASE_OK spawned=true consume ok=%s", consumed);
        });

        if (USE_ASYNC_DELAY_RELEASE) {
            CompletableFuture.runAsync(doRelease, CompletableFuture.delayedExecutor(RELEASE_DELAY_MS, TimeUnit.MILLISECONDS));
        } else {
            doRelease.run();
        }

        context.getState().state = InteractionState.Finished;
    }

    /**
     * Tente plusieurs signatures de spawn (selon versions/API), puis fallback sur la signature simple.
     * Objectif: éviter un NPC "incomplet" (motionKind null / spawnConfig invalid).
     */
    private static boolean spawnNpcSafe(
            Store<EntityStore> store,
            NPCPlugin npcPlugin,
            int roleIndex,
            String nameKey,
            Vector3d pos
    ) {
        // 1) Essai: spawnEntity(store, roleIndex, nameKey, pos, vel, model, triConsumer)
        try {
            java.lang.reflect.Method m = npcPlugin.getClass().getMethod(
                    "spawnEntity",
                    Store.class,
                    int.class,
                    String.class,
                    Vector3d.class,
                    Vector3f.class,
                    Model.class,
                    TriConsumer.class
            );

            m.invoke(npcPlugin, store, roleIndex, nameKey, pos, Vector3f.ZERO, null, null);
            HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] RELEASE_SPAWN used signature: (Store,int,String,Vector3d,Vector3f,Model,TriConsumer)");
            return true;
        } catch (NoSuchMethodException ignored) {
            // signature non disponible
        } catch (Throwable t) {
            HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] RELEASE_FAIL spawn(sig:role+name) ex=%s", String.valueOf(t));
        }

        // 2) Essai: spawnEntity(store, roleIndex, pos, vel, model, triConsumer, spawnConfigIndex)
        try {
            java.lang.reflect.Method m = npcPlugin.getClass().getMethod(
                    "spawnEntity",
                    Store.class,
                    int.class,
                    Vector3d.class,
                    Vector3f.class,
                    Model.class,
                    TriConsumer.class,
                    int.class
            );

            m.invoke(npcPlugin, store, roleIndex, pos, Vector3f.ZERO, null, null, 0);
            HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] RELEASE_SPAWN used signature: (Store,int,Vector3d,Vector3f,Model,TriConsumer,int)");
            return true;
        } catch (NoSuchMethodException ignored) {
            // signature non disponible
        } catch (Throwable t) {
            HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] RELEASE_FAIL spawn(sig:+spawnCfg) ex=%s", String.valueOf(t));
        }

        // 3) Fallback: signature simple
        try {
            npcPlugin.spawnEntity(store, roleIndex, pos, Vector3f.ZERO, (Model) null, (TriConsumer) null);
            HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] RELEASE_SPAWN used fallback signature: (Store,int,Vector3d,Vector3f,Model,TriConsumer)");
            return true;
        } catch (Throwable t) {
            HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] RELEASE_FAIL spawn(fallback) ex=%s", String.valueOf(t));
            return false;
        }
    }

    static {
        CODEC = ((Builder) BuilderCodec
                .builder(PokeBallReleaseInteraction.class, PokeBallReleaseInteraction::new, SimpleInstantInteraction.CODEC)
                .documentation("Release a captured NPC from a Pok\u00e9Ball (consumes the ball)."))
                .build();
    }
}
