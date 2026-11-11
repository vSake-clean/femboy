package pl.vsake.femboy.gui;

import static pl.vsake.femboy.Lang.tr;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import pl.vsake.femboy.Keys;
import pl.vsake.femboy.fx.Pose;
import pl.vsake.femboy.fx.Poses;

/**
 * Kółko animacji: przytrzymaj klawisz, najedź myszką na pozę i puść – postać ją zagra. Krótkie stuknięcie klawisza
 * zostawia kółko otwarte i wtedy wybiera się kliknięciem. Środek = przerwij trwającą animację / nic nie rób.
 */
public final class PoseWheel extends Screen {
	/** Krócej niż tyle od otwarcia = stuknięcie (kółko zostaje), dłużej = przytrzymanie (puszczenie wybiera). */
	private static final long TAP_MS = 260;

	private final KeyBinding key;
	private final Pose[] poses = Pose.values();
	private final long openedMs = Util.getMeasuringTimeMs();
	/** Klawisz już raz puszczony po stuknięciu – dalej wybiera tylko kliknięcie. */
	private boolean sticky;
	private int mouseX, mouseY;

	public PoseWheel(KeyBinding key) {
		super(Text.literal(tr("Kółko animacji")));
		this.key = key;
	}

	/** Która poza jest pod kursorem: -1 = środek (nic / przerwij). Czysta matematyka – testowana bez gry. */
	public static int pick(double dx, double dy, double rx, double ry, int count) {
		double nx = dx / rx, ny = dy / ry;
		if (count <= 0 || nx * nx + ny * ny < 0.345 * 0.345) return -1;
		double a = Math.atan2(nx, -ny); // 0 = góra, rośnie zgodnie z zegarem
		if (a < 0) a += Math.PI * 2;
		return (int) Math.floor(a / (Math.PI * 2) * count + 0.5) % count;
	}

	private int rx() {
		return Math.max(60, Math.min(width / 2 - 8, 215));
	}

	private int ry() {
		return Math.max(50, Math.min(height / 2 - 16, 122));
	}

	private int hovered() {
		return pick(mouseX - width / 2.0, mouseY - height / 2.0, rx(), ry(), poses.length);
	}

	// ---- wygląd: pierścień z wycinków liczony piksel po pikselu, a potem trzymany jako lista poziomych pasków ----
	private static final float INNER = 0.40f, HUB = 0.345f, POP = 1.06f;
	private static final int SEG_A = 0xD01A1524, SEG_B = 0xD0221B30, SEG_RIM = 0xE04A3A60, SEG_NOW = 0xD0244A36, NOW_RIM = 0xF06BE39A,
		SEL = 0xF0E561BD, SEL_RIM = 0xFFFFC4EE, HUB_FILL = 0xE0120E1A, HUB_RIM = 0xF0FF8AD8;
	private final java.util.List<int[]> strips = new java.util.ArrayList<>();
	private int stripKey = Integer.MIN_VALUE;

	/** Kolor piksela kółka (0 = przezroczysty). nx, ny – położenie względem środka w jednostkach promieni; px – grubość piksela. */
	private int colorAt(double nx, double ny, int hov, int now, double px) {
		double r = Math.sqrt(nx * nx + ny * ny);
		if (r <= HUB) return r > HUB - 1.6 * px ? HUB_RIM : HUB_FILL;
		if (r < INNER || r > POP) return 0;
		double a = Math.atan2(nx, -ny);
		if (a < 0) a += Math.PI * 2;
		double f = a / (Math.PI * 2) * poses.length + 0.5;
		int seg = (int) Math.floor(f) % poses.length;
		boolean sel = seg == hov;
		if (r > (sel ? POP : 1.0)) return 0;
		// przerwa między wycinkami (stała szerokość w pikselach, nie w stopniach)
		double edge = Math.min(f - Math.floor(f), Math.ceil(f) - f) * (Math.PI * 2 / poses.length) * r;
		if (edge < 1.1 * px) return 0;
		boolean rim = r > (sel ? POP : 1.0) - 2.2 * px || r < INNER + 1.4 * px;
		if (sel) return rim ? SEL_RIM : SEL;
		if (seg == now) return rim ? NOW_RIM : SEG_NOW;
		return rim ? SEG_RIM : (seg & 1) == 0 ? SEG_A : SEG_B;
	}

	private void rebuildStrips(int cx, int cy, double rx, double ry, int hov, int now) {
		strips.clear();
		int w = (int) Math.ceil(rx * POP) + 1, h = (int) Math.ceil(ry * POP) + 1;
		double px = 1.0 / Math.min(rx, ry);
		for (int y = -h; y <= h; y++) {
			int run = 0, from = -w;
			for (int x = -w; x <= w + 1; x++) {
				int c = x > w ? 0 : colorAt((x + 0.5) / rx, (y + 0.5) / ry, hov, now, px);
				if (c == run) continue;
				if (run != 0) strips.add(new int[]{cx + from, cy + y, cx + x, cy + y + 1, run});
				run = c;
				from = x;
			}
		}
	}

