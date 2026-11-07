package pl.vsake.femboy.acc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import static pl.vsake.femboy.acc.Item.*;

/**
 * Geometria wszystkich dodatków – czysta Java (bez Minecrafta), żeby dało się ją sprawdzić i obejrzeć poza grą.
 * Układ jak w modelu gracza: jednostka = piksel skina, Y rośnie w dół, przód to -Z, prawa strona gracza to -X.
 * Głowa: x∈[-4,4], y∈[-8,0], z∈[-4,4] (włosy 0,5 px dalej). Tułów: x∈[-4,4], y∈[0,12], z∈[-2,2].
 * Każdy prostopadłościan dostaje swój kawałek tekstury 128x128 (pakowanie półkowe przy starcie).
 */
public final class Parts {
	private Parts() {
	}

	public static final int TEX_W = 128, TEX_H = 128;

	/** Do której części gracza przyczepiony jest element. */
	public enum Anchor {
		HEAD("head"), BODY("body"), RIGHT_ARM("right_arm"), LEFT_ARM("left_arm"), RIGHT_LEG("right_leg"), LEFT_LEG("left_leg");

		public final String part;

		Anchor(String part) {
			this.part = part;
		}
	}

	/** Łapki są inne dla chudych (3 px) i szerokich (4 px) rąk. */
	public enum Fit {ANY, WIDE, SLIM}

	/** Rodzaj animacji (Anim). */
	public enum Motion {NONE, EAR, EAR_PUPPY, EAR_BUNNY, EAR_BUNNY_TOP, TAIL_CAT, TAIL_BUSHY, TAIL_PUPPY, TAIL_BUNNY, PENDANT, HAIR, SKIRT_FRONT, SKIRT_BACK, SKIRT_SIDE, WING}

	public static final class Box {
		public final String tag;
		public final float x, y, z;
		public final int sx, sy, sz;
		/** Pogrubienie (jak warstwa skina) – nie zmienia kawałka tekstury. */
		public final float gx, gy, gz;
		public int u, v;

		Box(String tag, float x, float y, float z, int sx, int sy, int sz, float gx, float gy, float gz) {
			this.tag = tag;
			this.x = x;
			this.y = y;
			this.z = z;
			this.sx = sx;
			this.sy = sy;
			this.sz = sz;
			this.gx = gx;
			this.gy = gy;
			this.gz = gz;
		}

		public int texW() {
			return 2 * (sx + sz);
		}

		public int texH() {
			return sz + sy;
		}
	}

	public static final class Piece {
		public final String name;
		/** Widoczny, gdy wygląd ma którykolwiek z tych dodatków. */
		public final EnumSet<Item> items;
		public final Fit fit;
		public final Anchor anchor;
		/** Poprzedni segment (ogony, zgięte uszy); null = przyczepiony do części gracza. */
		public final Piece parent;
		public final float ox, oy, oz, pitch, yaw, roll;
		/** -1 prawa strona, +1 lewa, 0 środek. */
		public final int side;
		/** Numer segmentu w ogonie. */
		public final int index;
		public final Motion motion;
		public final List<Box> boxes = new ArrayList<>();

		Piece(String name, EnumSet<Item> items, Fit fit, Anchor anchor, Piece parent, float ox, float oy, float oz,
			  float pitch, float yaw, float roll, int side, int index, Motion motion) {
			this.name = name;
			this.items = items;
			this.fit = fit;
			this.anchor = parent != null ? parent.anchor : anchor;
			this.parent = parent;
			this.ox = ox;
			this.oy = oy;
			this.oz = oz;
			this.pitch = pitch;
			this.yaw = yaw;
			this.roll = roll;
			this.side = side;
			this.index = index;
			this.motion = motion;
		}

		Piece box(String tag, float x, float y, float z, int sx, int sy, int sz) {
			return box(tag, x, y, z, sx, sy, sz, 0f, 0f, 0f);
		}

		Piece box(String tag, float x, float y, float z, int sx, int sy, int sz, float g) {
			return box(tag, x, y, z, sx, sy, sz, g, g, g);
		}

		Piece box(String tag, float x, float y, float z, int sx, int sy, int sz, float gx, float gy, float gz) {
			boxes.add(new Box(tag, x, y, z, sx, sy, sz, gx, gy, gz));
			return this;
		}

