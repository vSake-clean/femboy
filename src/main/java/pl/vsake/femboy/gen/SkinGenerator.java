package pl.vsake.femboy.gen;

import java.util.Random;

/**
 * Proceduralny generator skinów femboy / e-girl 64x64 (model slim, ręce 3 px).
 * Czysta Java bez klas Minecrafta – da się go odpalić i obejrzeć poza grą.
 * Wynik: piksele ARGB, indeks y * 64 + x, 0 = przezroczysty.
 * Ten sam styl + to samo ziarno = zawsze ten sam skin.
 */
public final class SkinGenerator {
	public static final int SIZE = 64;

	private SkinGenerator() {
	}

	public static int[] generate(Style style, long seed) {
		Look look = new Look(style, new Random(seed));
		return new Painter(look, new Random(seed * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L)).paint();
	}

	// ================================ układ UV ================================

	/**
	 * Prostopadłościan modelu w standardowym układzie skinów 64x64.
	 * "Pętla" to pas ścian bocznych, ciągły dookoła: prawa ściana (od tyłu do przodu),
	 * przód (od prawej strony postaci), lewa (od przodu do tyłu), tył (od lewej strony postaci).
	 */
	record Box(int u, int v, int w, int h, int d, boolean right) {
		int loop() {
			return 2 * (w + d);
		}

		/** Przód, i = 0 przy prawym boku postaci (lewa strona obrazka). */
		int front(int i) {
			return d + i;
		}

		/** Prawa ściana, i = 0 z tyłu. */
		int rightSide(int i) {
			return i;
		}

		/** Lewa ściana, i = 0 z przodu. */
		int leftSide(int i) {
			return d + w + i;
		}

		/** Tył, i = 0 przy lewym boku postaci (lewa strona obrazka, gdy patrzysz od tyłu). */
		int back(int i) {
			return 2 * d + w + i;
		}

		/** Zewnętrzna ściana kończyny, i = 0 z tyłu … d-1 z przodu. */
		int outer(int i) {
			return right ? i : d + w + (d - 1 - i);
		}

		/** Wewnętrzna ściana kończyny (od strony tułowia), i = 0 z tyłu … d-1 z przodu. */
		int inner(int i) {
			return right ? d + w + (d - 1 - i) : i;
		}

		/** 0 = prawa ściana, 1 = przód, 2 = lewa, 3 = tył. */
		int face(int col) {
			col = Math.floorMod(col, loop());
			if (col < d) return 0;
			if (col < d + w) return 1;
			if (col < 2 * d + w) return 2;
			return 3;
		}

		/** Czy kolumna pętli leży po prawej połowie postaci (do farbowania na dwa kolory). */
		boolean rightHalf(int col) {
			col = Math.floorMod(col, loop());
			return col < d + w / 2 || col >= 2 * d + w + w / 2;
		}
	}

	static final Box HEAD = new Box(0, 0, 8, 8, 8, false), HAT = new Box(32, 0, 8, 8, 8, false);
	static final Box BODY = new Box(16, 16, 8, 12, 4, false), JACKET = new Box(16, 32, 8, 12, 4, false);
	static final Box R_ARM = new Box(40, 16, 3, 12, 4, true), R_SLEEVE = new Box(40, 32, 3, 12, 4, true);
	static final Box L_ARM = new Box(32, 48, 3, 12, 4, false), L_SLEEVE = new Box(48, 48, 3, 12, 4, false);
	static final Box R_LEG = new Box(0, 16, 4, 12, 4, true), R_PANTS = new Box(0, 32, 4, 12, 4, true);
	static final Box L_LEG = new Box(16, 48, 4, 12, 4, false), L_PANTS = new Box(0, 48, 4, 12, 4, false);
	static final Box[] ARMS = {R_ARM, L_ARM}, SLEEVES = {R_SLEEVE, L_SLEEVE};
	static final Box[] LEGS = {R_LEG, L_LEG}, PANTS = {R_PANTS, L_PANTS};

	// ================================ kolory ================================

	static int rgb(int hex) {
		return 0xFF000000 | hex;
	}

	static int clamp(double v) {
		return (int) Math.max(0, Math.min(255, Math.round(v)));
	}

	static int mix(int a, int b, double t) {
		int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
		int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
		return 0xFF000000 | clamp(ar + (br - ar) * t) << 16 | clamp(ag + (bg - ag) * t) << 8 | clamp(ab + (bb - ab) * t);
	}

	private static final int SHADOW_TINT = rgb(0x2E1F4A);

	/** Cień: przyciemnia z lekkim fioletowym odcieniem (ładniej niż czysta czerń). */
	static int dark(int c, double t) {
		return mix(mix(c, 0xFF000000, t), SHADOW_TINT, t * 0.35);
	}

	static int light(int c, double t) {
		return mix(c, 0xFFFFFFFF, t);
	}

	static double luma(int c) {
		return (0.299 * ((c >> 16) & 255) + 0.587 * ((c >> 8) & 255) + 0.114 * (c & 255)) / 255.0;
	}

	// ================================ wygląd ================================

	enum Hair { BOB, FLUFFY, SWEPT, HIME, LONG, WOLF, PONYTAIL }

	enum Top { HOODIE, SWEATER, STRIPED, SAILOR, TEE }

	enum Print { NONE, HEART, SMALL_HEART, BAND }

	enum Bottom { SKIRT, PLAID, SHORTS }

	enum Legs { STRIPED_HIGHS, SOLID_HIGHS, KNEE_SOCKS, FISHNET, STRIPED_TIGHTS }

	enum Shoes { SNEAKERS, MARY_JANES, PLATFORMS }

	enum Acc { NONE, BOW_BACK, BOW_SIDE, CLIPS, HEADPHONES }

	static final int[] SKIN = {0xFFE6D8, 0xFAD7C3, 0xF1C4A6, 0xE2AE8C, 0xC98D67, 0xA56C4A, 0x7B4D35};
	static final int[] SKIN_W = {2, 3, 3, 2, 2, 2, 1};

