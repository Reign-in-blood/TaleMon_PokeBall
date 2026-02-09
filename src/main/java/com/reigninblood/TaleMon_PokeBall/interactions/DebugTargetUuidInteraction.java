package com.reigninblood.TaleMon_PokeBall.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.logging.Level;

public class DebugTargetUuidInteraction extends SimpleInstantInteraction {

    public static final String ID = "talemon:debug_target_uuid";

    public static final BuilderCodec<DebugTargetUuidInteraction> CODEC =
            BuilderCodec.builder(DebugTargetUuidInteraction.class, DebugTargetUuidInteraction::new, SimpleInstantInteraction.CODEC)
                    .documentation("Debug: dumps Role getters to find a stable role id.")
                    .build();

    // Pour éviter spam: dump une seule fois par lancement serveur
    private static boolean DUMPED_ROLE_METHODS = false;

    public DebugTargetUuidInteraction(String id) {
        super(id);
    }

    protected DebugTargetUuidInteraction() {
        super(ID);
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {

        Ref<EntityStore> playerEntityRef = context.getEntity();
        Ref<EntityStore> targetRef = context.getTargetEntity();
        CommandBuffer<EntityStore> buffer = context.getCommandBuffer();

        Player player = buffer.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        if (targetRef == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        NPCEntity npc = buffer.getComponent(targetRef, NPCEntity.getComponentType());
        if (npc == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        Object role = npc.getRole();
        String roleName = safeInvokeString(npc, "getRoleName"); // chez toi ça marche (Bulbasaur)
        if (roleName == null) roleName = "null";

        HytaleLogger.getLogger().at(Level.INFO).log(
                "[TaleMon_PokeBall] target=%s roleName=%s roleClass=%s",
                targetRef,
                roleName,
                role != null ? role.getClass().getName() : "null"
        );

        // Dump des getters du Role (une seule fois)
        if (role != null && !DUMPED_ROLE_METHODS) {
            DUMPED_ROLE_METHODS = true;
            dumpNoArgGetters(role);
        }

        player.sendMessage(Message.raw("Logged role info + (once) Role getters to console."));
    }

    private static void dumpNoArgGetters(Object role) {
        Method[] methods = role.getClass().getMethods();

        Arrays.stream(methods)
                .filter(m -> m.getParameterCount() == 0)
                .filter(m -> !m.getName().equals("getClass"))
                .sorted(Comparator.comparing(Method::getName))
                .forEach(m -> {
                    String name = m.getName();
                    // On se concentre sur les getters plausibles
                    if (!(name.startsWith("get") || name.startsWith("is"))) return;

                    String ret = m.getReturnType().getName();
                    String value;
                    try {
                        Object v = m.invoke(role);
                        value = String.valueOf(v);
                    } catch (Throwable t) {
                        value = "<error:" + t.getClass().getSimpleName() + ">";
                    }

                    // Log une ligne par méthode
                    HytaleLogger.getLogger().at(Level.INFO).log(
                            "[TaleMon_PokeBall] RoleGetter %s -> %s = %s",
                            name, ret, value
                    );
                });
    }

    private static String safeInvokeString(Object obj, String methodName) {
        if (obj == null) return null;
        try {
            Method m = obj.getClass().getMethod(methodName);
            Object v = m.invoke(obj);
            return v != null ? String.valueOf(v) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
