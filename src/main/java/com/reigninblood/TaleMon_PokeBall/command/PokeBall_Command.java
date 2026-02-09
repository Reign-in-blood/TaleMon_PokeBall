package com.reigninblood.TaleMon_PokeBall.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.reigninblood.TaleMon_PokeBall.util.TargetingUtil;

import javax.annotation.Nonnull;
import java.util.UUID;

public class PokeBall_Command extends AbstractPlayerCommand {

    public PokeBall_Command() {
        super("target", "talemon.pokeball.commands.target");
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerEntityRef,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        UUID targetUuid = TargetingUtil.findTargetNpcUuid(store, playerEntityRef);

        if (targetUuid != null) {
            context.sendMessage(Message.raw("Target NPC UUID: " + targetUuid));
        } else {
            context.sendMessage(Message.raw("No NPC targeted"));
        }
    }
}
