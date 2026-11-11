package pl.vsake.femboy.mixin;

import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.vsake.femboy.fx.Bonds;

/**
 * Noszony gracz jest rysowany na rękach / barkach niosącego, a naprawdę stoi obok. Gra (i Sodium) sprawdza, czy postać
 * jest w kadrze, według prawdziwej pozycji, więc noszony znikał z rąk, gdy jego prawdziwe miejsce wypadało poza ekran.
 */
@Mixin(EntityRenderManager.class)
public abstract class EntityRenderManagerMixin {
	@Inject(method = "shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Frustum;DDD)Z", at = @At("HEAD"), cancellable = true)
	private void femboy$carriedAlwaysVisible(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (Bonds.shownElsewhere(entity)) cir.setReturnValue(true);
	}
}
