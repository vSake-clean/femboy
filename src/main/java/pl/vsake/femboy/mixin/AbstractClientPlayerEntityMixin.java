package pl.vsake.femboy.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pl.vsake.femboy.SkinManager;

/** Skin modelu gracza w świecie (też ręka w pierwszej osobie i podgląd w ekwipunku). */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {
	@ModifyReturnValue(method = "getSkin", at = @At("RETURN"))
	private SkinTextures femboy$swapSkin(SkinTextures original) {
		AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;
		return SkinManager.apply(self.getUuid(), self.getGameProfile().name(), original);
	}
}
