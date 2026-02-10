package com.reigninblood.TaleMon_PokeBall.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import com.reigninblood.TaleMon_PokeBall.util.DespawnUtil;
import com.reigninblood.TaleMon_PokeBall.util.GiveUtil;
import com.reigninblood.TaleMon_PokeBall.util.MetaUtil;
import com.reigninblood.TaleMon_PokeBall.util.PokemonList;

import javax.annotation.Nonnull;
import java.util.logging.Level;

public class PokeBallCaptureInteraction extends SimpleInstantInteraction {

    public static final String ID = "talemon:pokeball_capture";

    public static final BuilderCodec<PokeBallCaptureInteraction> CODEC =
            BuilderCodec.builder(
                    PokeBallCaptureInteraction.class,
                    PokeBallCaptureInteraction::new,
                    SimpleInstantInteraction.CODEC
            ).documentation("Capture a NPC into a PokéBall (Pokemon only)").build();

    public PokeBallCaptureInteraction(String id) { super(id); }
    protected PokeBallCaptureInteraction() { super(ID); }

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {

        Ref<EntityStore> playerRef = context.getEntity();
        Ref<EntityStore> targetRef = context.getTargetEntity();
        CommandBuffer<EntityStore> buffer = context.getCommandBuffer();

        if (buffer == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        Player player = buffer.getComponent(playerRef, Player.getComponentType());
        if (player == null || targetRef == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        NPCEntity npc = buffer.getComponent(targetRef, NPCEntity.getComponentType());
        if (npc == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Role info
        Object role = npc.getRole();
        int roleIndex = -1;
        String roleName = "Unknown";

        try { roleIndex = (int) role.getClass().getMethod("getRoleIndex").invoke(role); } catch (Throwable ignored) {}
        try { roleName = (String) role.getClass().getMethod("getRoleName").invoke(role); } catch (Throwable ignored) {}

        // ✅ Pokemon-only filter
        if (!PokemonList.isPokemon(roleName)) {
            HytaleLogger.getLogger().at(Level.INFO).log(
                    "[TaleMon_PokeBall] CAPTURE_BLOCKED not a Pokemon: roleName=%s roleIndex=%s",
                    roleName, roleIndex
            );
            context.getState().state = InteractionState.Failed;
            return;
        }

        HytaleLogger.getLogger().at(Level.INFO).log(
                "[TaleMon_PokeBall] CAPTURE_OK roleName=%s roleIndex=%s target=%s",
                roleName, roleIndex, targetRef
        );

        // ✅ NEW: choose the full-ball item ID based on the captured pokemon
        String pokemonBallItemId = makePokemonFullBallItemId(roleName); // ex: PokeBall_Bulbasaur
        HytaleLogger.getLogger().at(Level.INFO).log(
                "[TaleMon_PokeBall] CAPTURE giveItemId=%s (fallback=PokeBall_Full)",
                pokemonBallItemId
        );

        // Create custom full ball + meta
        ItemStack fullBall = new ItemStack(pokemonBallItemId, 1);
        ItemStack fullBallWithMeta = MetaUtil.withCapturedEntity(fullBall, roleIndex, roleName, null, null);

        boolean given = GiveUtil.giveToStorage(player, fullBallWithMeta);

        // ✅ Fallback if the custom item doesn't exist or can't be given
        if (!given) {
            HytaleLogger.getLogger().at(Level.INFO).log(
                    "[TaleMon_PokeBall] GIVE failed for %s -> fallback to PokeBall_Full",
                    pokemonBallItemId
            );

            ItemStack fallbackBall = new ItemStack("PokeBall_Full", 1);
            ItemStack fallbackWithMeta = MetaUtil.withCapturedEntity(fallbackBall, roleIndex, roleName, null, null);
            given = GiveUtil.giveToStorage(player, fallbackWithMeta);
        }

        HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] GIVE fullBallWithMeta ok=%s", given);

        if (!given) {
            // If we can't give the item, don't despawn the NPC (avoid losing it)
            context.getState().state = InteractionState.Failed;
            return;
        }

        boolean despawnOk = DespawnUtil.forceDespawn(buffer, targetRef);
        HytaleLogger.getLogger().at(Level.INFO).log("[TaleMon_PokeBall] DESPAWN result=%s", despawnOk);

        context.getState().state = InteractionState.Finished;
    }

    // Build item id: "PokeBall_<RoleName>" with safe characters
    private static String makePokemonFullBallItemId(String roleName) {
        if (roleName == null || roleName.isEmpty()) return "PokeBall_Full";

        // Keep only letters/numbers/_ and replace others with _
        String safe = roleName.replaceAll("[^A-Za-z0-9_]", "_");

        // Optional: avoid double underscores
        safe = safe.replaceAll("_+", "_");

        return "PokeBall_" + safe;
    }
}