	static final int[] FB_HAIR = {0xF4A6C8, 0xF07FB4, 0xA6D4F4, 0xC4A9EE, 0xF1ECE6, 0xEBCB7A, 0x8C5A3C, 0x4E3427, 0x2B2430, 0xA6E6CF, 0xC9C6D3};
	static final int[][] FB_SPLIT = {{0xF4A6C8, 0xA6D4F4}, {0xF1ECE6, 0xF07FB4}, {0xC4A9EE, 0xF4A6C8}, {0x2B2430, 0xF4A6C8}};
	static final int[] EG_HAIR = {0x1E1A22, 0x232A3D, 0xEE4F95, 0xF4A6C8, 0x8B57D3, 0x3F76E4, 0xC8344A, 0xEDE8EE, 0x69D17A, 0x2B2430};
	static final int[][] EG_SPLIT = {{0x1E1A22, 0xEE4F95}, {0x1E1A22, 0xF3F1F4}, {0xEE4F95, 0x3F76E4}, {0x8B57D3, 0x1E1A22},
		{0xC8344A, 0x1E1A22}, {0xEBCB7A, 0x1E1A22}, {0x69D17A, 0x1E1A22}, {0xF4A6C8, 0x1E1A22}};

	static final int[] FB_EYES = {0x4A8FE8, 0x7FC3F5, 0x4FBF7A, 0x9A6AE0, 0xE86AAE, 0xE8B84A, 0x7A4E2E, 0x3FB8B0};
	static final int[] EG_EYES = {0xD4424E, 0x9A6AE0, 0xE86AAE, 0x4A8FE8, 0x4FBF7A, 0x8B93A6, 0x7A4E2E};

	static final int[] FB_TOP = {0xF6B4D1, 0xCDB6F2, 0xB3D8F5, 0xF5F2F7, 0xF6EEDB, 0xBDE9D6, 0x2E2A36, 0xBFBBC8, 0xFBE29A};
	static final int[] FB_ACC = {0xF07FB4, 0xFFFFFF, 0x8FC4F0, 0xBFA3EE, 0xFFD34A, 0x2E2A36};
	static final int[] EG_TOP = {0x1F1C24, 0x2B2533, 0x3A2A4A, 0x5B1F33, 0x1F1C24};
	static final int[] EG_ACC = {0xEE4F95, 0x9B5CF0, 0x3FD1E4, 0xE83A4A, 0x7CE08A, 0xF4F4F4};
	static final int[] SAILOR_COLLAR = {0x2E3B66, 0xF07FB4, 0x1E1A22, 0xC23B4A, 0x8FC4F0};

	static final int[] FB_SKIRT = {0x2A2730, 0xF3A1C6, 0xF2F1F6, 0x2E3B66, 0xCDB6F2, 0xB3D8F5};
	static final int[][] FB_PLAID = {{0xC23B4A, 0x1E1A22}, {0xF3A1C6, 0xFFFFFF}, {0x2E3B66, 0xF2F1F6}, {0x6B6F7A, 0x1E1A22}, {0xB3D8F5, 0xFFFFFF}};
	static final int[] EG_SKIRT = {0x1C1A20, 0x2A2730, 0x4A2E6A, 0x1C1A20};
	static final int[][] EG_PLAID = {{0xB8323F, 0x1C1A20}, {0x2A2730, 0x77727F}, {0xEE4F95, 0x1C1A20}, {0x4A2E6A, 0x1C1A20}};
	static final int[] SHORTS = {0x4F6FA8, 0x2A2730, 0xF3A1C6, 0xF2F1F6};

	static final int[][] FB_STRIPES = {{0xF7F5F8, 0xF49AC1}, {0xF7F5F8, 0x8FC4F0}, {0xF7F5F8, 0xBFA3EE}, {0xF7F5F8, 0x2A2730}, {0xF49AC1, 0xFFFFFF}};
	static final int[] FB_SOCK = {0xF7F5F8, 0x2A2730, 0xF49AC1, 0xF7F5F8, 0xCDB6F2};
	static final int[][] EG_STRIPES = {{0x1C1A20, 0xF3F1F4}, {0x1C1A20, 0xEE4F95}, {0x1C1A20, 0x9B5CF0}, {0x1C1A20, 0xE83A4A}};

	static final int SILVER = rgb(0xC9CCD6), SILVER_D = rgb(0x8E92A0), GOLD = rgb(0xFFD34A), WHITE = rgb(0xF7F6FA);

	/** Wszystkie losowe decyzje dla jednej postaci. */
	static final class Look {
		final Style style;
		final int skin, skinShade, blush, mouthC;
		final Hair hair;
		final int hairA, hairB;
		final int eye;
		final boolean bigEyes, winged, heartMark, strongBlush;
		final int mouth; // 0 = mała, 1 = szeroka, 2 = język
		final Top top;
		final int topC, topAcc;
		final Print print;
		final boolean underStripes, armWarmers;
		final int underA, underB;
		final Bottom bottom;
		final int botC, botAcc;
		final int skirtLen;
		final Legs legs;
		final int legA, legB;
		final boolean sockBow;
		final int sockBowC;
		final Shoes shoes;
		final int shoeC, shoeAcc, soleC;
		final boolean choker;
		final int chokerC, charmC;
		final Acc acc;
		final int accC;
		final boolean chains, bracelet;
		final int braceletC;