		/** Czy ten element należy do wyglądu (bez sprawdzania chudych/szerokich rąk). */
		public boolean usedBy(Look look) {
			for (Item it : items) if (look.has(it)) return true;
			return false;
		}

		public boolean visible(Look look, boolean slim) {
			if (fit == Fit.WIDE && slim || fit == Fit.SLIM && !slim) return false;
			return usedBy(look);
		}

		public Item item() {
			return items.iterator().next();
		}
	}

	public static final List<Piece> PIECES;

	static {
		List<Piece> l = new ArrayList<>();
		build(l);
		pack(l);
		PIECES = Collections.unmodifiableList(l);
	}

	private static Piece root(List<Piece> l, String name, EnumSet<Item> items, Fit fit, Anchor a, float ox, float oy, float oz,
							  float pitch, float yaw, float roll, int side, Motion m) {
		Piece p = new Piece(name, items, fit, a, null, ox, oy, oz, pitch, yaw, roll, side, 0, m);
		l.add(p);
		return p;
	}

	private static Piece child(List<Piece> l, String name, Piece parent, float ox, float oy, float oz,
							   float pitch, float yaw, float roll, int index, Motion m) {
		Piece p = new Piece(name, parent.items, parent.fit, null, parent, ox, oy, oz, pitch, yaw, roll, parent.side, index, m);
		l.add(p);
		return p;
	}

