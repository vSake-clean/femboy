package pl.vsake.femboy.mixin;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.vsake.femboy.pat.PatRender;

/**
 * Pat pat u ludzików (gracze, zombie…): ściska się sama głowa razem z włosami/czapką (to jej dziecko),
 * od szyi w górę – jak obrazek w GIF-ie petpet. Model.setAngles i tak resetuje skale co klatkę.
 */
@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelMixin {
	@Shadow
	@Final
	public ModelPart head;

	@Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V", at = @At("RETURN"))
	private void femboy$patHead(BipedEntityRenderState state, CallbackInfo ci) {
		PatRender.squishHead(state, head);
	}
}