		Look(Style style, Random r) {
			this.style = style;
			boolean fb = style != Style.EGIRL; // catboy rysuje się jak femboy

			skin = rgb(SKIN[weighted(r, SKIN_W)]);
			skinShade = dark(skin, 0.12);
			blush = mix(skin, rgb(0xFF5F8A), 0.42);
			mouthC = mix(skin, rgb(0xA8344C), 0.55);

			// --- włosy
			if (fb) {
				hair = pick(r, new Hair[]{Hair.BOB, Hair.FLUFFY, Hair.SWEPT, Hair.HIME, Hair.LONG}, new int[]{35, 30, 15, 10, 10});
				if (chance(r, 0.12)) {
					int[] p = FB_SPLIT[r.nextInt(FB_SPLIT.length)];
					hairA = rgb(p[0]);
					hairB = rgb(p[1]);
				} else {
					hairA = hairB = hairPick(r, FB_HAIR, skin);
				}
			} else {
				hair = pick(r, new Hair[]{Hair.LONG, Hair.HIME, Hair.WOLF, Hair.PONYTAIL, Hair.BOB}, new int[]{32, 24, 22, 14, 8});
				if (chance(r, 0.45)) {
					int[] p = EG_SPLIT[r.nextInt(EG_SPLIT.length)];
					boolean flip = r.nextBoolean();
					hairA = rgb(flip ? p[1] : p[0]);
					hairB = rgb(flip ? p[0] : p[1]);
				} else {
					hairA = hairB = hairPick(r, EG_HAIR, skin);
				}
			}

			// --- twarz
			eye = rgb(fb ? FB_EYES[r.nextInt(FB_EYES.length)] : EG_EYES[r.nextInt(EG_EYES.length)]);
			bigEyes = chance(r, fb ? 0.7 : 0.5);
			winged = chance(r, fb ? 0.15 : 0.8);
			strongBlush = chance(r, fb ? 0.3 : 0.65);
			heartMark = chance(r, fb ? 0.1 : 0.55);
			mouth = pick(r, new Integer[]{0, 1, 2}, fb ? new int[]{60, 30, 10} : new int[]{50, 25, 25});

			// --- góra
			top = fb ? pick(r, new Top[]{Top.HOODIE, Top.SWEATER, Top.STRIPED, Top.SAILOR}, new int[]{40, 22, 13, 25})
				: pick(r, new Top[]{Top.TEE, Top.STRIPED, Top.HOODIE, Top.SAILOR}, new int[]{50, 18, 14, 18});
			if (top == Top.SAILOR) {
				topC = WHITE;
				topAcc = rgb(SAILOR_COLLAR[r.nextInt(SAILOR_COLLAR.length)]);
			} else if (fb) {
				topC = rgb(FB_TOP[r.nextInt(FB_TOP.length)]);
				topAcc = contrastPick(r, topC, FB_ACC);
			} else {
				topC = rgb(EG_TOP[r.nextInt(EG_TOP.length)]);
				topAcc = rgb(EG_ACC[r.nextInt(EG_ACC.length)]);
			}
			print = top == Top.SAILOR || top == Top.STRIPED ? Print.NONE
				: pick(r, Print.values(), fb ? new int[]{40, 20, 30, 10} : new int[]{30, 35, 20, 15});
			underStripes = !fb && top == Top.TEE && chance(r, 0.7);
			armWarmers = !fb && top == Top.TEE && !underStripes && chance(r, 0.6);
			underA = rgb(0x1C1A20);
			underB = chance(r, 0.5) ? rgb(0xF3F1F4) : topAcc;

			// --- dół
			bottom = pick(r, new Bottom[]{Bottom.SKIRT, Bottom.PLAID, Bottom.SHORTS}, fb ? new int[]{55, 28, 17} : new int[]{42, 48, 10});
			if (bottom == Bottom.PLAID) {
				int[] p = fb ? FB_PLAID[r.nextInt(FB_PLAID.length)] : EG_PLAID[r.nextInt(EG_PLAID.length)];
				botC = rgb(p[0]);
				botAcc = rgb(p[1]);
			} else if (bottom == Bottom.SHORTS) {
				botC = rgb(SHORTS[r.nextInt(SHORTS.length)]);
				botAcc = WHITE;
			} else if (top == Top.SAILOR && chance(r, 0.7)) {
				botC = topAcc; // spódnica do kołnierza
				botAcc = WHITE;
			} else {
				botC = rgb(fb ? FB_SKIRT[r.nextInt(FB_SKIRT.length)] : EG_SKIRT[r.nextInt(EG_SKIRT.length)]);
				botAcc = WHITE;
			}
			skirtLen = bottom == Bottom.SHORTS ? 2 : 2 + r.nextInt(2);

			// --- nogi
			legs = fb ? pick(r, new Legs[]{Legs.STRIPED_HIGHS, Legs.SOLID_HIGHS, Legs.KNEE_SOCKS}, new int[]{55, 30, 15})
				: pick(r, new Legs[]{Legs.FISHNET, Legs.STRIPED_TIGHTS, Legs.STRIPED_HIGHS, Legs.SOLID_HIGHS}, new int[]{35, 22, 23, 20});
			if (legs == Legs.STRIPED_HIGHS || legs == Legs.STRIPED_TIGHTS) {
				int[] p = fb ? FB_STRIPES[r.nextInt(FB_STRIPES.length)] : EG_STRIPES[r.nextInt(EG_STRIPES.length)];
				legA = rgb(p[0]);
				legB = rgb(p[1]);
			} else if (legs == Legs.FISHNET) {
				legA = rgb(0x18161C);
				legB = rgb(0x18161C);
			} else {
				legA = rgb(fb ? FB_SOCK[r.nextInt(FB_SOCK.length)] : 0x1C1A20);
				legB = fb ? (luma(legA) > 0.6 ? rgb(0xF49AC1) : WHITE) : rgb(EG_ACC[r.nextInt(EG_ACC.length)]);
			}
			sockBow = fb && (legs == Legs.STRIPED_HIGHS || legs == Legs.SOLID_HIGHS) && chance(r, 0.45);
			sockBowC = rgb(chance(r, 0.6) ? 0xF07FB4 : 0x2A2730);

			// --- buty
			shoes = fb ? pick(r, new Shoes[]{Shoes.SNEAKERS, Shoes.MARY_JANES, Shoes.PLATFORMS}, new int[]{48, 42, 10})
				: pick(r, new Shoes[]{Shoes.PLATFORMS, Shoes.MARY_JANES, Shoes.SNEAKERS}, new int[]{60, 25, 15});
			if (shoes == Shoes.SNEAKERS) {
				shoeC = fb ? rgb(chance(r, 0.6) ? 0xF4F4F6 : 0xF3A9C9) : rgb(0x1E1B22);
				shoeAcc = fb ? rgb(FB_ACC[r.nextInt(FB_ACC.length - 1)]) : rgb(EG_ACC[r.nextInt(EG_ACC.length)]);
				soleC = fb ? WHITE : rgb(0xE9E7EE);
			} else if (shoes == Shoes.MARY_JANES) {
				shoeC = rgb(fb ? (chance(r, 0.7) ? 0x1E1B22 : 0x7A2E3E) : 0x1A181E);
				shoeAcc = SILVER;
				soleC = rgb(0x121014);
			} else {
				shoeC = rgb(0x1A181E);
				shoeAcc = SILVER;
				soleC = rgb(0x3B3842);
			}

			// --- dodatki
			choker = chance(r, fb ? 0.6 : 0.9);
			chokerC = rgb(fb && chance(r, 0.35) ? (chance(r, 0.5) ? 0xF07FB4 : 0xF7F6FA) : 0x16141A);
			charmC = fb ? (chance(r, 0.5) ? GOLD : rgb(0xF07FB4)) : (chance(r, 0.6) ? SILVER : rgb(0xEE4F95));
			acc = pick(r, Acc.values(), fb ? new int[]{30, 18, 14, 24, 14} : new int[]{25, 12, 10, 25, 28});
			accC = fb ? rgb(new int[]{0xF07FB4, 0xFFFFFF, 0x8FC4F0, 0xFFD34A, 0x2E2A36}[r.nextInt(5)])
				: rgb(new int[]{0x16141A, 0xEE4F95, 0xF3F1F4, 0x9B5CF0}[r.nextInt(4)]);
			chains = !fb && bottom != Bottom.SHORTS && chance(r, 0.6);
			bracelet = chance(r, fb ? 0.3 : 0.5);
			braceletC = fb ? rgb(0xF07FB4) : rgb(0x16141A);
		}