	private static void build(List<Piece> l) {
		// ======================== uszy (na głowie, przy górnej krawędzi; podstawa lekko w włosach) ========================
		for (int s : new int[]{-1, 1}) {
			String sd = s < 0 ? "r" : "l";
			// kocie: trójkąt 4x5, odchylone na boki i lekko "otwarte" do przodu
			root(l, "ear_cat_" + sd, EnumSet.of(EARS_CAT), Fit.ANY, Anchor.HEAD, s * 2.5f, -7.9f, -0.5f, -0.05f, -s * 0.3f, s * 0.25f, s, Motion.EAR)
				.box("r0", -2, -2, -0.5f, 4, 2, 1)
				.box("r1", -1.5f, -3, -0.5f, 3, 1, 1)
				.box("r2", -1, -4, -0.5f, 2, 1, 1)
				.box("r3", -0.5f, -5, -0.5f, 1, 1, 1);
			// lisie: duże trójkąty z szeroką podstawą, jasnym środkiem i ciemnym czubkiem
			root(l, "ear_fox_" + sd, EnumSet.of(EARS_FOX), Fit.ANY, Anchor.HEAD, s * 2.8f, -7.9f, -0.3f, -0.08f, -s * 0.22f, s * 0.3f, s, Motion.EAR)
				.box("r0", -2.5f, -2, -0.5f, 5, 2, 1)
				.box("r1", -2, -3, -0.5f, 4, 1, 1)
				.box("r2", -1.5f, -4, -0.5f, 3, 1, 1)
				.box("r3", -1, -5, -0.5f, 2, 1, 1)
				.box("r4", -0.5f, -6, -0.5f, 1, 1, 1);
			// wilcze: szerokie, ścięte u góry
			root(l, "ear_wolf_" + sd, EnumSet.of(EARS_WOLF), Fit.ANY, Anchor.HEAD, s * 2.6f, -7.9f, -0.3f, -0.1f, -s * 0.2f, s * 0.2f, s, Motion.EAR)
				.box("r0", -2, -2, -0.5f, 4, 2, 1)
				.box("r1", -1.5f, -4, -0.5f, 3, 2, 1)
				.box("r2", -1, -5, -0.5f, 2, 1, 1);
			// psie: zakładka na czubku głowy + szerokie oklapnięte ucho przy policzku (tuż nad włosami)
			float fold = s < 0 ? -0.5f : -1.5f; // zakładka idzie w stronę środka głowy
			root(l, "ear_puppy_" + sd, EnumSet.of(EARS_PUPPY), Fit.ANY, Anchor.HEAD, s * 4.95f, -7.6f, -0.5f, 0f, 0f, -s * 0.1f, s, Motion.EAR_PUPPY)
				.box("fold", fold, -1, -2, 2, 1, 4)
				.box("flap", -0.5f, 0, -2, 1, 6, 4)
				.box("tip", -0.5f, 6, -1.5f, 1, 1, 3);
			// królicze: długie, z zagięciem w połowie
			Piece bunny = root(l, "ear_bunny_" + sd, EnumSet.of(EARS_BUNNY), Fit.ANY, Anchor.HEAD, s * 2.0f, -7.9f, 0f, -0.1f, -s * 0.1f, s * 0.12f, s, Motion.EAR_BUNNY)
				.box("lo", -1.5f, -4, -0.5f, 3, 4, 1);
			child(l, "ear_bunny_top_" + sd, bunny, 0, -4, 0, -0.22f, 0f, s * 0.1f, 1, Motion.EAR_BUNNY_TOP)
				.box("up", -1.5f, -3, -0.5f, 3, 3, 1)
				.box("tip", -1, -4, -0.5f, 2, 1, 1);
			// misie: małe, okrągłe, szeroko rozstawione
			root(l, "ear_bear_" + sd, EnumSet.of(EARS_BEAR), Fit.ANY, Anchor.HEAD, s * 3.2f, -7.9f, -0.3f, -0.05f, -s * 0.15f, s * 0.2f, s, Motion.EAR)
				.box("b0", -1.5f, -2, -0.5f, 3, 2, 1)
				.box("b1", -1, -3, -0.5f, 2, 1, 1);
			// mysie: duże, okrągłe „talerzyki”
			root(l, "ear_mouse_" + sd, EnumSet.of(EARS_MOUSE), Fit.ANY, Anchor.HEAD, s * 3.0f, -7.9f, -0.2f, -0.05f, -s * 0.2f, s * 0.35f, s, Motion.EAR)
				.box("m0", -2, -3, -0.5f, 4, 3, 1)
				.box("m1", -1.5f, -4, -0.5f, 3, 1, 1)
				.box("m2", -1.5f, 0, -0.5f, 3, 1, 1);
		}

		// ======================== ogony (dół pleców, segmenty jeden za drugim) ========================
		Piece seg = root(l, "tail_cat_0", EnumSet.of(TAIL_CAT), Fit.ANY, Anchor.BODY, 0, 10.5f, 1.8f, -0.7f, 0, 0, 0, Motion.TAIL_CAT)
			.box("s0", -1, -1, -0.5f, 2, 2, 3);
		for (int i = 1; i < 5; i++)
			seg = child(l, "tail_cat_" + i, seg, 0, 0, 2.5f, 0.45f, 0, 0, i, Motion.TAIL_CAT).box("s" + i, -1, -1, -0.5f, 2, 2, 3);

		bushy(l, "tail_fox_", TAIL_FOX, -1.0f, 0.25f);
		bushy(l, "tail_wolf_", TAIL_WOLF, -1.2f, 0.2f);

		seg = root(l, "tail_puppy_0", EnumSet.of(TAIL_PUPPY), Fit.ANY, Anchor.BODY, 0, 10.2f, 1.8f, 0.5f, 0, 0, 0, Motion.TAIL_PUPPY)
			.box("s0", -1, -1, -0.5f, 2, 2, 3);
		seg = child(l, "tail_puppy_1", seg, 0, 0, 2.5f, 0.4f, 0, 0, 1, Motion.TAIL_PUPPY).box("s1", -1, -1, -0.5f, 2, 2, 3);
		child(l, "tail_puppy_2", seg, 0, 0, 2.5f, 0.35f, 0, 0, 2, Motion.TAIL_PUPPY).box("s2", -1, -1, -0.5f, 2, 2, 2);

		// diabelski: cienki, długi, zakończony grotem
		seg = root(l, "tail_devil_0", EnumSet.of(TAIL_DEVIL), Fit.ANY, Anchor.BODY, 0, 10.5f, 1.8f, -0.9f, 0, 0, 0, Motion.TAIL_CAT)
			.box("s0", -0.5f, -0.5f, -0.5f, 1, 1, 3);
		for (int i = 1; i < 5; i++)
			seg = child(l, "tail_devil_" + i, seg, 0, 0, 2.5f, 0.38f, 0, 0, i, Motion.TAIL_CAT).box("s" + i, -0.5f, -0.5f, -0.5f, 1, 1, 3);
		child(l, "tail_devil_tip", seg, 0, 0, 2.5f, 0.2f, 0, 0, 5, Motion.TAIL_CAT)
			.box("spade", -1.5f, -0.5f, -0.5f, 3, 1, 2)
			.box("point", -0.5f, -0.5f, 1.5f, 1, 1, 1);

		root(l, "tail_bunny", EnumSet.of(TAIL_BUNNY), Fit.ANY, Anchor.BODY, 0, 10.5f, 2.0f, 0, 0, 0, 0, Motion.TAIL_BUNNY)
			.box("puff", -1.5f, -1.5f, 0, 3, 3, 2)
			.box("end", -1, -1, 2, 2, 2, 1);

		// ======================== szyja: obroża/choker na górze tułowia + zawieszka z przodu ========================
		root(l, "collar", EnumSet.of(NECK_BELL, NECK_TAG, NECK_HEART, NECK_BOW, NECK_LOCK, NECK_SPIKES), Fit.ANY, Anchor.BODY, 0, 0, 0, 0, 0, 0, 0, Motion.NONE)
			.box("band", -4, 0.5f, -2, 8, 1, 4, 0.45f, 0.12f, 0.45f);
		root(l, "bell", EnumSet.of(NECK_BELL), Fit.ANY, Anchor.BODY, 0, 1.5f, -2.45f, 0, 0, 0, 0, Motion.PENDANT)
			.box("bell", -1, -0.1f, -2, 2, 2, 2);
		root(l, "bone", EnumSet.of(NECK_TAG), Fit.ANY, Anchor.BODY, 0, 1.5f, -2.45f, 0, 0, 0, 0, Motion.PENDANT)
			.box("ring", -0.5f, -0.4f, -1, 1, 2, 1)
			.box("knob_r", -2, 0.6f, -1, 1, 3, 1)
			.box("knob_l", 1, 0.6f, -1, 1, 3, 1)
			.box("bar", -1, 1.6f, -1, 2, 1, 1);
		heart(root(l, "heart", EnumSet.of(NECK_HEART), Fit.ANY, Anchor.BODY, 0, 1.5f, -2.45f, 0, 0, 0, 0, Motion.PENDANT), -1f);
		bow(root(l, "neckbow", EnumSet.of(NECK_BOW), Fit.ANY, Anchor.BODY, 0, 1.0f, -2.45f, 0, 0, 0, 0, Motion.NONE), -1f);
		root(l, "lock", EnumSet.of(NECK_LOCK), Fit.ANY, Anchor.BODY, 0, 1.5f, -2.45f, 0, 0, 0, 0, Motion.PENDANT)
			.box("shackle", -0.5f, -0.4f, -1, 1, 1, 1)
			.box("body", -1, 0.6f, -1.2f, 2, 2, 1);
		Piece spikes = root(l, "spikes", EnumSet.of(NECK_SPIKES), Fit.ANY, Anchor.BODY, 0, 0.5f, -2.5f, 0, 0, 0, 0, Motion.NONE);
		for (int i = 0; i < 4; i++) spikes.box("sp" + i, -3.5f + i * 2, 0, -0.6f, 1, 1, 1);

		// ======================== włosy: kokardka na czubku głowy / spinka nad prawym okiem ========================
		bow(root(l, "hairbow", EnumSet.of(HEAD_BOW), Fit.ANY, Anchor.HEAD, 2.6f, -9.0f, -2.4f, -0.15f, -0.2f, 0.3f, 1, Motion.HAIR), -0.5f);
		heart(root(l, "clip", EnumSet.of(HEAD_CLIP), Fit.ANY, Anchor.HEAD, -2.6f, -7.6f, -4.55f, 0, 0, -0.2f, -1, Motion.HAIR), -1f);
		// aureolka: złote kółko unoszące się nad głową
		root(l, "halo", EnumSet.of(HEAD_HALO), Fit.ANY, Anchor.HEAD, 0, -11.5f, 0, 0, 0, 0, 0, Motion.HAIR)
			.box("n", -3, 0, -3, 6, 1, 1)
			.box("s", -3, 0, 2, 6, 1, 1)
			.box("w", -3, 0, -2, 1, 1, 4)
			.box("e", 2, 0, -2, 1, 1, 4);
		// diabelskie różki: zakrzywione do środka
		for (int s : new int[]{-1, 1}) {
			root(l, s < 0 ? "horn_r" : "horn_l", EnumSet.of(HEAD_HORNS), Fit.ANY, Anchor.HEAD, s * 2.4f, -8.2f, -1.5f, 0.15f, 0, s * 0.35f, s, Motion.HAIR)
				.box("h0", -1, -1, -1, 2, 1, 2)
				.box("h1", -0.5f, -2.5f, -0.5f, 1, 2, 1)
				.box("h2", -0.5f + -s * 0.4f, -3.3f, -0.5f, 1, 1, 1);
		}
		// tiara: złoty pasek z trzema ząbkami nad czołem
		root(l, "tiara", EnumSet.of(HEAD_TIARA), Fit.ANY, Anchor.HEAD, 0, -8.2f, -4.0f, -0.35f, 0, 0, 0, Motion.HAIR)
			.box("band", -3, -0.5f, -0.5f, 6, 1, 1)
			.box("p_c", -0.5f, -2.5f, -0.5f, 1, 2, 1)
			.box("p_r", -2.5f, -1.5f, -0.5f, 1, 1, 1)
			.box("p_l", 1.5f, -1.5f, -0.5f, 1, 1, 1);
		// kwiatek nad lewym okiem (spinka jest nad prawym)
		root(l, "flower", EnumSet.of(HEAD_FLOWER), Fit.ANY, Anchor.HEAD, 2.7f, -7.0f, -4.55f, 0, 0, 0.2f, 1, Motion.HAIR)
			.box("p_t", -0.5f, -1.5f, -1, 1, 1, 1)
			.box("p_b", -0.5f, 0.5f, -1, 1, 1, 1)
			.box("p_r", -1.5f, -0.5f, -1, 1, 1, 1)
			.box("p_l", 0.5f, -0.5f, -1, 1, 1, 1)
			.box("c", -0.5f, -0.5f, -1.3f, 1, 1, 1);

		// ======================== twarz: okulary / maseczka (przed warstwą włosów) ========================
		root(l, "glasses", EnumSet.of(FACE_GLASSES), Fit.ANY, Anchor.HEAD, 0, -4.0f, -4.6f, 0, 0, 0, 0, Motion.HAIR)
			.box("r_top", -3.5f, -1, -0.5f, 3, 1, 1)
			.box("r_bot", -3.5f, 1, -0.5f, 3, 1, 1)
			.box("l_top", 0.5f, -1, -0.5f, 3, 1, 1)
			.box("l_bot", 0.5f, 1, -0.5f, 3, 1, 1)
			.box("bridge", -0.5f, -1, -0.5f, 1, 1, 1)
			.box("arm_r", -4.7f, -1, -0.3f, 1, 1, 5)
			.box("arm_l", 3.7f, -1, -0.3f, 1, 1, 5);
		root(l, "mask", EnumSet.of(FACE_MASK), Fit.ANY, Anchor.HEAD, 0, -2.0f, -4.6f, 0, 0, 0, 0, Motion.HAIR)
			.box("cloth", -3, -1, -0.5f, 6, 3, 1)
			.box("strap_r", -4.7f, -0.5f, -0.3f, 1, 1, 4)
			.box("strap_l", 3.7f, -0.5f, -0.3f, 1, 1, 4);

		// ======================== plecy: skrzydła (płaskie panele, trzepoczą) ========================
		for (int s : new int[]{-1, 1}) {
			String sd = s < 0 ? "r" : "l";
			wing(root(l, "wing_angel_" + sd, EnumSet.of(BACK_ANGEL), Fit.ANY, Anchor.BODY, s * 1.5f, 2.0f, 2.3f, 0, s * -0.55f, 0, s, Motion.WING), s,
				new int[][]{{5, -2, 1}, {7, -1, 3}, {6, 2, 2}, {4, 4, 2}, {2, 6, 1}});
			wing(root(l, "wing_bat_" + sd, EnumSet.of(BACK_BAT), Fit.ANY, Anchor.BODY, s * 1.5f, 2.0f, 2.3f, 0, s * -0.6f, 0, s, Motion.WING), s,
				new int[][]{{8, -1, 1}, {7, 0, 2}, {5, 2, 2}, {3, 4, 1}});
			wing(root(l, "wing_butterfly_" + sd, EnumSet.of(BACK_BUTTERFLY), Fit.ANY, Anchor.BODY, s * 1.2f, 3.0f, 2.3f, 0, s * -0.5f, 0, s, Motion.WING), s,
				new int[][]{{5, -3, 2}, {6, -1, 2}, {4, 1, 1}, {4, 2, 2}, {3, 4, 1}});
		}

		// ======================== nogi: zakolanówki (nad warstwą spodni) z gumką u góry ========================
		for (int s : new int[]{-1, 1}) {
			root(l, s < 0 ? "sock_r" : "sock_l", EnumSet.of(LEGS_SOCKS, LEGS_STRIPES), Fit.ANY, s < 0 ? Anchor.RIGHT_LEG : Anchor.LEFT_LEG,
				0, 0, 0, 0, 0, 0, s, Motion.NONE)
				.box("sock", -2, 2, -2, 4, 10, 4, 0.35f)
				.box("cuff", -2, 2, -2, 4, 1, 4, 0.5f, 0.15f, 0.5f);
		}

		// ======================== ręce: ocieplacze na przedramionach (pod łapkami) ========================
		for (int s : new int[]{-1, 1}) {
			Anchor a = s < 0 ? Anchor.RIGHT_ARM : Anchor.LEFT_ARM;
			String sd = s < 0 ? "r" : "l";
			float wx = s < 0 ? -3 : -1, sx = s < 0 ? -2 : -1;
			EnumSet<Item> wm = EnumSet.of(ARMS_WARMERS, ARMS_STRIPES);
			root(l, "warmer_" + sd + "_wide", wm, Fit.WIDE, a, 0, 0, 0, 0, 0, 0, s, Motion.NONE)
				.box("knit", wx, 3, -2, 4, 5, 4, 0.3f)
				.box("cuff", wx, 3, -2, 4, 1, 4, 0.45f, 0.1f, 0.45f);
			root(l, "warmer_" + sd + "_slim", wm, Fit.SLIM, a, 0, 0, 0, 0, 0, 0, s, Motion.NONE)
				.box("knit", sx, 3, -2, 3, 5, 4, 0.3f)
				.box("cuff", sx, 3, -2, 3, 1, 4, 0.45f, 0.1f, 0.45f);
		}

		// ======================== łapki na dłoniach (dół ręki), osobno dla chudych i szerokich ========================
		for (int s : new int[]{-1, 1}) {
			Anchor a = s < 0 ? Anchor.RIGHT_ARM : Anchor.LEFT_ARM;
			String sd = s < 0 ? "r" : "l";
			float wx = s < 0 ? -3 : -1, sx = s < 0 ? -2 : -1;
			root(l, "paw_" + sd + "_wide", EnumSet.of(PAWS), Fit.WIDE, a, 0, 0, 0, 0, 0, 0, s, Motion.NONE)
				.box("glove", wx, 6, -2, 4, 4, 4, 0.35f)
				.box("cuff", wx, 6, -2, 4, 1, 4, 0.55f, 0.2f, 0.55f);
			root(l, "paw_" + sd + "_slim", EnumSet.of(PAWS), Fit.SLIM, a, 0, 0, 0, 0, 0, 0, s, Motion.NONE)
				.box("glove", sx, 6, -2, 3, 4, 4, 0.35f)
				.box("cuff", sx, 6, -2, 3, 1, 4, 0.55f, 0.2f, 0.55f);
		}

		// ======================== plisowana spódniczka: pasek + 4 rozkloszowane panele ========================
		EnumSet<Item> sk = EnumSet.of(SKIRT);
		root(l, "skirt_waist", sk, Fit.ANY, Anchor.BODY, 0, 11, 0, 0, 0, 0, 0, Motion.NONE)
			.box("waist", -4, 0, -2, 8, 1, 4, 0.6f, 0.1f, 0.6f);
		root(l, "skirt_front", sk, Fit.ANY, Anchor.BODY, 0, 11, -2.4f, -0.22f, 0, 0, 0, Motion.SKIRT_FRONT)
			.box("panel", -5, 0, -0.5f, 10, 5, 1);
		root(l, "skirt_back", sk, Fit.ANY, Anchor.BODY, 0, 11, 2.4f, 0.22f, 0, 0, 0, Motion.SKIRT_BACK)
			.box("panel", -5, 0, -0.5f, 10, 5, 1);
		root(l, "skirt_right", sk, Fit.ANY, Anchor.BODY, -4.4f, 11, 0, 0, 0, 0.22f, -1, Motion.SKIRT_SIDE)
			.box("side", -0.5f, 0, -3, 1, 5, 6);
		root(l, "skirt_left", sk, Fit.ANY, Anchor.BODY, 4.4f, 11, 0, 0, 0, -0.22f, 1, Motion.SKIRT_SIDE)
			.box("side", -0.5f, 0, -3, 1, 5, 6);
	}

