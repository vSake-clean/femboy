package pl.vsake.femboy.acc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.text.Text;
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
		boolean thin = state.skinTextures != null && state.skinTextures.model() == PlayerSkinType.SLIM;
		AccessoryModel model = thin ? slim : wide;
		pl.vsake.femboy.fx.Reactions.State react = ((AccessoryRenderState) state).femboy$react();
		if (react.blink > 0.02f && state.squaredDistanceToCamera < LID_RANGE * LID_RANGE) eyelids(model, matrices, queue, light, state, react.blink);
		Look look = ((AccessoryRenderState) state).femboy$getLook();
		if (look == null) return;
		Identifier texture = Accessories.texture(look);
		if (texture == null) return; // tekstura powstanie w jednej z następnych klatek
		queue.submitModel(model, state, matrices, RenderLayers.entityCutoutNoCull(texture), light,
			LivingEntityRenderer.getOverlay(state, 0f), state.outlineColor, null);
		if (look.has(Item.NECK_ID) && state.squaredDistanceToCamera < TAG_RANGE * TAG_RANGE) nameTag(model, matrices, queue, light, state);
	}

	/** Z jak daleka rysujemy powieki – dalej oczu i tak nie widać. */
	private static final double LID_RANGE = 24;

	/**
	 * Mruganie: na oczach (wiersze 4–5 twarzy, kolumny 1–2 i 5–6 – tak mają skiny z moda i większość innych) rysujemy
	 * kawałek policzka z tego samego skina, więc kolor skóry zawsze pasuje; pod spodem ciemna kreska rzęs.
	 */
	private static void eyelids(AccessoryModel model, MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
								PlayerEntityRenderState state, float blink) {
		if (state.skinTextures == null || state.skinTextures.body() == null) return;
		Identifier skin = state.skinTextures.body().texturePath();
		model.setAngles(state); // poza głowy na tę klatkę
		matrices.push();
		model.getRootPart().applyTransform(matrices);
		model.getHead().applyTransform(matrices);
		float k = Math.min(1f, blink);
		int overlay = LivingEntityRenderer.getOverlay(state, 0f);
		queue.submitCustom(matrices, RenderLayers.entityCutoutNoCull(skin), (entry, vc) -> {
			for (int eye = 0; eye < 2; eye++) {
				int col = eye == 0 ? 1 : 5;
				float x0 = (-4 + col) / 16f, x1 = x0 + 2 / 16f, y0 = -4 / 16f, y1 = y0 + 2 * k / 16f, z = -4.03f / 16f;
				float u0 = (8 + col) / 64f, u1 = u0 + 2 / 64f, v0 = 14.1f / 64f, v1 = 14.9f / 64f;
				lid(vc, entry, x0, y0, x1, y1, z, u0, v0, u1, v1, 0xFFFFFFFF, light, overlay);
				// rzęsy: ta sama skóra, mocno przyciemniona
				float l0 = Math.max(y0, y1 - 0.4f / 16f);
				lid(vc, entry, x0, l0, x1, y1, z - 0.002f, u0, v0, u1, v1, 0xFF5A4048, light, overlay);
			}
		});
		matrices.pop();
	}

	private static void lid(net.minecraft.client.render.VertexConsumer vc, MatrixStack.Entry e, float x0, float y0, float x1, float y1, float z,
							float u0, float v0, float u1, float v1, int color, int light, int overlay) {
		vc.vertex(e, x0, y1, z).color(color).texture(u0, v1).overlay(overlay).light(light).normal(e, 0f, 0f, -1f);
		vc.vertex(e, x1, y1, z).color(color).texture(u1, v1).overlay(overlay).light(light).normal(e, 0f, 0f, -1f);
		vc.vertex(e, x1, y0, z).color(color).texture(u1, v0).overlay(overlay).light(light).normal(e, 0f, 0f, -1f);
		vc.vertex(e, x0, y0, z).color(color).texture(u0, v0).overlay(overlay).light(light).normal(e, 0f, 0f, -1f);
	}

	/** Z jak daleka (w blokach) rysujemy nick na identyfikatorze – dalej i tak byłby nieczytelną kreską. */
	private static final double TAG_RANGE = 7;

	/** Nick na blaszce identyfikatora: napis przyklejony do blaszki (buja się razem z nią), dopasowany do jej szerokości. */
	private static void nameTag(AccessoryModel model, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state) {
		String name = ((AccessoryRenderState) state).femboy$react().name;
		ModelPart tag = model.part(Parts.ID_TAG);
		if (name == null || name.isEmpty() || tag == null) return;
		TextRenderer font = MinecraftClient.getInstance().textRenderer;
		int w = font.getWidth(name);
		if (w <= 0) return;
		model.setAngles(state); // poza na tę klatkę (model rysowany z kolejki ułoży się tak samo)
		matrices.push();
		model.getRootPart().applyTransform(matrices);
		model.bodyPart().applyTransform(matrices);
		tag.applyTransform(matrices);
		// środek przedniej ściany blaszki (jednostki elementu: 1/16 bloku × skala), napis tuż przed nią
		matrices.translate(0f, (1.4f + Parts.ID_H / 2f) / 16f, (-1.5f - 0.03f) / 16f);
		long pats = ((AccessoryRenderState) state).femboy$react().pats;
		if (pats > 0) {
			// dwie linijki: nick u góry, pod nim licznik głaskań
			String count = "\u2764 " + pats + "\u00d7";
			int cw = font.getWidth(count);
			float k = Math.min((Parts.ID_W - 1.4f) / w, (Parts.ID_H - 1.4f) / 2f / 8f) / 16f;
			matrices.push();
			matrices.translate(0f, -Parts.ID_H / 4f / 16f + 0.15f / 16f, 0f);
			matrices.scale(k, k, k);
			queue.submitText(matrices, -w / 2f, -3.5f, Text.literal(name).asOrderedText(), false, TextRenderer.TextLayerType.NORMAL, light, 0xFF23202B, 0, 0);
			matrices.pop();
			float kc = Math.min((Parts.ID_W - 1.4f) / cw, (Parts.ID_H - 1.4f) / 2f / 8f) / 16f;
			matrices.translate(0f, Parts.ID_H / 4f / 16f - 0.05f / 16f, 0f);
			matrices.scale(kc, kc, kc);
			queue.submitText(matrices, -cw / 2f, -3.5f, Text.literal(count).asOrderedText(), false, TextRenderer.TextLayerType.NORMAL, light, 0xFFC2356B, 0, 0);
		} else {
			float k = Math.min((Parts.ID_W - 1.4f) / w, (Parts.ID_H - 1.6f) / 8f) / 16f;
			matrices.scale(k, k, k);
			queue.submitText(matrices, -w / 2f, -3.5f, Text.literal(name).asOrderedText(), false, TextRenderer.TextLayerType.NORMAL, light, 0xFF23202B, 0, 0);
		}
		matrices.pop();
	}
}