		/** Kolor włosów wyraźnie inny niż skóra (inaczej postać wygląda na łysą). */
		static int hairPick(Random r, int[] pal, int skin) {
			int c = rgb(pal[0]);
			for (int tries = 0; tries < 12; tries++) {
				c = rgb(pal[r.nextInt(pal.length)]);
				if (dist(c, skin) > 70) return c;
			}
			return luma(skin) > 0.55 ? rgb(0x2B2430) : rgb(0xF4A6C8);
		}

		static double dist(int a, int b) {
			int dr = ((a >> 16) & 255) - ((b >> 16) & 255), dg = ((a >> 8) & 255) - ((b >> 8) & 255), db = (a & 255) - (b & 255);
			return Math.sqrt(dr * dr + dg * dg + db * db);
		}

		static boolean chance(Random r, double p) {
			return r.nextDouble() < p;
		}

		static int weighted(Random r, int[] w) {
			int sum = 0;
			for (int x : w) sum += x;
			int k = r.nextInt(sum);
			for (int i = 0; i < w.length; i++) {
				k -= w[i];
				if (k < 0) return i;
			}
			return w.length - 1;
		}

		static <T> T pick(Random r, T[] v, int[] w) {
			return v[weighted(r, w)];
		}

		/** Dodatek, który odcina się od koloru bazowego (np. sznurki bluzy). */
		static int contrastPick(Random r, int base, int[] pal) {
			for (int tries = 0; tries < 8; tries++) {
				int c = rgb(pal[r.nextInt(pal.length)]);
				if (Math.abs(luma(c) - luma(base)) > 0.18) return c;
			}
			return luma(base) > 0.5 ? rgb(0x2E2A36) : WHITE;
		}
	}

	// ================================ malowanie ================================

	static final class Painter {
		final Look L;
		final Random r;
		final int[] px = new int[SIZE * SIZE];

		Painter(Look look, Random random) {
			L = look;
			r = random;
		}

		int[] paint() {
			skinBase();
			legwear();
			shoes();
			bottom();
			top();
			choker();
			chains();
			headHair();
			face();
			bodyHair();
			hatHair();
			hairAccessory();
			return px;
		}

		// ---------- prymitywy ----------

		void set(int x, int y, int c) {
			if (x >= 0 && x < SIZE && y >= 0 && y < SIZE) px[y * SIZE + x] = c;
		}

		void at(Box b, int col, int row, int c) {
			if (row < 0 || row >= b.h()) return;
			set(b.u() + Math.floorMod(col, b.loop()), b.v() + b.d() + row, c);
		}

		void topAt(Box b, int x, int z, int c) {
			if (x >= 0 && x < b.w() && z >= 0 && z < b.d()) set(b.u() + b.d() + x, b.v() + z, c);
		}

		void botAt(Box b, int x, int z, int c) {
			if (x >= 0 && x < b.w() && z >= 0 && z < b.d()) set(b.u() + b.d() + b.w() + x, b.v() + z, c);
		}

		/** Delikatny szum, żeby kolory nie były płaskie. */
		int n(int c, double amt) {
			return amt <= 0 ? c : mix(c, r.nextBoolean() ? 0xFFFFFFFF : 0xFF000000, r.nextDouble() * amt);
		}

		void ring(Box b, int row, int c, double amt) {
			for (int col = 0; col < b.loop(); col++) at(b, col, row, n(c, amt));
		}

		void rows(Box b, int from, int to, int c, double amt) {
			for (int row = from; row <= to; row++) ring(b, row, c, amt);
		}

		void topFace(Box b, int c, double amt) {
			for (int x = 0; x < b.w(); x++) for (int z = 0; z < b.d(); z++) topAt(b, x, z, n(c, amt));
		}

		void bottomFace(Box b, int c, double amt) {
			for (int x = 0; x < b.w(); x++) for (int z = 0; z < b.d(); z++) botAt(b, x, z, n(c, amt));
		}

		void fill(Box b, int c, double amt) {
			rows(b, 0, b.h() - 1, c, amt);
			topFace(b, c, amt);
			bottomFace(b, c, amt);
		}

		/** Boki tułowia/kończyn lekko ciemniejsze – daje objętość. */
		void sideShade(Box b, int from, int to, double t) {
			for (int col = 0; col < b.loop(); col++) {
				int f = b.face(col);
				if (f == 1) continue;
				for (int row = from; row <= to; row++) {
					int y = b.v() + b.d() + row, x = b.u() + col;
					int c = px[y * SIZE + x];
					if (c != 0) px[y * SIZE + x] = dark(c, f == 3 ? t * 0.6 : t);
				}
			}
		}

		// ---------- skóra ----------

		void skinBase() {
			for (Box b : new Box[]{HEAD, BODY, R_ARM, L_ARM, R_LEG, L_LEG}) fill(b, L.skin, 0.025);
			ring(BODY, 0, L.skinShade, 0.02); // cień pod brodą
			for (Box b : new Box[]{R_ARM, L_ARM, R_LEG, L_LEG})
				for (int i = 0; i < b.d(); i++)
					for (int row = 0; row < b.h(); row++) at(b, b.inner(i), row, n(dark(L.skin, 0.07), 0.02));
		}

		// ---------- twarz ----------

		void f(int x, int y, int c) {
			at(HEAD, HEAD.front(x), y, c);
		}