	/** Puszysty ogon (lis, wilk): grubieje, potem się zwęża; wisi nisko i lekko podwija się do góry. */
	private static void bushy(List<Piece> l, String prefix, Item item, float basePitch, float curl) {
		Piece seg = root(l, prefix + "0", EnumSet.of(item), Fit.ANY, Anchor.BODY, 0, 10.5f, 1.8f, basePitch, 0, 0, 0, Motion.TAIL_BUSHY)
			.box("s0", -1.5f, -1.5f, -0.5f, 3, 3, 3);
		seg = child(l, prefix + "1", seg, 0, 0, 2.5f, curl, 0, 0, 1, Motion.TAIL_BUSHY).box("s1", -2, -2, -0.5f, 4, 4, 4);
		seg = child(l, prefix + "2", seg, 0, 0, 3.5f, curl, 0, 0, 2, Motion.TAIL_BUSHY).box("s2", -2, -2, -0.5f, 4, 4, 4);
		seg = child(l, prefix + "3", seg, 0, 0, 3.5f, curl, 0, 0, 3, Motion.TAIL_BUSHY).box("s3", -1.5f, -1.5f, -0.5f, 3, 3, 3);
		child(l, prefix + "4", seg, 0, 0, 2.5f, curl * 0.8f, 0, 0, 4, Motion.TAIL_BUSHY).box("s4", -1, -1, -0.5f, 2, 2, 2);
	}

