package io.github.flemmli97.simplequests.fabric.mixin;

import io.github.flemmli97.simplequests.datapack.QuestsManager;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.world.flag.FeatureFlagSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ReloadableServerResources.class)
public abstract class ReloadableServerResourcesMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onNewServerReload(LayeredRegistryAccess layeredRegistryAccess, HolderLookup.Provider provider, FeatureFlagSet featureFlagSet, Commands.CommandSelection commandSelection, List list, int i, CallbackInfo ci) {
        QuestsManager.INSTANCE = new QuestsManager(layeredRegistryAccess.compositeAccess());
    }
}
