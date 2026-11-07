package pl.vsake.femboy.acc;

import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.util.Identifier;

/** Rysuje dodatki gracza (warstwa doczepiana do renderera gracza, tak jak zbroja czy peleryna). */
public final class AccessoryFeature extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
	private final AccessoryModel wide = AccessoryModel.create(false);
	private final AccessoryModel slim = AccessoryModel.create(true);

	public AccessoryFeature(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
		super(context);
	}

	@Override
	public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state,
					   float limbAngle, float limbDistance) {
		if (state.invisible || state.spectator) return;
		Look look = ((AccessoryRenderState) state).femboy$getLook();
		if (look == null) return;
		Identifier texture = Accessories.texture(look);
		if (texture == null) return; // tekstura powstanie w jednej z następnych klatek
		boolean thin = state.skinTextures != null && state.skinTextures.model() == PlayerSkinType.SLIM;
		queue.submitModel(thin ? slim : wide, state, matrices, RenderLayers.entityCutoutNoCull(texture), light,
			LivingEntityRenderer.getOverlay(state, 0f), state.outlineColor, null);
	}
}
