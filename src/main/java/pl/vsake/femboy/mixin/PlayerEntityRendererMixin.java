package pl.vsake.femboy.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.vsake.femboy.FemboyMod;
import pl.vsake.femboy.SkinManager;
import pl.vsake.femboy.acc.Accessories;
import pl.vsake.femboy.acc.AccessoryRenderState;
import pl.vsake.femboy.acc.Look;

/**
 * Nasze skiny mają włosy 3D, kaptur i spódniczkę na zewnętrznych warstwach –
 * pokazujemy je nawet u graczy, którzy wyłączyli sobie warstwy w ustawieniach skina.
 * Tu też gracz dostaje swoje dodatki (uszy, ogon…) na tę klatkę.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
	@Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("TAIL"))
	private void femboy$forceLayers(PlayerLikeEntity entity, PlayerEntityRenderState state, float tickProgress, CallbackInfo ci) {
		if (!FemboyMod.CONFIG.layers || !SkinManager.isOurs(state.skinTextures)) return;
		state.hatVisible = true;
		state.jacketVisible = true;
		state.leftPantsLegVisible = true;
		state.rightPantsLegVisible = true;
		state.leftSleeveVisible = true;
		state.rightSleeveVisible = true;
	}

	@Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("TAIL"))
	private void femboy$accessories(PlayerLikeEntity entity, PlayerEntityRenderState state, float tickProgress, CallbackInfo ci) {
		Look look = null;
		if (entity instanceof AbstractClientPlayerEntity player) {
			try {
				look = Accessories.lookFor(player, state.skinTextures);
			} catch (Throwable t) {
				Accessories.failed(t);
			}
		}
		((AccessoryRenderState) state).femboy$setLook(look);
	}
}