		void face() {
			int lash = rgb(0x2A1D27);
			int irisD = dark(L.eye, 0.4), irisL = light(L.eye, 0.25), white = rgb(0xFFFFFF);
			boolean big = L.bigEyes;
			for (int e = 0; e < 2; e++) {
				int x = e == 0 ? 1 : 5;
				int ox = e == 0 ? x : x + 1; // zewnętrzny kącik
				int ix = e == 0 ? x + 1 : x; // wewnętrzny kącik
				f(x, 3, lash);
				f(x + 1, 3, lash);
				if (big) {
					f(ox, 4, white);
					f(ix, 4, irisD);
					f(ox, 5, L.eye);
					f(ix, 5, irisL);
				} else {
					f(ox, 4, white);
					f(ix, 4, L.eye);
				}
				if (L.winged) f(e == 0 ? 0 : 7, 3, lash);
			}
			int blushRow = big ? 6 : 5, mouthRow = big ? 7 : 6;
			if (L.strongBlush) {
				for (int x = 1; x <= 6; x++) f(x, blushRow, n(L.blush, 0.02));
			} else {
				f(1, blushRow, L.blush);
				f(2, blushRow, L.blush);
				f(5, blushRow, L.blush);
				f(6, blushRow, L.blush);
			}
			if (L.heartMark) f(6, blushRow, L.style == Style.EGIRL ? rgb(0xC81E5A) : rgb(0xE8507E));
			int dm = dark(L.mouthC, 0.25);
			switch (L.mouth) {
				case 1 -> {
					f(3, mouthRow, dm);
					f(4, mouthRow, dm);
				}
				case 2 -> {
					f(3, mouthRow, dm);
					f(4, mouthRow, dm);
					if (mouthRow < 7) f(4, mouthRow + 1, rgb(0xF27A98));
				}
				default -> {
					f(3, mouthRow, L.mouthC);
					f(4, mouthRow, L.mouthC);
				}
			}
		}

		// ---------- włosy ----------

		/** Ile rzędów grzywki (od góry) na przodzie głowy – warstwa bazowa. */
		int[] baseBangs() {
			return switch (L.hair) {
				case LONG -> new int[]{2, 2, 2, 1, 1, 2, 2, 2};
				case SWEPT -> new int[]{2, 2, 2, 2, 2, 2, 2, 1};
				default -> new int[]{2, 2, 2, 2, 2, 2, 2, 2};
			};
		}

		/** Grzywka na zewnętrznej warstwie (3D). Kolumny 1,2,5,6 max 3 rzędy – nie zasłaniają oczu. */
		int[] hatBangs() {
			return switch (L.hair) {
				case BOB -> new int[]{5, 3, 3, 2, 2, 3, 3, 5};
				case FLUFFY -> new int[]{3 + r.nextInt(2), 2 + r.nextInt(2), 3, 2 + r.nextInt(2), 2 + r.nextInt(2), 3, 2 + r.nextInt(2), 3 + r.nextInt(2)};
				case SWEPT -> new int[]{5, 3, 3, 3, 3, 2, 2, 2};
				case HIME -> new int[]{7, 3, 3, 3, 3, 3, 3, 7};
				case LONG -> new int[]{6, 3, 2, 1, 1, 2, 3, 6};
				case WOLF -> new int[]{5, 3, 2, 3, 2, 3, 2, 5};
				case PONYTAIL -> new int[]{4, 3, 2, 2, 2, 2, 3, 4};
			};
		}

		final double[] strand = new double[32];

		int hc(boolean rightHalf, int col, int row, int len, boolean hat, boolean ring) {
			int c = rightHalf ? L.hairA : L.hairB;
			double s = strand[Math.floorMod(col, 32)];
			c = s >= 1 ? light(c, s - 1) : dark(c, 1 - s);
			if (ring && row == 2) c = light(c, 0.17);
			if (row == len - 1 && len > 2) c = dark(c, 0.13);
			if (hat) c = light(c, 0.05);
			return n(c, 0.02);
		}

		void headHair() {
			for (int i = 0; i < 32; i++) strand[i] = 0.91 + r.nextDouble() * 0.14;
			// wierzch głowy + przedziałek
			for (int x = 0; x < 8; x++)
				for (int z = 0; z < 8; z++) topAt(HEAD, x, z, hc(x < 4, x * 4 + z, 0, 8, false, false));
			int part = switch (L.hair) {
				case LONG, HIME -> 3;
				case SWEPT -> 2;
				default -> -1;
			};
			if (part >= 0) for (int z = 4; z < 8; z++) topAt(HEAD, part, z, dark(L.hairA, 0.22));
			// boki i tył w całości, przód – grzywka
			int[] bangs = baseBangs();
			for (int col = 0; col < HEAD.loop(); col++) {
				int face = HEAD.face(col);
				int len = face == 1 ? bangs[col - HEAD.d()] : 8;
				for (int row = 0; row < len; row++) at(HEAD, col, row, hc(HEAD.rightHalf(col), col, row, len, false, face != 1));
			}
			// cień skóry tuż pod grzywką
			for (int i = 0; i < 8; i++) f(i, bangs[i], dark(L.skin, 0.1));
		}

		void hatHair() {
			int[] bangs = hatBangs();
			for (int x = 0; x < 8; x++)
				for (int z = 0; z < 8; z++) topAt(HAT, x, z, hc(x < 4, x * 4 + z + 7, 0, 8, true, false));
			for (int col = 0; col < HAT.loop(); col++) {
				int face = HAT.face(col);
				int len;
				if (face == 1) len = bangs[col - HAT.d()];
				else {
					boolean shortHair = L.hair == Hair.FLUFFY || L.hair == Hair.BOB || L.hair == Hair.SWEPT;
					len = shortHair ? 7 + (r.nextInt(3) == 0 ? 1 : 0) : 8;
				}
				for (int row = 0; row < len; row++) at(HAT, col, row, hc(HAT.rightHalf(col), col + 3, row, len, true, face != 1));
			}
			// kucyk: gumka z tyłu głowy
			if (L.hair == Hair.PONYTAIL) {
				at(HAT, HAT.back(3), 5, L.accC == L.hairA ? rgb(0xF07FB4) : L.accC);
				at(HAT, HAT.back(4), 5, L.accC == L.hairA ? rgb(0xF07FB4) : L.accC);
			}
		}

