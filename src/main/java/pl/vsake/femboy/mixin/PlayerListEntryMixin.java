package pl.vsake.femboy.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import pl.vsake.femboy.FemboyMod;
import pl.vsake.femboy.SkinManager;

/** Skin z listy graczy – z niego korzysta TAB, a zwykle też sam model gracza. */
@Mixin(PlayerListEntry.class)
public abstract class PlayerListEntryMixin {
	@Shadow
	@Final
	private GameProfile profile;

	@ModifyReturnValue(method = "getSkinTextures", at = @At("RETURN"))
	private SkinTextures femboy$swapSkin(SkinTextures original) {
		if (!FemboyMod.CONFIG.tab) return original;
		return SkinManager.apply(profile.id(), profile.name(), original);
	}
}
