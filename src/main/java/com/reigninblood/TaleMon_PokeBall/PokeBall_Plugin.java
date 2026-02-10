package com.reigninblood.TaleMon_PokeBall;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.reigninblood.TaleMon_PokeBall.interactions.PokeBallCaptureInteraction;
import com.reigninblood.TaleMon_PokeBall.interactions.PokeBallReleaseInteraction;

import javax.annotation.Nonnull;

public class PokeBall_Plugin extends JavaPlugin {

    public PokeBall_Plugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        this.getCodecRegistry(Interaction.CODEC)
                .register(PokeBallCaptureInteraction.ID, PokeBallCaptureInteraction.class, PokeBallCaptureInteraction.CODEC);

        this.getCodecRegistry(Interaction.CODEC)
                .register(PokeBallReleaseInteraction.ID, PokeBallReleaseInteraction.class, PokeBallReleaseInteraction.CODEC);
    }
}