		void bodyHair() {
			int[] len = switch (L.hair) {
				case LONG -> new int[]{6, 7, 8, 9, 9, 8, 7, 6};
				case HIME -> new int[]{6, 6, 6, 6, 6, 6, 6, 6};
				case WOLF -> new int[]{3, 4, 3, 5, 4, 5, 3, 4};
				case PONYTAIL -> new int[]{0, 0, 0, 10, 10, 0, 0, 0};
				default -> new int[]{1, 1, 1, 1, 1, 1, 1, 1};
			};
			for (int i = 0; i < 8; i++) {
				boolean rh = i >= 4;
				int l = len[i];
				for (int row = 0; row < l; row++) at(BODY, BODY.back(i), row, hc(rh, i * 3, row, l, false, false));
				if (l >= 3) for (int row = 0; row < l - 1; row++) at(JACKET, JACKET.back(i), row, hc(rh, i * 3 + 1, row, l - 1, true, false));
			}
			if (L.hair == Hair.PONYTAIL) { // zwężający się koniec
				at(BODY, BODY.back(2), 1, hc(false, 2, 1, 4, false, false));
				at(BODY, BODY.back(5), 1, hc(true, 5, 1, 4, false, false));
			}
			int wrap = switch (L.hair) {
				case LONG -> 7;
				case HIME -> 5;
				case WOLF -> 3;
				default -> 0;
			};
			for (int row = 0; row < wrap; row++) {
				at(BODY, BODY.rightSide(0), row, hc(true, 1, row, wrap, false, false));
				at(BODY, BODY.leftSide(3), row, hc(false, 9, row, wrap, false, false));
			}
			if (L.hair == Hair.LONG) { // kosmyki z przodu na ramionach
				for (int row = 0; row < 5; row++) {
					at(JACKET, JACKET.front(0), row, hc(true, 4, row, 5, true, false));
					at(JACKET, JACKET.front(7), row, hc(false, 11, row, 5, true, false));
				}
			}
		}

		void hairAccessory() {
			int c = L.accC, cd = dark(c, 0.3), cl = light(c, 0.3);
			if (luma(c) < 0.2) cl = light(c, 0.25);
			switch (L.acc) {
				case CLIPS -> {
					at(HAT, HAT.front(5), 1, c);
					at(HAT, HAT.front(6), 1, c);
					at(HAT, HAT.front(6), 0, cl);
				}
				case BOW_BACK -> {
					int[][] bow = {
						{1, 1, 0, 0, 1, 1},
						{1, 1, 2, 2, 1, 1},
						{1, 1, 0, 0, 1, 1},
						{0, 0, 1, 1, 0, 0}};
					for (int y = 0; y < bow.length; y++)
						for (int x = 0; x < 6; x++)
							if (bow[y][x] != 0) at(HAT, HAT.back(1 + x), 2 + y, bow[y][x] == 2 ? cd : (y == 0 ? cl : c));
				}
				case BOW_SIDE -> {
					int[][] bow = {
						{1, 0, 0, 1},
						{1, 2, 2, 1},
						{1, 0, 0, 1}};
					for (int y = 0; y < 3; y++)
						for (int x = 0; x < 4; x++)
							if (bow[y][x] != 0) at(HAT, HAT.leftSide(1 + x), y, bow[y][x] == 2 ? cd : c);
				}
				case HEADPHONES -> {
					int band = c, inner = luma(c) < 0.35 ? rgb(0xEE4F95) : rgb(0x2A2730);
					for (int x = 0; x < 8; x++) {
						topAt(HAT, x, 3, band);
						topAt(HAT, x, 4, dark(band, 0.12));
					}
					for (int side = 0; side < 2; side++) {
						for (int row = 0; row <= 2; row++)
							for (int i = 3; i <= 4; i++)
								at(HAT, side == 0 ? HAT.rightSide(i) : HAT.leftSide(i), row, i == 3 ? band : dark(band, 0.12));
						for (int row = 3; row <= 6; row++)
							for (int i = 2; i <= 5; i++) {
								boolean edge = row == 3 || row == 6 || i == 2 || i == 5;
								at(HAT, side == 0 ? HAT.rightSide(i) : HAT.leftSide(i), row, edge ? dark(band, 0.2) : inner);
							}
					}
				}
				default -> {
				}
			}
		}

		// ---------- góra ----------

		void torso(int c) {
			rows(BODY, 0, 7, c, 0.025);
			topFace(BODY, c, 0.02);
		}

		void sleeves(int c, int to) {
			for (Box a : ARMS) {
				rows(a, 0, to, c, 0.025);
				topFace(a, c, 0.02);
			}
		}

		void top() {
			switch (L.top) {
				case HOODIE -> hoodie();
				case SWEATER -> sweater(false);
				case STRIPED -> sweater(true);
				case SAILOR -> sailor();
				case TEE -> tee();
			}
			print();
			if (L.bracelet && L.top != Top.HOODIE) {
				ring(L_ARM, 10, L.braceletC, 0.02);
				if (L.style == Style.EGIRL) for (int c = 0; c < L_ARM.loop(); c += 2) at(L_ARM, c, 10, SILVER);
			}
		}

		void hoodie() {
			int c = L.topC, d1 = dark(c, 0.13), d2 = dark(c, 0.26), acc = L.topAcc;
			torso(c);
			sleeves(c, 10);
			sideShade(BODY, 0, 7, 0.08);
			for (Box a : ARMS) {
				ring(a, 9, d1, 0.02);
				ring(a, 10, d1, 0.02);
			}
			// kaptur na plecach
			for (int i = 1; i <= 6; i++)
				for (int row = 0; row <= 2; row++)
					at(BODY, BODY.back(i), row, n(row == 2 || i == 1 || i == 6 ? d2 : d1, 0.02));
			// wycięcie przy szyi
			at(BODY, BODY.front(2), 0, d2);
			at(BODY, BODY.front(5), 0, d2);
			at(BODY, BODY.front(3), 0, d1);
			at(BODY, BODY.front(4), 0, d1);
			// sznurki (tylko gdy nie ma nadruku – nie mieszczą się razem)
			if (L.print == Print.NONE)
				for (int row = 1; row <= 3; row++) {
					at(BODY, BODY.front(2), row, n(acc, 0.02));
					at(BODY, BODY.front(5), row, n(acc, 0.02));
				}
			// kieszeń kangurka
			for (int i = 1; i <= 6; i++) {
				at(BODY, BODY.front(i), 5, n(d1, 0.02));
				at(BODY, BODY.front(i), 6, n(d1, 0.02));
			}
			at(BODY, BODY.front(1), 5, d2);
			at(BODY, BODY.front(6), 5, d2);
			ring(BODY, 7, d1, 0.02);
			// warstwa zewnętrzna: luźny dół i mankiety, kaptur
			for (int col = 0; col < JACKET.loop(); col++) {
				at(JACKET, col, 7, n(c, 0.02));
				at(JACKET, col, 8, n(d1, 0.02));
			}
			for (int i = 1; i <= 6; i++)
				for (int row = 0; row <= 1; row++) at(JACKET, JACKET.back(i), row, n(i == 1 || i == 6 ? d1 : c, 0.02));
			for (Box s : SLEEVES) ring(s, 10, d1, 0.02);
		}

