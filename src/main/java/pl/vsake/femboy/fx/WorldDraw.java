package pl.vsake.femboy.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import org.joml.Quaternionf;
import pl.vsake.femboy.FemboyMod;

import java.util.ArrayList;
import java.util.List;

/**
 * Rysowanie rzeczy, które leżą w świecie, a nie na graczu: kropki wskaźnika laserowego, kłębki włóczki i ślady łapek.
 * Wszystko idzie do tej samej kolejki co moby (tuż przed nimi), ze współrzędnymi względem kamery.
 * Jedna mała tekstura malowana przy pierwszym użyciu: kropka, łapka, włóczka (komórki 16×16; łapka liczona z kółek, z miękkim brzegiem).
 */
public final class WorldDraw {
	private WorldDraw() {
	}

	private static final Identifier TEX = Identifier.of(FemboyMod.ID, "fx/world");
	private static final int CELL = 16, CELLS = 3;
	private static final String[] DOT = {
		"........",
		"..oooo..",
		".oOOOOo.",
		".oOWWOo.",
		".oOWWOo.",
		".oOOOOo.",
		"..oooo..",
		"........"};
	private static final String[] YARN = {
		"WLWWLWWL",
		"LWWLWWLW",
		"WWLWWLWW",
		"WLWWLWWD",
		"LWWLWWDW",
		"WWLWWDWW",
		"WLWWDWWL",
		"LWWDWWLW"};
	private static boolean made, broken, warned;

	public static void init() {
		WorldRenderEvents.BEFORE_ENTITIES.register(ctx -> {
			try {
				draw(ctx);
			} catch (Throwable t) { // błąd w zabawkach nie może wywalić rysowania świata
				if (!warned) {
					warned = true;
					FemboyMod.LOG.error("Zabawki / ślady łapek nie rysują się – pomijam je", t);
				}
			}
		});
	}

	private static boolean ensure() {
		if (made) return true;
		if (broken || !RenderSystem.isOnRenderThread()) return false;
		try {
			NativeImage img = new NativeImage(NativeImage.Format.RGBA, CELL * CELLS, CELL, true);
			String[][] cells = {DOT, null, YARN};
			for (int c = 0; c < CELLS; c++)
				for (int y = 0; y < CELL; y++)
					for (int x = 0; x < CELL; x++) {
						if (cells[c] == null) {
							img.setColorArgb(c * CELL + x, y, Math.round(paw(x, y) * 255f) << 24 | 0xFFFFFF);
							continue;
						}
						img.setColorArgb(c * CELL + x, y, switch (cells[c][y / 2].charAt(x / 2)) {
							case 'o' -> 0x70FF2020;
							case 'O' -> 0xFFFF2B2B;
							case 'W' -> 0xFFFFFFFF;
							case 'L' -> 0xFFD8D8D8;
							case 'D' -> 0xFFA8A8A8;
							default -> 0;
						});
					}
			MinecraftClient.getInstance().getTextureManager().registerTexture(TEX, new NativeImageBackedTexture(() -> "femboy world fx", img));
			made = true;
		} catch (Throwable t) {
			broken = true;
			FemboyMod.LOG.error("Tekstura zabawek nie powstała – pomijam je", t);
		}
		return made;
	}

	/** Kocia łapka 16×16 (palce u góry): duża poduszka i cztery paluszki; wynik = ile piksela jest zakryte (0–1). */
	static float paw(int px, int py) {
		// {środek x, środek y, promień x, promień y}
		float[][] parts = {{8f, 10.9f, 4.3f, 3.3f}, {5.7f, 9.3f, 2.3f, 2.2f}, {10.3f, 9.3f, 2.3f, 2.2f},
			{2.9f, 6.6f, 1.55f, 1.9f}, {6.1f, 3.6f, 1.6f, 2.05f}, {9.9f, 3.6f, 1.6f, 2.05f}, {13.1f, 6.6f, 1.55f, 1.9f}};
		int in = 0;
		for (int sy = 0; sy < 4; sy++)
			for (int sx = 0; sx < 4; sx++) {
				float x = px + (sx + 0.5f) / 4f, y = py + (sy + 0.5f) / 4f;
				for (float[] p : parts) {
					float dx = (x - p[0]) / p[2], dy = (y - p[1]) / p[3];
					if (dx * dx + dy * dy <= 1f) {
						in++;
						break;
					}
				}
			}
		return in / 16f;
	}

