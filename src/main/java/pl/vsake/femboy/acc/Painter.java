package pl.vsake.femboy.acc;

/**
 * Maluje teksturę dodatków (128x128 ARGB) dla jednego wyglądu: futro w kolorze z wyglądu z delikatnym
 * "włosiem", różowe środki uszu, złoty dzwoneczek, paski na zakolanówkach, plisy spódniczki itd.
 * Czysta Java – ten sam obraz trafia do gry i do podglądu poza grą.
 *
 * Ściany prostopadłościanu w teksturze (jak w vanilli, kawałek od (u,v), wymiary sx,sy,sz):
 * góra (u+sz, v) sx×sz, spód (u+sz+sx, v) sx×sz, prawy bok (u, v+sz) sz×sy, przód (u+sz, v+sz) sx×sy,
 * lewy bok (u+sz+sx, v+sz) sz×sy, tył (u+2sz+sx, v+sz) sx×sy.
 * Współrzędne na ścianie (fx, fy): przód – fx rośnie z X; tył – fx=0 przy max X; prawy bok – fx=0 z tyłu;
 * lewy bok – fx=0 z przodu; góra/spód – fx rośnie z X, fy=0 z tyłu. fy zawsze 0 u góry ściany.
 */
public final class Painter {
	private Painter() {
	}

	public enum Face {TOP, BOTTOM, RIGHT, FRONT, LEFT, BACK}

	public static final int PINK = 0xF7A9C4, PINK_DARK = 0xE88AAE, CREAM = 0xF3E7D3, SNOW = 0xF7F4EE, DARK_TIP = 0x2D221C;
	public static final int GOLD = 0xE5B53A, GOLD_LIGHT = 0xFFE27A, GOLD_DARK = 0x7A5A1A, BONE = 0xF1EBDD, SILVER = 0xBFC4CC;
	public static final int HEART = 0xFF4B7D, HEART_LIGHT = 0xFFC2D3, WHITE = 0xF5F5F5;

	@FunctionalInterface
	interface Fn {
		/** @return 0xRRGGBB albo -1 (przezroczyste) */
		int color(Face f, int fx, int fy, int fw, int fh);
	}

	public static int[] paint(Look look) {
		int[] px = new int[Parts.TEX_W * Parts.TEX_H];
		for (Parts.Piece p : Parts.PIECES) {
			if (!p.usedBy(look)) continue;
			for (Parts.Box b : p.boxes) paint(px, b, fn(p, b, look));
		}
		return px;
	}

	/** Prostokąt ściany w teksturze: {x0, y0, szer, wys}. */
	public static int[] rect(Parts.Box b, Face f) {
		int u = b.u, v = b.v, sx = b.sx, sy = b.sy, sz = b.sz;
		return switch (f) {
			case TOP -> new int[]{u + sz, v, sx, sz};
			case BOTTOM -> new int[]{u + sz + sx, v, sx, sz};
			case RIGHT -> new int[]{u, v + sz, sz, sy};
			case FRONT -> new int[]{u + sz, v + sz, sx, sy};
			case LEFT -> new int[]{u + sz + sx, v + sz, sz, sy};
			case BACK -> new int[]{u + 2 * sz + sx, v + sz, sx, sy};
		};
	}

	private static void paint(int[] px, Parts.Box b, Fn fn) {
		for (Face f : Face.values()) {
			int[] r = rect(b, f);
			for (int fy = 0; fy < r[3]; fy++)
				for (int fx = 0; fx < r[2]; fx++) {
					int x = r[0] + fx, y = r[1] + fy;
					if (x < 0 || y < 0 || x >= Parts.TEX_W || y >= Parts.TEX_H) continue;
					int c = fn.color(f, fx, fy, r[2], r[3]);
					px[y * Parts.TEX_W + x] = c < 0 ? 0 : 0xFF000000 | (shadeFace(c, f) & 0xFFFFFF);
				}
		}
	}