	/**
	 * Skrzydło z poziomych pasków grubości 1 px: każdy wiersz {szerokość, y, wysokość}.
	 * Rozkłada się od pleców na zewnątrz (prawe skrzydło w -X, lewe w +X).
	 */
	private static void wing(Piece p, int side, int[][] rows) {
		for (int i = 0; i < rows.length; i++) {
			int w = rows[i][0], y = rows[i][1], h = rows[i][2];
			p.box("w" + i, side < 0 ? -w : 0, y, -0.5f, w, h, 1);
		}
	}

	/** Pikselowe serduszko 3x3 (dwa łuki, środek, czubek), grubości 1 px od z0 do z0+1. */
	private static void heart(Piece p, float z0) {
		p.box("lobe_r", -1.5f, 0, z0, 1, 1, 1)
			.box("lobe_l", 0.5f, 0, z0, 1, 1, 1)
			.box("mid", -1.5f, 1, z0, 3, 1, 1)
			.box("tip", -0.5f, 2, z0, 1, 1, 1);
	}

	/** Kokardka 5x3: dwie pętelki i wysunięty węzełek. */
	private static void bow(Piece p, float z0) {
		p.box("loop_r", -2.5f, -1.5f, z0, 2, 3, 1)
			.box("loop_l", 0.5f, -1.5f, z0, 2, 3, 1)
			.box("knot", -0.5f, -1, z0 - 0.4f, 1, 2, 1);
	}

	/** Pakowanie półkowe: najwyższe kawałki najpierw. */
	private static void pack(List<Piece> pieces) {
		List<Box> all = new ArrayList<>();
		for (Piece p : pieces) all.addAll(p.boxes);
		all.sort(Comparator.comparingInt(Box::texH).reversed().thenComparing(Comparator.comparingInt(Box::texW).reversed()));
		int x = 0, y = 0, rowH = 0;
		for (Box b : all) {
			if (b.texW() > TEX_W) throw new IllegalStateException("Za szeroki element dodatku: " + b.tag);
			if (x + b.texW() > TEX_W) {
				x = 0;
				y += rowH;
				rowH = 0;
			}
			b.u = x;
			b.v = y;
			x += b.texW();
			rowH = Math.max(rowH, b.texH());
		}
		if (y + rowH > TEX_H) throw new IllegalStateException("Tekstura dodatków za mała: potrzeba " + (y + rowH) + " px wysokości");
	}

	public static Piece byName(String name) {
		for (Piece p : PIECES) if (p.name.equals(name)) return p;
		return null;
	}
}