	@Override
	public void render(DrawContext ctx, int mx, int my, float delta) {
		mouseX = mx;
		mouseY = my;
		int cx = width / 2, cy = height / 2;
		// kółko „wyrasta” ze środka w ułamku sekundy
		float open = Math.min(1f, (Util.getMeasuringTimeMs() - openedMs) / 130f);
		open = 1f - (1f - open) * (1f - open);
		double rx = rx() * (0.6 + 0.4 * open), ry = ry() * (0.6 + 0.4 * open);
		int hov = hovered();
		Pose now = Poses.mine();
		int nowIdx = now == null ? -1 : now.ordinal();
		int key = ((hov + 1) * 31 + (nowIdx + 1)) * 31 + (int) (open * 64) + width * 7919 + height * 104729;
		if (key != stripKey) {
			stripKey = key;
			rebuildStrips(cx, cy, rx, ry, hov, nowIdx);
		}
		for (int[] s : strips) ctx.fill(s[0], s[1], s[2], s[3], s[4]);
		if (open < 0.6f) return; // napisy dopiero na rozwiniętym kółku
		for (int i = 0; i < poses.length; i++) {
			boolean sel = i == hov;
			double a = Math.PI * 2 * i / poses.length, at = sel ? 0.745 : 0.715;
			int x = cx + (int) Math.round(Math.sin(a) * rx * at), y = cy - (int) Math.round(Math.cos(a) * ry * at);
			String name = tr(poses[i].brief);
			int w = textRenderer.getWidth(name);
			String k = Keys.name(Poses.key(poses[i]));
			boolean hasKey = !"—".equals(k);
			int ty = hasKey ? y - 9 : y - 4;
			ctx.drawText(textRenderer, Text.literal(name), x - w / 2, ty, sel ? 0xFFFFFFFF : poses[i] == now ? 0xFFB6F5CF : 0xFFE6DCEF, true);
			if (hasKey) ctx.drawText(textRenderer, Text.literal(k), x - textRenderer.getWidth(k) / 2, ty + 10, sel ? 0xFFFFE3F6 : 0xFF8E84A0, false);
		}
		// środek: co się stanie po puszczeniu
		String top = hov >= 0 ? tr(poses[hov].label) : now != null ? tr("Przerwij") : tr("Animacje");
		String sub = hov >= 0 ? (poses[hov].held() ? tr("trwa do ruchu") : tr("krótka")) : now != null ? tr(now.brief) : sticky ? tr("kliknij pozę") : tr("puść na pozie");
		int maxW = (int) (rx * HUB * 1.75);
		top = fit(top, maxW);
		sub = fit(sub, maxW);
		ctx.drawText(textRenderer, Text.literal(top), cx - textRenderer.getWidth(top) / 2, cy - 9, hov >= 0 ? 0xFFFF9BE0 : 0xFFFFFFFF, true);
		ctx.drawText(textRenderer, Text.literal(sub), cx - textRenderer.getWidth(sub) / 2, cy + 3, 0xFFB0A8BC, false);
	}

	private String fit(String s, int w) {
		if (textRenderer.getWidth(s) <= w) return s;
		while (s.length() > 1 && textRenderer.getWidth(s + "…") > w) s = s.substring(0, s.length() - 1);
		return s + "…";
	}

	@Override
	public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
		// bez rozmycia i przyciemnienia – świat ma być widać, kółko jest tylko nakładką
	}

	private void choose() {
		int hov = hovered();
		MinecraftClient mc = MinecraftClient.getInstance();
		mc.setScreen(null);
		if (hov >= 0) Poses.play(poses[hov]);
		else if (Poses.mine() != null) Poses.stopMine();
	}

	@Override
	public void tick() {
		if (sticky || key == null || key.isUnbound()) {
			sticky = true;
			return;
		}
		// puszczenie klawisza: gra nie liczy klawiszy przy otwartym oknie, więc pytamy klawiaturę wprost
		InputUtil.Key bound = InputUtil.fromTranslationKey(key.getBoundKeyTranslationKey());
		if (bound.getCategory() != InputUtil.Type.KEYSYM) {
			sticky = true;
			return;
		}
		if (InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), bound.getCode())) return;
		if (Util.getMeasuringTimeMs() - openedMs < TAP_MS) sticky = true;
		else choose();
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
			close();
			return true;
		}
		if (sticky && key != null && key.matchesKey(input)) { // drugi raz ten sam klawisz = zamknij
			close();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		mouseX = (int) click.x();
		mouseY = (int) click.y();
		choose();
		return true;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
