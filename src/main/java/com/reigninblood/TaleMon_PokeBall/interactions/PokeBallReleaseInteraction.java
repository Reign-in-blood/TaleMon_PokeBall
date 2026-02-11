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

import java.util.logging.Level;
import javax.annotation.Nonnull;

public class PokeBallReleaseInteraction extends SimpleInstantInteraction {
    public static final String ID = "talemon:pokeball_release";
    public static final BuilderCodec<PokeBallReleaseInteraction> CODEC;

    private static final boolean SPAWN_COMPANION_ROLE = true;

    public PokeBallReleaseInteraction(String id) { super(id); }
    protected PokeBallReleaseInteraction() { super(ID); }

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

        Vector3d spawnPos = new Vector3d((double) target.x + 0.5D, (double) target.y + 0.5D, (double) target.z + 0.5D);

        BlockFace face = null;
        if (context.getClientState() != null) {
            face = BlockFace.fromProtocolFace(context.getClientState().blockFace);
        }

        if (face != null) {
            Vector3d dir = new Vector3d(face.getDirection());
            spawnPos.add(dir);
        }

        final Vector3d finalSpawnPos = SpawnPosUtil.makeSafe(spawnPos);

        final int wildRoleIndex = meta.getRoleIndex();
        final String npcNameKey = meta.getNpcNameKey();
        final NPCPlugin npcPlugin = NPCPlugin.get();
        final String faceStr = (face == null) ? "null" : face.name();

        final String wildRoleName = safeGetRoleName(npcPlugin, wildRoleIndex);

        buffer.run((Store<EntityStore> store) -> {
            int spawnRoleIndex = wildRoleIndex;
            int companionIndex = -1;

            if (SPAWN_COMPANION_ROLE) {
                companionIndex = resolveCompanionRoleIndex(npcPlugin, wildRoleName, npcNameKey);
                if (companionIndex >= 0) spawnRoleIndex = companionIndex;
            }

            HytaleLogger.getLogger().at(Level.INFO).log(
                    "[TaleMon_PokeBall] RELEASE_USE wildRoleIndex=%s wildRoleName=%s companionIndex=%s spawnRoleIndex=%s npc=%s pos=%s face=%s",
                    wildRoleIndex,
                    wildRoleName == null ? "null" : wildRoleName,
                    companionIndex < 0 ? "none" : String.valueOf(companionIndex),
                    spawnRoleIndex,
                    npcNameKey,
                    finalSpawnPos,
                    faceStr
            );

            boolean spawned = spawnNpcSafe(store, npcPlugin, spawnRoleIndex, finalSpawnPos);
            if (!spawned) {
                HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] RELEASE_FAIL spawn failed");
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

        context.getState().state = InteractionState.Finished;
    }

    private static String safeGetRoleName(NPCPlugin npcPlugin, int roleIndex) {
        try {
            return npcPlugin.getName(roleIndex);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean spawnNpcSafe(Store<EntityStore> store, NPCPlugin npcPlugin, int roleIndex, Vector3d pos) {
        try {
            npcPlugin.spawnEntity(store, roleIndex, pos, Vector3f.ZERO, (Model) null, (TriConsumer) null);
            return true;
        } catch (Throwable t) {
            HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] RELEASE_FAIL spawn exception=%s", String.valueOf(t));
            return false;
        }
    }

    /**
     * ✅ Resolver robuste: on ne dépend plus de hasRoleName().
     * On essaie getIndex() sur plusieurs formes (nom simple, nom+suffix, chemin, chemin+.json).
     */
    private static int resolveCompanionRoleIndex(NPCPlugin npcPlugin, String wildRoleName, String npcNameKey) {
        String baseA = toBaseName(wildRoleName);
        String baseB = toBaseName(npcNameKey);

        String[] bases = new String[] { wildRoleName, baseA, baseB };
        String[] suffixes = new String[] { "_Companion", "_Summoned" };

        String[] prefixes = new String[] {
                "",
                "Pokemon/",
                "Server/NPC/Roles/Pokemon/",
                "Server/NPC/Roles/"
        };

        for (String b : bases) {
            if (b == null || b.isEmpty()) continue;

            String last = toBaseName(b);

            for (String suf : suffixes) {
                // (1) forme simple
                Integer idx = tryGetIndex(npcPlugin, last + suf);
                if (idx != null) return idx;

                // (2) avec prefixes communs
                for (String p : prefixes) {
                    idx = tryGetIndex(npcPlugin, p + last + suf);
                    if (idx != null) return idx;

                    // (3) parfois il faut l'extension .json
                    idx = tryGetIndex(npcPlugin, p + last + suf + ".json");
                    if (idx != null) return idx;
                }
            }
        }

        return -1;
    }

    private static Integer tryGetIndex(NPCPlugin npcPlugin, String roleName) {
        if (roleName == null || roleName.isEmpty()) return null;
        try {
            return npcPlugin.getIndex(roleName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String toBaseName(String nameOrPath) {
        if (nameOrPath == null) return null;
        String s = nameOrPath;

        int slash = s.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < s.length()) s = s.substring(slash + 1);

        if (s.endsWith(".json")) s = s.substring(0, s.length() - 5);
        if (s.endsWith("_Companion")) s = s.substring(0, s.length() - "_Companion".length());
        if (s.endsWith("_Summoned")) s = s.substring(0, s.length() - "_Summoned".length());

        return s;
    }

    static {
        CODEC = ((Builder) BuilderCodec
                .builder(PokeBallReleaseInteraction.class, PokeBallReleaseInteraction::new, SimpleInstantInteraction.CODEC)
                .documentation("Release a captured NPC from a Pok\u00e9Ball (consumes the ball)."))
                .build();
    }
}
