package pl.vsake.femboy.mixin;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.vsake.femboy.fx.EmoteRender;
import pl.vsake.femboy.pat.PatPat;
import pl.vsake.femboy.pat.PatRender;
import pl.vsake.femboy.pat.PatRenderState;

/** Pat pat: kto jest głaskany (stan), ściśnięcie mobów bez ludzkiej głowy i ręka nad głową. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void femboy$patState(LivingEntity entity, LivingEntityRenderState state, float tickProgress, CallbackInfo ci) {
		((PatRenderState) state).femboy$setPatTime(PatPat.timeFor(entity));
	}

	// zaraz po skali renderera, przed przesunięciem modelu: punkt odniesienia to stopy
	@Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;scale(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;)V",
			shift = At.Shift.AFTER))
	private void femboy$patSquish(LivingEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue,
								  CameraRenderState camera, CallbackInfo ci) {
		if (!femboy$biped()) PatRender.squishBody(state, matrices); // ludzikom ściska się sama głowa (BipedEntityModelMixin)
	}

	@Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
		at = @At("TAIL"))
	private void femboy$patHand(LivingEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue,
								CameraRenderState camera, CallbackInfo ci) {
		PatRender.hand(state, matrices, queue, camera, femboy$biped());
		EmoteRender.draw(state, matrices, queue, camera);
	}

	@Unique
	private boolean femboy$biped() {
		return ((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel() instanceof BipedEntityModel<?>;
	}
}