	private static Fn fn(Parts.Piece p, Parts.Box b, Look look) {
		int fur = look.fur, acc = look.accent;
		String n = p.name, t = b.tag;
		int salt = n.hashCode() * 31 + t.hashCode();
		if (n.startsWith("ear_cat_")) return (f, x, y, w, h) -> {
			if (f == Face.FRONT && (t.equals("r0") && (x == 1 || x == 2) || t.equals("r1") && x == 1))
				return t.equals("r0") && y == h - 1 ? PINK_DARK : PINK;
			return furTex(fur, f, x, y, salt);
		};
		if (n.startsWith("ear_fox_")) return (f, x, y, w, h) -> {
			if (t.equals("r4")) return DARK_TIP;
			if (t.equals("r3")) return mix(furTex(fur, f, x, y, salt), DARK_TIP, 0.3f);
			// jasny środek: trójkąt 3-2-1 na przedniej ścianie
			if (f == Face.FRONT && (t.equals("r0") && x >= 1 && x <= 3 || t.equals("r1") && (x == 1 || x == 2) || t.equals("r2") && x == 1)) return CREAM;
			return furTex(fur, f, x, y, salt);
		};
		if (n.startsWith("ear_wolf_")) return (f, x, y, w, h) -> {
			if (t.equals("r2")) return mul(furTex(fur, f, x, y, salt), 0.72f);
			if (f == Face.FRONT && (t.equals("r0") && (x == 1 || x == 2) || t.equals("r1") && x == 1)) return mix(CREAM, 0xD9D6D2, 0.6f);
			return furTex(fur, f, x, y, salt);
		};
		if (n.startsWith("ear_puppy_")) return (f, x, y, w, h) -> mul(furTex(fur, f, x, y, salt), 0.82f);
		if (n.startsWith("ear_bunny")) return (f, x, y, w, h) -> {
			if (f == Face.FRONT && (t.equals("lo") || t.equals("up")) && x == 1) return t.equals("lo") && y == h - 1 ? PINK_DARK : PINK;
			return furTex(fur, f, x, y, salt);
		};
		if (n.startsWith("ear_bear_") || n.startsWith("ear_mouse_")) {
			boolean mouse = n.startsWith("ear_mouse_");
			return (f, x, y, w, h) -> {
				// różowy (mysz) albo jaśniejszy (miś) środek na przedniej ścianie
				boolean inner = f == Face.FRONT && (t.equals("b0") && x == 1 || t.equals("m0") && x >= 1 && x <= 2 && y >= 1);
				if (inner) return mouse ? PINK : mix(furTex(fur, f, x, y, salt), CREAM, 0.45f);
				return furTex(fur, f, x, y, salt);
			};
		}
		if (n.startsWith("tail_devil_")) return (f, x, y, w, h) -> {
			int c = matTex(acc, x, y, salt);
			return t.equals("spade") || t.equals("point") ? mul(c, 0.85f) : c;
		};
		if (n.equals("lock")) return (f, x, y, w, h) -> {
			if (t.equals("shackle")) return SILVER;
			if (f == Face.FRONT && x == 1 && y == 1) return GOLD_DARK; // dziurka od klucza
			return f == Face.FRONT && y == 0 ? GOLD_LIGHT : GOLD;
		};
		if (n.equals("spikes")) return (f, x, y, w, h) -> f == Face.TOP || f == Face.FRONT && y == 0 ? 0xFFFFFF : SILVER;
		if (n.equals("halo")) return (f, x, y, w, h) -> f == Face.TOP ? GOLD_LIGHT : f == Face.BOTTOM ? GOLD : mix(GOLD, GOLD_LIGHT, 0.5f);
		if (n.startsWith("horn_")) return (f, x, y, w, h) -> {
			int c = matTex(acc, x, y, salt);
			return t.equals("h2") ? mix(c, 0xFFFFFF, 0.25f) : t.equals("h0") ? mul(c, 0.8f) : c;
		};
		if (n.equals("tiara")) return (f, x, y, w, h) -> {
			if (t.equals("p_c") && f == Face.FRONT && y == 0) return acc; // klejnot w kolorze dodatków
			return f == Face.TOP ? GOLD_LIGHT : GOLD;
		};
		if (n.equals("flower")) return (f, x, y, w, h) -> t.equals("c") ? GOLD_LIGHT : mix(acc, 0xFFFFFF, f == Face.FRONT ? 0.15f : 0f);
		if (n.equals("glasses")) return (f, x, y, w, h) -> mul(acc, 0.55f);
		if (n.equals("mask")) return (f, x, y, w, h) -> {
			int c = mix(acc, 0xFFFFFF, 0.65f);
			if (t.startsWith("strap")) return mul(c, 0.85f);
			return f == Face.FRONT && y == 1 ? mul(c, 0.93f) : c; // zakładka materiału
		};
		if (n.startsWith("wing_angel_")) return (f, x, y, w, h) -> {
			// pióra: jaśniejsze pionowe pasy, końcówki szare
			int c = (x % 2 == 0) ? SNOW : 0xE6E3DE;
			return t.equals("w4") || t.equals("w3") && y == h - 1 ? mix(c, 0xBDBAC4, 0.4f) : c;
		};
		if (n.startsWith("wing_bat_")) return (f, x, y, w, h) -> {
			int c = mul(mix(acc, 0x2A202C, 0.55f), 1f);
			return t.equals("w0") ? mul(c, 0.7f) : (x % 3 == 0) ? mul(c, 0.82f) : c; // „kości” błony
		};
		if (n.startsWith("wing_butterfly_")) return (f, x, y, w, h) -> {
			int c = matTex(acc, x, y, salt);
			if ((f == Face.FRONT || f == Face.BACK) && (x + y + (t.hashCode() & 1)) % 4 == 0) return 0xFFFFFF; // kropki
			return x == 0 || x == w - 1 ? mul(c, 0.7f) : c;
		};
		if (n.startsWith("warmer_")) {
			boolean stripes = look.has(Item.ARMS_STRIPES);
			return (f, x, y, w, h) -> {
				if (t.equals("cuff")) return mix(acc, 0xFFFFFF, 0.3f);
				if (f == Face.TOP || f == Face.BOTTOM) return mul(acc, 0.85f);
				return stripes && y % 2 == 1 ? WHITE : matTex(acc, x, y, salt);
			};
		}
		if (n.startsWith("tail_fox_")) return (f, x, y, w, h) -> t.equals("s3") || t.equals("s4") ? furTex(SNOW, f, x, y, salt) : furTex(fur, f, x, y, salt);
		if (n.startsWith("tail_wolf_")) return (f, x, y, w, h) -> {
			int c = furTex(fur, f, x, y, salt);
			return t.equals("s4") ? mul(c, 0.55f) : t.equals("s3") ? mul(c, 0.75f) : c;
		};
		if (n.startsWith("tail_bunny")) return (f, x, y, w, h) -> furTex(mix(fur, SNOW, 0.35f), f, x, y, salt);
		if (n.startsWith("tail_")) return (f, x, y, w, h) -> furTex(fur, f, x, y, salt);
		if (n.equals("collar")) return (f, x, y, w, h) -> {
			int c = matTex(acc, x, y, salt);
			return f == Face.TOP || f == Face.BOTTOM ? mul(c, 0.8f) : c;
		};
		if (n.equals("bell")) return (f, x, y, w, h) -> {
			if (f == Face.TOP) return GOLD_LIGHT;
			if (f == Face.FRONT) return y == h - 1 ? GOLD_DARK : x == 0 ? GOLD_LIGHT : GOLD;
			if (f == Face.BOTTOM) return GOLD_DARK;
			return y == h - 1 ? mul(GOLD, 0.8f) : GOLD;
		};
		if (n.equals("bone")) return (f, x, y, w, h) -> t.equals("ring") ? SILVER : (f == Face.FRONT && y == 0 && !t.equals("bar") ? 0xFFFFFF : BONE);
		if (n.equals("heart") || n.equals("clip")) {
			int base = n.equals("clip") ? acc : HEART;
			return (f, x, y, w, h) -> f == Face.FRONT && t.equals("lobe_r") ? mix(base, 0xFFFFFF, 0.6f) : f == Face.FRONT && t.equals("tip") ? mul(base, 0.8f) : base;
		}
		if (n.equals("neckbow") || n.equals("hairbow")) return (f, x, y, w, h) -> {
			if (t.equals("knot")) return mul(acc, 0.72f);
			// pętelki ciemnieją przy węzełku, środek wybrzuszony (jaśniejszy)
			boolean inner = t.equals("loop_r") ? x == w - 1 : x == 0;
			int c = matTex(acc, x, y, salt);
			if (f == Face.FRONT || f == Face.BACK) return inner ? mul(c, 0.8f) : y == 1 ? mix(c, 0xFFFFFF, 0.18f) : c;
			return mul(c, 0.9f);
		};
		if (n.startsWith("sock_")) {
			boolean stripes = look.has(Item.LEGS_STRIPES);
			return (f, x, y, w, h) -> {
				if (t.equals("cuff")) return stripes ? mul(acc, 0.92f) : mix(acc, 0xFFFFFF, 0.25f);
				if (f == Face.BOTTOM) return mul(acc, 0.8f);
				if (f == Face.TOP) return acc;
				return stripes && (y % 2 == 0) ? WHITE : matTex(acc, x, y, salt);
			};
		}
		if (n.startsWith("paw_")) return (f, x, y, w, h) -> {
			if (t.equals("cuff")) return furTex(mix(fur, SNOW, 0.55f), f, x, y, salt);
			if (f == Face.BOTTOM) {
				// poduszeczki: duża w środku z tyłu, palce przy przedniej krawędzi
				boolean pad = (x >= 1 && x <= w - 2) && (y <= 1);
				boolean toe = y == h - 1 && (x == 0 || x == w - 1) || y == h - 2 && w >= 4 && (x == 1 || x == 2);
				if (pad || toe) return pad ? PINK : PINK_DARK;
			}
			return furTex(fur, f, x, y, salt);
		};
		if (n.startsWith("skirt_")) return (f, x, y, w, h) -> {
			if (t.equals("waist")) return mul(acc, 0.75f);
			int c = (x % 2 == 0) ? acc : mul(acc, 0.8f);
			if (f == Face.TOP || f == Face.BOTTOM) return mul(acc, 0.85f);
			return y == h - 1 ? mul(c, 0.85f) : c;
		};
		return (f, x, y, w, h) -> fur;
	}

