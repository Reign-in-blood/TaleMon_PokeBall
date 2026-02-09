package com.reigninblood.TaleMon_PokeBall.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.reigninblood.TaleMon_PokeBall.util.ConsumeUtil;
import com.reigninblood.TaleMon_PokeBall.util.SpawnPosUtil;

import org.bson.BsonDocument;
import org.bson.BsonValue;

import javax.annotation.Nonnull;
import java.util.logging.Level;

public class PokeBallReleaseInteraction extends SimpleInstantInteraction {

    public static final String ID = "talemon:pokeball_release";

    public static final BuilderCodec<PokeBallReleaseInteraction> CODEC =
            BuilderCodec.builder(
                    PokeBallReleaseInteraction.class,
                    PokeBallReleaseInteraction::new,
                    SimpleInstantInteraction.CODEC
            ).documentation("Release captured NPC from pokeball metadata").build();

    public PokeBallReleaseInteraction(String id) { super(id); }
    protected PokeBallReleaseInteraction() { super(ID); }

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {

        
        Ref<EntityStore> playerRef = context.getEntity();
        CommandBuffer<EntityStore> buffer = context.getCommandBuffer();

        Player player = buffer.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Item utilisé = held item (dans ta build tu avais déjà une méthode stable côté ConsumeUtil)
        ItemStack used = ConsumeUtil.getHeldItem(player);
        if (used == null || used == ItemStack.EMPTY) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Lire metadata BSON
        BsonDocument meta = used.getMetadata();
        if (meta == null || !meta.containsKey("CapturedEntity")) {
            HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] RELEASE_FAIL no CapturedEntity metadata");
            context.getState().state = InteractionState.Failed;
            return;
        }

        BsonValue capVal = meta.get("CapturedEntity");
        if (capVal == null || !capVal.isDocument()) {
            HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] RELEASE_FAIL CapturedEntity not a document");
            context.getState().state = InteractionState.Failed;
            return;
        }

        BsonDocument cap = capVal.asDocument();

        int roleIndex = cap.containsKey("RoleIndex") ? cap.getInt32("RoleIndex").getValue() : -1;
        String npcName = cap.containsKey("NpcNameKey") ? cap.getString("NpcNameKey").getValue() : "Unknown";

        // Position visée (déjà OK chez toi)
        Vector3d rawPos = ConsumeUtil.getAimPosition(context);
        Vector3d pos = SpawnPosUtil.makeSafe(rawPos);

        HytaleLogger.getLogger().at(Level.INFO).log(
                "[TaleMon_PokeBall] RELEASE roleIndex=%s npc=%s pos=%s",
                roleIndex, npcName, String.valueOf(pos)
        );

        // Spawn NPC via helper existant chez toi (tu l'avais déjà, donc on garde ta méthode)
        boolean spawned = ConsumeUtil.spawnRoleAt(context, roleIndex, pos);

        if (!spawned) {
            HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] RELEASE_FAIL spawn returned false");
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Consommer la Pokeball_Full (ça marche déjà chez toi)
        boolean consumed = ConsumeUtil.consumeHeldItem(player, used);
        HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] RELEASE consume ok=%s", consumed);

        context.getState().state = InteractionState.Finished;
    }
}