	private static void draw(WorldRenderContext ctx) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientWorld world = mc.world;
		if (world == null || Toys.dots().isEmpty() && Toys.balls().isEmpty() && Prints.all().isEmpty()) return;
		CameraRenderState cam = ctx.worldState().cameraRenderState;
		OrderedRenderCommandQueue queue = ctx.commandQueue();
		if (cam == null || cam.pos == null || queue == null || !ensure()) return;
		MatrixStack ms = ctx.matrices() != null ? ctx.matrices() : new MatrixStack();
		Vec3d c = cam.pos;
		float tp = mc.getRenderTickCounter().getTickProgress(false);
		prints(world, queue, ms, c);
		balls(world, queue, ms, c, tp);
		dots(queue, ms, c, cam.orientation);
	}

	private record Quad(float x, float y, float z, float cos, float sin, int color, int light, boolean flip) {
	}

	/**
	 * Ślady łapek: odcisk „wgnieciony” w ziemię – ciemne wgłębienie i pod nim jasny brzeg przesunięty o włos (jak światło
	 * na krawędzi dołka). Obrócone w stronę marszu, lewa i prawa łapka są lustrzane; bledną pod koniec życia.
	 */
	private static void prints(ClientWorld world, OrderedRenderCommandQueue queue, MatrixStack ms, Vec3d c) {
		if (Prints.all().isEmpty()) return;
		long now = Util.getMeasuringTimeMs();
		List<Quad> rims = new ArrayList<>(), dents = new ArrayList<>();
		for (Prints.Print p : Prints.all()) {
			double dx = p.x() - c.x, dy = p.y() - c.y, dz = p.z() - c.z;
			if (dx * dx + dy * dy + dz * dz > 48 * 48) continue;
			long age = now - p.bornMs(), left = Prints.LIFE_MS - age;
			if (left <= 0) continue;
			// pojawia się w ułamku sekundy (stopa dopiero się odrywa), znika powoli
			float k = Math.min(1f, left / (float) Prints.FADE_MS) * Math.min(1f, age / 180f);
			int light = light(world, p.x(), p.y() + 0.2, p.z());
			float cos = MathHelper.cos(p.yaw()), sin = MathHelper.sin(p.yaw());
			// na śniegu chłodny niebieskawy cień i biały brzeg, na piasku ciemny brąz i jasny piasek
			int rim = (int) (k * (p.snow() ? 150 : 90)) << 24 | (p.snow() ? 0xFFFFFF : 0xFFF2CC);
			int dent = (int) (k * (p.snow() ? 135 : 150)) << 24 | (p.snow() ? 0x62789E : 0x57401F);
			rims.add(new Quad((float) dx + 0.011f, (float) dy, (float) dz + 0.011f, cos, sin, rim, light, p.left()));
			dents.add(new Quad((float) dx, (float) dy + 0.002f, (float) dz, cos, sin, dent, light, p.left()));
		}
		if (dents.isEmpty()) return;
		rims.addAll(dents); // jasne brzegi najpierw, wgłębienia na nich
		float u0 = 1f / CELLS, u1 = 2f / CELLS, h = 0.1f;
		queue.submitCustom(ms, RenderLayers.entityTranslucent(TEX), (e, vc) -> {
			for (Quad q : rims) {
				float ua = q.flip ? u1 : u0, ub = q.flip ? u0 : u1;
				// lokalnie: x w bok, z do przodu (palce z przodu = góra obrazka)
				ground(vc, e, q, -h, -h, ua, 1f);
				ground(vc, e, q, -h, h, ua, 0f);
				ground(vc, e, q, h, h, ub, 0f);
				ground(vc, e, q, h, -h, ub, 1f);
			}
		});
	}

	private static void ground(VertexConsumer vc, MatrixStack.Entry e, Quad q, float lx, float lz, float u, float v) {
		float x = q.x + lx * q.cos - lz * q.sin, z = q.z + lx * q.sin + lz * q.cos;
		vc.vertex(e, x, q.y, z).color(q.color).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(q.light).normal(e, 0f, 1f, 0f);
	}

	/** Kłębki: mały sześcian w kolorze dodatków właściciela, obraca się od toczenia. */
	private static void balls(ClientWorld world, OrderedRenderCommandQueue queue, MatrixStack ms, Vec3d c, float tp) {
		float u0 = 2f / CELLS, u1 = 1f, r = (float) Toys.RADIUS;
		for (Toys.Ball b : Toys.balls()) {
			double x = MathHelper.lerp(tp, b.px, b.x), y = MathHelper.lerp(tp, b.py, b.y), z = MathHelper.lerp(tp, b.pz, b.z);
			if (c.squaredDistanceTo(x, y, z) > 64 * 64) continue;
			int light = light(world, x, y, z), color = 0xFF000000 | b.color;
			ms.push();
			ms.translate(x - c.x, y - c.y, z - c.z);
			ms.multiply(new Quaternionf().rotateAxis(MathHelper.lerp(tp, b.lastSpin, b.spin), b.axisX, 0f, b.axisZ));
			queue.submitCustom(ms, RenderLayers.entityCutoutNoCull(TEX), (e, vc) -> {
				// 6 ścian: (środek ściany) n, osie a i b
				face(vc, e, r, 0, 1, 0, 1, 0, 0, 0, 0, 1, u0, u1, color, light);
				face(vc, e, r, 0, -1, 0, 1, 0, 0, 0, 0, -1, u0, u1, color, light);
				face(vc, e, r, 1, 0, 0, 0, 0, 1, 0, 1, 0, u0, u1, color, light);
				face(vc, e, r, -1, 0, 0, 0, 0, -1, 0, 1, 0, u0, u1, color, light);
				face(vc, e, r, 0, 0, 1, -1, 0, 0, 0, 1, 0, u0, u1, color, light);
				face(vc, e, r, 0, 0, -1, 1, 0, 0, 0, 1, 0, u0, u1, color, light);
			});
			ms.pop();
		}
	}

	private static void face(VertexConsumer vc, MatrixStack.Entry e, float r, float nx, float ny, float nz, float ax, float ay, float az,
							 float bx, float by, float bz, float u0, float u1, int color, int light) {
		corner(vc, e, r, nx, ny, nz, ax, ay, az, bx, by, bz, -1, -1, u0, 1f, color, light);
		corner(vc, e, r, nx, ny, nz, ax, ay, az, bx, by, bz, 1, -1, u1, 1f, color, light);
		corner(vc, e, r, nx, ny, nz, ax, ay, az, bx, by, bz, 1, 1, u1, 0f, color, light);
		corner(vc, e, r, nx, ny, nz, ax, ay, az, bx, by, bz, -1, 1, u0, 0f, color, light);
	}

	private static void corner(VertexConsumer vc, MatrixStack.Entry e, float r, float nx, float ny, float nz, float ax, float ay, float az,
							   float bx, float by, float bz, int sa, int sb, float u, float v, int color, int light) {
		vc.vertex(e, (nx + ax * sa + bx * sb) * r, (ny + ay * sa + by * sb) * r, (nz + az * sa + bz * sb) * r).color(color).texture(u, v)
			.overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, nx, ny, nz);
	}

	/** Kropki lasera: obrazek zwrócony do kamery, zawsze w pełni jasny. */
	private static void dots(OrderedRenderCommandQueue queue, MatrixStack ms, Vec3d c, Quaternionf orientation) {
		if (Toys.dots().isEmpty() || orientation == null) return;
		float u1 = 1f / CELLS;
		for (Toys.Dot d : Toys.dots().values()) {
			Vec3d p = d.shown;
			double dist = Math.sqrt(c.squaredDistanceTo(p));
			if (dist > 96) continue;
			// z daleka trochę większa, żeby nie znikała
			float h = (float) (0.07 + Math.min(0.12, dist * 0.004));
			ms.push();
			ms.translate(p.x - c.x, p.y - c.y, p.z - c.z);
			ms.multiply(orientation);
			queue.submitCustom(ms, RenderLayers.entityTranslucentEmissive(TEX), (e, vc) -> {
				dot(vc, e, -h, -h, 0f, 1f);
				dot(vc, e, h, -h, u1, 1f);
				dot(vc, e, h, h, u1, 0f);
				dot(vc, e, -h, h, 0f, 0f);
			});
			ms.pop();
		}
	}

	private static void dot(VertexConsumer vc, MatrixStack.Entry e, float x, float y, float u, float v) {
		vc.vertex(e, x, y, 0f).color(0xFFFFFFFF).texture(u, v).overlay(OverlayTexture.DEFAULT_UV)
			.light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 1f, 0f);
	}

	private static int light(ClientWorld world, double x, double y, double z) {
		BlockPos pos = BlockPos.ofFloored(x, y, z);
		return LightmapTextureManager.pack(world.getLightLevel(LightType.BLOCK, pos), world.getLightLevel(LightType.SKY, pos));
	}
}