	/** Futro: drobny szum jasności, żeby nie wyglądało jak plastik. */
	private static int furTex(int rgb, Face f, int x, int y, int salt) {
		float n = noise(x, y, salt + f.ordinal() * 977);
		return mul(rgb, 1f + 0.08f * n);
	}

	/** Materiał (wstążka, dzianina): słabszy szum niż futro. */
	private static int matTex(int rgb, int x, int y, int salt) {
		return mul(rgb, 1f + 0.035f * noise(x, y, salt));
	}

	/** Góra trochę jaśniejsza, spód ciemniejszy – gra i tak cieniuje ściany, to tylko dodatek. */
	private static int shadeFace(int rgb, Face f) {
		return switch (f) {
			case TOP -> mul(rgb, 1.05f);
			case BOTTOM -> mul(rgb, 0.9f);
			default -> rgb;
		};
	}

	/** -1..1, stały dla danego piksela. */
	private static float noise(int x, int y, int salt) {
		long h = Rng.mix(((long) x * 73856093L) ^ ((long) y * 19349663L) ^ ((long) salt * 83492791L));
		return ((h >>> 40) / (float) (1 << 24)) * 2f - 1f;
	}

	public static int mul(int rgb, float k) {
		int r = clamp((int) ((rgb >> 16 & 255) * k)), g = clamp((int) ((rgb >> 8 & 255) * k)), b = clamp((int) ((rgb & 255) * k));
		return r << 16 | g << 8 | b;
	}

	public static int mix(int a, int b, float t) {
		int r = (int) ((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t);
		int g = (int) ((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t);
		int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return clamp(r) << 16 | clamp(g) << 8 | clamp(bl);
	}

	private static int clamp(int v) {
		return v < 0 ? 0 : Math.min(255, v);
	}
}
