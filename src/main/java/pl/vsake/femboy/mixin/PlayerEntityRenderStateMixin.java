package pl.vsake.femboy.mixin;

import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import pl.vsake.femboy.acc.AccessoryRenderState;
import pl.vsake.femboy.acc.Look;

/** Wygląd dodatków przenoszony z gracza do rysowania (render state jest wypełniany co klatkę). */
@Mixin(PlayerEntityRenderState.class)
public abstract class PlayerEntityRenderStateMixin implements AccessoryRenderState {
	@Unique
	private Look femboy$look;

	@Override
	public Look femboy$getLook() {
		return femboy$look;
	}

	@Override
	public void femboy$setLook(Look look) {
		femboy$look = look;
	}
}