		void sweater(boolean striped) {
			int c = L.topC, d1 = dark(c, 0.13), acc = L.topAcc;
			torso(c);
			sleeves(c, 10);
			if (striped) {
				for (int row = 1; row <= 6; row += 2) ring(BODY, row, acc, 0.02);
				for (Box a : ARMS) for (int row = 1; row <= 9; row += 2) ring(a, row, acc, 0.02);
			}
			sideShade(BODY, 0, 7, 0.07);
			ring(BODY, 7, d1, 0.02);
			for (Box a : ARMS) ring(a, 10, d1, 0.02);
			if (L.style != Style.EGIRL && !striped) { // biały kołnierzyk
				for (int i = 2; i <= 5; i++) at(BODY, BODY.front(i), 0, WHITE);
				at(BODY, BODY.front(2), 1, WHITE);
				at(BODY, BODY.front(5), 1, WHITE);
				at(BODY, BODY.front(3), 1, dark(WHITE, 0.12));
				at(BODY, BODY.front(4), 1, dark(WHITE, 0.12));
			}
			for (int col = 0; col < JACKET.loop(); col++) at(JACKET, col, 7, n(d1, 0.02));
			for (Box s : SLEEVES) ring(s, 10, d1, 0.02);
		}

		void sailor() {
			int w = L.topC, k = L.topAcc, kd = dark(k, 0.2), ribbon = luma(k) < 0.3 || k == rgb(0x2E3B66) ? rgb(0xE8506A) : rgb(0xF7F6FA);
			if (k == rgb(0xF07FB4)) ribbon = rgb(0xE8506A);
			torso(w);
			boolean longSleeves = r.nextBoolean();
			sleeves(w, longSleeves ? 10 : 3);
			sideShade(BODY, 0, 7, 0.07);
			for (Box a : ARMS) {
				int cuff = longSleeves ? 9 : 3;
				ring(a, cuff, k, 0.02);
				if (longSleeves) ring(a, 10, k, 0.02);
			}
			// kołnierz z tyłu (z białym paskiem)
			for (int i = 0; i < 8; i++)
				for (int row = 0; row <= 3; row++) at(BODY, BODY.back(i), row, row == 2 ? WHITE : n(k, 0.02));
			for (int i = 0; i < 4; i++) {
				at(BODY, BODY.rightSide(i), 0, k);
				at(BODY, BODY.leftSide(i), 0, k);
			}
			// kołnierz z przodu w literę V + kokarda
			int[][] v = {{0, 1, 6, 7}, {1, 2, 5, 6}, {2, 3, 4, 5}};
			for (int row = 0; row < 3; row++) for (int i : v[row]) at(BODY, BODY.front(i), row, n(k, 0.02));
			at(BODY, BODY.front(3), 0, L.skin);
			at(BODY, BODY.front(4), 0, L.skin);
			at(BODY, BODY.front(3), 1, w);
			at(BODY, BODY.front(4), 1, w);
			at(BODY, BODY.front(2), 3, ribbon);
			at(BODY, BODY.front(3), 3, dark(ribbon, 0.2));
			at(BODY, BODY.front(4), 3, dark(ribbon, 0.2));
			at(BODY, BODY.front(5), 3, ribbon);
			at(BODY, BODY.front(3), 4, ribbon);
			at(BODY, BODY.front(4), 4, ribbon);
			// kokarda wystaje na warstwie zewnętrznej
			at(JACKET, JACKET.front(2), 3, ribbon);
			at(JACKET, JACKET.front(5), 3, ribbon);
			at(JACKET, JACKET.front(3), 3, dark(ribbon, 0.2));
			at(JACKET, JACKET.front(4), 3, dark(ribbon, 0.2));
			for (int i = 0; i < 8; i++) at(JACKET, JACKET.back(i), 0, n(k, 0.02));
			for (int i = 0; i < 8; i++) at(JACKET, JACKET.back(i), 1, n(kd, 0.02));
		}

		void tee() {
			int c = L.topC, d1 = dark(c, 0.15);
			torso(c);
			sleeves(c, 3);
			sideShade(BODY, 0, 7, 0.07);
			for (Box a : ARMS) ring(a, 3, d1, 0.02);
			if (L.underStripes) {
				for (Box a : ARMS) for (int row = 4; row <= 10; row++) ring(a, row, row % 2 == 0 ? L.underA : L.underB, 0.02);
			} else if (L.armWarmers) {
				for (Box a : ARMS) for (int row = 6; row <= 10; row++) ring(a, row, row % 2 == 0 ? L.underA : L.topAcc, 0.02);
				for (Box s : SLEEVES) ring(s, 6, L.underA, 0.02);
			}
			at(BODY, BODY.front(3), 0, L.skinShade);
			at(BODY, BODY.front(4), 0, L.skinShade);
			for (Box s : SLEEVES) ring(s, 3, d1, 0.02);
		}

		void print() {
			int c = L.topAcc;
			int[][] m;
			int ox, oy;
			switch (L.print) {
				case HEART -> {
					m = new int[][]{{1, 1, 0, 0, 1, 1}, {1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 0}, {0, 0, 1, 1, 0, 0}};
					ox = 1;
					oy = L.top == Top.HOODIE ? 1 : 2;
				}
				case SMALL_HEART -> {
					m = new int[][]{{1, 0, 1}, {1, 1, 1}, {0, 1, 0}};
					ox = 4;
					oy = 2;
				}
				case BAND -> {
					for (int row = 3; row <= 4; row++) ring(BODY, row, c, 0.02);
					for (Box a : ARMS) for (int row = 3; row <= 4; row++) if (L.top != Top.TEE || row < 3) ring(a, row, c, 0.02);
					return;
				}
				default -> {
					return;
				}
			}
			for (int y = 0; y < m.length; y++)
				for (int x = 0; x < m[y].length; x++)
					if (m[y][x] != 0) at(BODY, BODY.front(ox + x), oy + y, n(c, 0.02));
		}

		// ---------- dół ----------

		void bottom() {
			switch (L.bottom) {
				case SKIRT -> skirt(false);
				case PLAID -> skirt(true);
				case SHORTS -> shorts();
			}
		}

		int skirtPx(int col, int row, boolean plaid, boolean overlay) {
			int c = L.botC, d1 = dark(c, 0.16);
			int base = Math.floorMod(col, 2) == 0 ? c : d1; // plisy
			if (plaid) {
				boolean v = Math.floorMod(col, 4) == 1, h = Math.floorMod(row, 4) == 2;
				if (v && h) base = L.botAcc;
				else if (v || h) base = mix(base, L.botAcc, 0.45);
			}
			if (overlay) base = light(base, 0.04);
			return n(base, 0.02);
		}

