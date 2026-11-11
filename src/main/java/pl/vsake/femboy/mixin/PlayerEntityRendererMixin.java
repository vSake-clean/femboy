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
import pl.vsake.femboy.fx.Leash;
import pl.vsake.femboy.fx.Reactions;

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
		AccessoryRenderState acc = (AccessoryRenderState) state;
		if (entity instanceof AbstractClientPlayerEntity player) {
			try {
				look = Accessories.lookFor(player, state.skinTextures);
				// reakcje uszu/ogona, emotka nad głową, smycz
				Reactions.State r = acc.femboy$react();
				Reactions.fill(player, r);
				r.age = state.age;
				r.pats = pl.vsake.femboy.pat.PatPat.count(player);
				r.hunt = state.isInSneakingPose ? 1f : 0f;
				Leash.fill(player, state, tickProgress);
				// animacje ciała, mruganie / boop, pozy we dwoje (te mogą przestawić rysowaną postać), głowa za kropką lasera
				pl.vsake.femboy.fx.Poses.fill(player, r);
				pl.vsake.femboy.fx.Boop.fill(player, r, look);
				pl.vsake.femboy.fx.Bonds.fill(player, state, r, tickProgress);
				pl.vsake.femboy.fx.Toys.fill(player, state, r, look, tickProgress);
			} catch (Throwable t) {
				Accessories.failed(t);
			}
		} else {
			acc.femboy$react().reset();
		}
		acc.femboy$setLook(look);
	}
}
