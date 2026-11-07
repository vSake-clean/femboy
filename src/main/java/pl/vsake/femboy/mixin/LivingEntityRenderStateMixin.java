package pl.vsake.femboy.mixin;

import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import pl.vsake.femboy.pat.PatRenderState;

/** Czas animacji pat pat przenoszony z encji do rysowania (render state jest tworzony co klatkę). */
@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements PatRenderState {
	@Unique
	private float femboy$patTime = -1f;

	@Override
	public float femboy$getPatTime() {
		return femboy$patTime;
	}

	@Override
	public void femboy$setPatTime(float time) {
		femboy$patTime = time;
	}
}