		void skirt(boolean plaid) {
			int len = L.skirtLen;
			// tułów: rzędy 8..11 (pas w 8)
			for (int col = 0; col < BODY.loop(); col++)
				for (int row = 8; row <= 11; row++) at(BODY, col, row, skirtPx(col, row, plaid, false));
			ring(BODY, 8, dark(L.botC, 0.3), 0.02);
			bottomFace(BODY, dark(L.botC, 0.35), 0.02);
			for (Box l : LEGS)
				for (int col = 0; col < l.loop(); col++)
					for (int row = 0; row < len; row++) at(l, col, row, skirtPx(col, row + 12, plaid, false));
			// warstwa zewnętrzna: rozkloszowanie
			for (int col = 0; col < JACKET.loop(); col++)
				for (int row = 9; row <= 11; row++) at(JACKET, col, row, skirtPx(col, row, plaid, true));
			for (Box p : PANTS)
				for (int col = 0; col < p.loop(); col++)
					for (int row = 0; row <= len; row++)
						at(p, col, row, row == len ? dark(skirtPx(col, row + 12, plaid, true), 0.1) : skirtPx(col, row + 12, plaid, true));
		}

		void shorts() {
			int c = L.botC, d1 = dark(c, 0.15);
			rows(BODY, 8, 11, c, 0.03);
			ring(BODY, 8, d1, 0.02);
			bottomFace(BODY, d1, 0.02);
			for (Box l : LEGS) {
				rows(l, 0, 1, c, 0.03);
				ring(l, 1, light(c, 0.1), 0.02);
			}
			for (Box p : PANTS) ring(p, 1, light(c, 0.12), 0.02);
		}

		// ---------- nogi ----------

		void legwear() {
			int t = L.skirtLen + 2; // dwa rzędy gołych ud ("absolute territory")
			int a = L.legA, b = L.legB;
			switch (L.legs) {
				case STRIPED_HIGHS -> {
					for (Box l : LEGS) for (int row = t; row <= 11; row++) ring(l, row, (row - t) % 2 == 0 ? a : b, 0.02);
					for (Box p : PANTS) ring(p, t, a, 0.02);
				}
				case SOLID_HIGHS -> {
					for (Box l : LEGS) {
						rows(l, t, 11, a, 0.02);
						ring(l, t, b, 0.02);
					}
					for (Box p : PANTS) ring(p, t, b, 0.02);
				}
				case KNEE_SOCKS -> {
					for (Box l : LEGS) {
						rows(l, 7, 11, a, 0.02);
						ring(l, 7, b, 0.02);
					}
				}
				case FISHNET -> {
					int net = mix(L.skin, a, 0.8);
					for (Box l : LEGS)
						for (int col = 0; col < l.loop(); col++)
							for (int row = 0; row <= 11; row++)
								if (Math.floorMod(col + row, 4) == 0 || Math.floorMod(col - row, 4) == 0) at(l, col, row, net);
				}
				case STRIPED_TIGHTS -> {
					for (Box l : LEGS) for (int row = 0; row <= 11; row++) ring(l, row, row % 2 == 0 ? a : b, 0.02);
				}
			}
			if (L.sockBow) {
				for (Box l : LEGS) {
					at(l, l.outer(1), t, L.sockBowC);
					at(l, l.outer(2), t, L.sockBowC);
					at(l, l.outer(1), t + 1, dark(L.sockBowC, 0.2));
				}
			}
		}

		void shoes() {
			int c = L.shoeC, acc = L.shoeAcc, sole = L.soleC;
			switch (L.shoes) {
				case SNEAKERS -> {
					for (Box l : LEGS) {
						ring(l, 10, c, 0.02);
						ring(l, 11, sole, 0.02);
						at(l, l.front(1), 10, light(c, 0.5));
						at(l, l.front(2), 10, light(c, 0.5));
						at(l, l.outer(1), 10, acc);
						at(l, l.outer(2), 10, acc);
						at(l, l.front(0), 11, dark(sole, 0.1));
						bottomFace(l, dark(sole, 0.3), 0.02);
					}
				}
				case MARY_JANES -> {
					for (Box l : LEGS) {
						ring(l, 11, c, 0.02);
						for (int i = 0; i < 4; i++) at(l, l.front(i), 10, c);
						at(l, l.outer(3), 10, c);
						at(l, l.outer(2), 10, acc); // klamerka
						at(l, l.front(1), 11, light(c, 0.3)); // połysk
						bottomFace(l, sole, 0.02);
					}
				}
				case PLATFORMS -> {
					for (Box l : LEGS) {
						rows(l, 7, 10, c, 0.02);
						ring(l, 11, sole, 0.02);
						for (int row = 7; row <= 10; row++) {
							at(l, l.front(1), row, row % 2 == 0 ? rgb(0x8E8A98) : c);
							at(l, l.front(2), row, row % 2 == 1 ? rgb(0x8E8A98) : c);
						}
						at(l, l.outer(1), 8, acc);
						at(l, l.outer(2), 8, acc);
						bottomFace(l, dark(sole, 0.2), 0.02);
					}
					for (Box p : PANTS) {
						ring(p, 11, sole, 0.02);
						ring(p, 10, c, 0.02);
					}
				}
			}
		}

		// ---------- dodatki ----------

		void choker() {
			if (!L.choker) return;
			ring(BODY, 0, L.chokerC, 0.02);
			at(BODY, BODY.front(3), 0, L.charmC);
			at(BODY, BODY.front(4), 0, L.charmC);
			if (L.style == Style.EGIRL && L.charmC == SILVER) { // kółko O-ring
				at(BODY, BODY.front(3), 1, SILVER_D);
				at(BODY, BODY.front(4), 1, SILVER);
			} else {
				at(BODY, BODY.front(3), 1, dark(L.charmC, 0.15));
			}
		}

		void chains() {
			if (!L.chains) return;
			int[][] pts = {{0, 9}, {1, 10}, {2, 10}, {3, 9}};
			for (int k = 0; k < pts.length; k++) at(JACKET, JACKET.front(pts[k][0]), pts[k][1], k % 2 == 0 ? SILVER : SILVER_D);
			for (int i = 4; i < 8; i += 2) at(BODY, BODY.front(i), 8, SILVER);
			at(JACKET, JACKET.rightSide(2), 9, SILVER_D);
			at(JACKET, JACKET.rightSide(1), 10, SILVER);
		}
	}
}
