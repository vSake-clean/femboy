package pl.vsake.femboy.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import pl.vsake.femboy.FemboyMod;
import pl.vsake.femboy.acc.AccessoryRenderState;

/**
 * Rysuje emotkę nad głową gracza: płaski obrazek zwrócony do kamery (jak ręka pat pat), wyskakuje, lekko się buja
 * i po 3 s znika. Obrazki ze wzorów w Emote lądują w jednej teksturze malowanej przy pierwszym użyciu.
 */
public final class EmoteRender {
	private EmoteRender() {
	}

	private static final Identifier TEX = Identifier.of(FemboyMod.ID, "fx/emotes");
	/** Obrazek + 1 px obwódki z każdej strony. */
	private static final int CELL = Emote.SIZE + 2;
	private static final int OUTLINE = 0xFF2A1F2E;
	/** Bok emotki w blokach. */
	private static final float SIZE = 0.46f;
	private static boolean made, broken;

	private static boolean ensure() {
		if (made) return true;
		if (broken || !RenderSystem.isOnRenderThread()) return false;
		try {
			Emote[] all = Emote.values();
			NativeImage img = new NativeImage(NativeImage.Format.RGBA, CELL * all.length, CELL, true);
			for (int i = 0; i < all.length; i++)
				for (int y = 0; y < CELL; y++)
					for (int x = 0; x < CELL; x++) img.setColorArgb(i * CELL + x, y, pixel(all[i], x - 1, y - 1));
			MinecraftClient.getInstance().getTextureManager().registerTexture(TEX, new NativeImageBackedTexture(() -> "femboy emotes", img));
			made = true;
		} catch (Throwable t) {
			broken = true;
			FemboyMod.LOG.error("Emotki nie działają – pomijam je", t);
		}
		return made;
	}

	/** Piksel komórki: kolor ze wzoru albo ciemna obwódka wokół niego. */
	private static int pixel(Emote e, int x, int y) {
		int c = at(e, x, y);
		if (c >= 0) return 0xFF000000 | c;
		for (int dy = -1; dy <= 1; dy++)
			for (int dx = -1; dx <= 1; dx++) if (at(e, x + dx, y + dy) >= 0) return OUTLINE;
		return 0;
	}

	private static int at(Emote e, int x, int y) {
		return x < 0 || y < 0 || x >= Emote.SIZE || y >= Emote.SIZE ? -1 : e.pixel(x, y);
	}

	public static void draw(LivingEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
		if (!(state instanceof AccessoryRenderState acc) || state.invisible) return;
		Reactions.State r = acc.femboy$react();
		Emote e = r.emote;
		if (e == null || camera == null || camera.orientation == null || !ensure()) return;
		float t = r.emoteT;
		// wyskok z lekkim przestrzeleniem, na końcu szybkie zmniejszenie
		float k = t < 0.07f ? back(t / 0.07f) : t > 0.9f ? Math.max(0f, (1f - t) / 0.1f) : 1f;
		float half = SIZE * k / 2f;
		// nad nickiem, jeśli go widać
		float y = state.height + (state.displayName != null ? 0.92f : 0.5f) + 0.035f * (float) Math.sin(state.age * 0.18f);
		int n = Emote.values().length;
		float u0 = e.ordinal() / (float) n, u1 = (e.ordinal() + 1) / (float) n;
		matrices.push();
		matrices.translate(0f, y, 0f);
		matrices.multiply(camera.orientation);
		queue.submitCustom(matrices, RenderLayers.entityCutoutNoCull(TEX), (entry, vc) -> {
			vertex(vc, entry, -half, -half, u0, 1f);
			vertex(vc, entry, half, -half, u1, 1f);
			vertex(vc, entry, half, half, u1, 0f);
			vertex(vc, entry, -half, half, u0, 0f);
		});
		matrices.pop();
	}

	private static float back(float x) {
		float c = 1.70158f, y = x - 1f;
		return 1f + (c + 1f) * y * y * y + c * y * y;
	}

	/** Pełna jasność i normalna „do góry” – jak ręka pat pat (patrz PatRender.vertex). */
	private static void vertex(VertexConsumer vc, MatrixStack.Entry e, float x, float y, float u, float v) {
		vc.vertex(e, x, y, 0f).color(0xFFFFFFFF).texture(u, v).overlay(OverlayTexture.DEFAULT_UV)
			.light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 1f, 0f);
	}
}
