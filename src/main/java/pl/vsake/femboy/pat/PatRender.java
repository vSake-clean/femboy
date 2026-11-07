package pl.vsake.femboy.pat;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import pl.vsake.femboy.FemboyMod;

/**
 * Rysowanie pat pat. Geometria jak w generatorze petpet (128x128): "obrazek" zajmuje 80% płótna,
 * stoi przy prawej krawędzi, jego góra jest 12% płótna pod górą płótna, a w najmocniejszym
 * klepnięciu traci 31% wysokości (dół zostaje). U ludzików obrazkiem jest głowa, u reszty – góra ciała.
 */
public final class PatRender {
	private PatRender() {
	}

	private static final Identifier HAND = Identifier.of(FemboyMod.ID, "textures/petpet/hand.png");
	private static final int HAND_FRAMES = 5;

	/** Ściśnięcie z GIF-a: 0 → 1 (klatka 5) → 0. */
	public static float squeeze(float t) {
		return Math.max(0f, 1f - Math.abs(t - 5f) / 5f);
	}

	private static float time(LivingEntityRenderState state) {
		return ((PatRenderState) state).femboy$getPatTime();
	}

	/** Bok "obrazka" w blokach: u ludzików głowa (z warstwą włosów), u reszty szerokość ciała. */
	private static float avatar(LivingEntityRenderState state, boolean biped) {
		return Math.max(0.25f, state.width * (biped ? 0.85f : 1f));
	}

	/** Głowa jak obrazek w petpet: niższa o 31%, szersza o 12,5%, szyja zostaje na miejscu. */
	public static void squishHead(LivingEntityRenderState state, ModelPart head) {
		float t = time(state);
		if (t < 0f) return;
		float s = squeeze(t);
		float w = 1f + 0.125f * s;
		head.xScale *= w;
		head.yScale *= 1f - 0.3125f * s;
		head.zScale *= w;
	}

	/** Moby bez ludzkiej głowy: całe ciało ściska się od stóp dokładnie o tyle, ile opada ręka. */
	public static void squishBody(LivingEntityRenderState state, MatrixStack matrices) {
		float t = time(state);
		if (t < 0f || state.height <= 0f) return;
		float s = squeeze(t);
		float drop = 0.25f * (avatar(state, false) / 0.8f) * s;
		float sy = Math.max(0.5f, 1f - drop / state.height);
		float sx = 1f + 0.08f * s;
		matrices.scale(sx, sy, sx);
	}

	/** Ręka z GIF-a jako płaski obrazek zwrócony do kamery, nad głową i trochę przed nią. */
	public static void hand(LivingEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue,
							CameraRenderState camera, boolean biped) {
		float t = time(state);
		if (t < 0f || camera == null || camera.orientation == null) return;
		int frame = Math.min(HAND_FRAMES - 1, Math.max(0, (int) t / 2));
		float a = avatar(state, biped);
		float c = a / 0.8f;                                   // całe płótno GIF-a
		float top = state.height * (biped ? 1.04f : 1f);      // ludzik: głowa wystaje trochę ponad hitbox
		float x1 = a / 2f, x0 = x1 - c;                       // prawa krawędź płótna = prawa krawędź obrazka
		float y1 = 0.12f * c, y0 = y1 - c;
		float u0 = frame / (float) HAND_FRAMES, u1 = (frame + 1) / (float) HAND_FRAMES;
		matrices.push();
		matrices.translate(0f, top, 0f);
		matrices.multiply(camera.orientation);
		matrices.translate(0f, 0f, state.width * 0.5f + 0.1f); // przed głową, od strony patrzącego
		queue.submitCustom(matrices, RenderLayers.entityCutoutNoCull(HAND), (entry, vc) -> {
			vertex(vc, entry, x0, y0, u0, 1f);
			vertex(vc, entry, x1, y0, u1, 1f);
			vertex(vc, entry, x1, y1, u1, 0f);
			vertex(vc, entry, x0, y1, u0, 0f);
		});
		matrices.pop();
	}

	/**
	 * Normalna celowo NIE obraca się z kamerą: światła vanilli (0.2,1,-0.7)/(-0.2,1,0.7) są w świecie,
	 * a ich "góra" daje 1.0 jasności z każdej strony. Normalna do kamery dawała 50–74% – ręka szara jak z boku moba.
	 * Kolejność wierzchołków (lewy dół → prawy dół → prawa góra) jak w kuli XP, więc kamera widzi przód (PER_FACE_LIGHTING).
	 */
	private static void vertex(VertexConsumer vc, MatrixStack.Entry e, float x, float y, float u, float v) {
		vc.vertex(e, x, y, 0f).color(0xFFFFFFFF).texture(u, v).overlay(OverlayTexture.DEFAULT_UV)
			.light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 1f, 0f);
	}
}
