package pl.vsake.femboy.fx;

/**
 * Matematyka animacji ciała – czysta Java, wspólna dla gry i testów. Bierze pozę z gry (Rig) i przerabia ją:
 * w = jak mocno (0..1, płynne wejście/wyjście), t = sekundy od początku animacji.
 * Układ modelu gracza: Y w dół, przód to -Z, prawa ręka przy -X; ujemny pitch kończyny = do przodu,
 * dodatni roll prawej ręki = w bok na zewnątrz. Stopy są 24 px pod szyją (root).
 */
public final class PoseMath {
	private PoseMath() {
	}

	private static final float HALF_PI = 1.5707964f;

	/** Rodzaje póz we dwoje (fx/Bonds). */
	public static final int PAIR_NONE = 0, PAIR_HUG = 1, PAIR_HANDS = 2, PAIR_CARRY_HOLD = 3, PAIR_CARRIED = 4,
		PAIR_PIGGY_HOLD = 5, PAIR_PIGGY_RIDER = 6;

	public static void apply(Pose p, float w, float t, Rig r) {
		if (p == null || w <= 0f) return;
		switch (p) {
			case SIT -> sit(r, w, t);
			case KNEAD -> {
				sit(r, w, t);
				// łapki na zmianę wciskają się w „kocyk” przed sobą
				float k = sin(t * 6.2f);
				r.rightArm.to(-1.05f + 0.32f * k, -0.12f, 0f, w);
				r.leftArm.to(-1.05f - 0.32f * k, 0.12f, 0f, w);
				r.rightArm.move(0, 0.8f * w * Math.max(0f, k), 0);
				r.leftArm.move(0, 0.8f * w * Math.max(0f, -k), 0);
				lean(r, 0.2f, w);
				r.head.add(0.3f * w, 0f, 0.05f * w * sin(t * 3.1f));
			}
			case LOAF -> {
				stand(r, w);
				// tułów poziomo, głowa do przodu; nogi podwinięte pod brzuch, łapki przed sobą jak u sfinksa
				r.root.to(HALF_PI, 0f, 0f, w).move(0, (18f + 0.25f * sin(t * 1.6f)) * w, -2f * w);
				r.head.to(-1.4f + 0.06f * sin(t * 0.7f), 0f, 0f, w);
				r.rightLeg.to(-2.75f, 0f, 0f, w);
				r.leftLeg.to(-2.75f, 0f, 0f, w);
				r.rightArm.to(-2.9f, 0f, 0.12f, w).move(0, 0, -1.5f * w);
				r.leftArm.to(-2.9f, 0f, -0.12f, w).move(0, 0, -1.5f * w);
			}
			case BELLY -> {
				stand(r, w);
				// na plecach, stopami do przodu; łapki i nogi w górze, lekkie kołysanie na boki
				r.root.to(-HALF_PI, 0f, 0.14f * sin(t * 2.2f), w).move(0, 20.5f * w, 8f * w);
				r.head.to(0.5f + 0.08f * sin(t * 1.3f), 0f, 0.18f * sin(t * 1.1f), w);
				float k = sin(t * 5f);
				r.rightArm.to(-1.9f + 0.25f * k, 0f, 0.35f, w);
				r.leftArm.to(-1.9f - 0.25f * k, 0f, -0.35f, w);
				float l = sin(t * 3.6f);
				r.rightLeg.to(-1.15f + 0.3f * l, 0f, 0.25f, w);
				r.leftLeg.to(-1.15f - 0.3f * l, 0f, -0.25f, w);
			}
			case NAP -> {
				stand(r, w);
				// na lewym boku, zwinięty w kłębek; spokojny oddech
				r.root.to(0f, 0f, HALF_PI, w).move(8f * w, (16f + 0.2f * sin(t * 1.2f)) * w, 0);
				lean(r, 0.3f, w);
				r.head.to(0.6f, 0f, 0f, w);
				r.rightLeg.to(-1.4f, 0f, 0f, w);
				r.leftLeg.to(-1.2f, 0f, 0f, w);
				r.rightArm.to(-1.05f, 0f, 0f, w);
				r.leftArm.to(-0.75f, 0f, 0f, w);
			}
			case BEG -> {
				stand(r, w);
				// na kolanach, łapki przed piersią, główka przechylona
				r.root.move(0, 10f * w, 0);
				r.rightLeg.to(1.5f, 0.12f, 0f, w);
				r.leftLeg.to(1.5f, -0.12f, 0f, w);
				float k = sin(t * 7f);
				r.rightArm.to(-1.3f + 0.07f * k, -0.38f, 0f, w);
				r.leftArm.to(-1.3f - 0.07f * k, 0.38f, 0f, w);
				r.head.add(-0.15f * w, 0f, 0.2f * w * sin(t * 1.5f));
			}
			case DANCE -> {
				float beat = sin(t * 7f);
				r.root.move(1.3f * w * sin(t * 3.5f), -1.6f * w * Math.abs(beat), 0);
				r.rightArm.to(-0.4f, 0f, 1.9f + 0.5f * beat, w);
				r.leftArm.to(-0.4f, 0f, -1.9f + 0.5f * beat, w);
				r.rightLeg.to(0.28f * beat, 0f, 0.06f, w);
				r.leftLeg.to(-0.28f * beat, 0f, -0.06f, w);
				r.body.add(0f, 0.12f * w * sin(t * 3.5f), 0f);
				r.head.add(0f, 0f, 0.14f * w * sin(t * 3.5f + 1f));
			}
			case STRETCH -> {
				// ręce wysoko nad głowę, wygięcie do tyłu, na palcach
				r.rightArm.to(-3.0f, 0f, 0.22f + 0.05f * sin(t * 9f), w);
				r.leftArm.to(-3.0f, 0f, -0.22f - 0.05f * sin(t * 9f), w);
				lean(r, -0.14f, w);
				r.root.move(0, -0.9f * w, 0);
				r.head.add(-0.45f * w, 0f, 0f);
			}
			case WAVE -> {
				r.rightArm.to(0f, 0f, 2.35f + 0.38f * sin(t * 11f), w);
				r.head.add(0f, 0f, -0.1f * w);
			}
			case PAW -> {
				r.rightArm.to(-1.45f + 0.06f * sin(t * 9f), 0.1f, 0f, w);
				r.head.add(0.1f * w, 0f, 0.16f * w);
			}
			case GROOM -> {
				// łapka przy pyszczku, głowa pociera o nią
				float k = sin(t * 8f);
				r.rightArm.to(-2.15f + 0.12f * k, -0.75f, 0f, w);
				r.head.to(0.25f + 0.1f * k, -0.2f, 0.1f, w);
			}
			case WIGGLE -> {
				// nisko na nogach, pupa kręci się na boki, na końcu podskok
				stand(r, w * 0.6f);
				lean(r, 0.8f, w);
				float wig = sin(t * 13f) * Math.min(1f, t * 2f);
				r.root.move(0, 3.6f * w, 0);
				r.body.add(0f, 0.22f * w * wig, 0f);
				r.rightLeg.move(-1.2f * w * wig, 0, 0);
				r.leftLeg.move(-1.2f * w * wig, 0, 0);
				r.head.add(-0.65f * w, 0f, 0f);
				r.rightArm.to(-0.55f, 0f, 0.15f, w);
				r.leftArm.to(-0.55f, 0f, -0.15f, w);
			}
			case SHAKE -> {
				float k = sin(t * 34f) * w;
				r.head.add(0f, 0f, 0.45f * k);
				r.body.add(0f, 0.22f * sin(t * 34f + 0.8f) * w, 0f);
				r.rightArm.add(0f, 0f, 0.25f * w + 0.1f * k);
				r.leftArm.add(0f, 0f, -0.25f * w + 0.1f * k);
			}
			case NUZZLE -> {
				float k = sin(t * 4.2f);
				r.head.add(0.2f * w, 0f, 0.36f * w * k);
				r.root.move(1.1f * w * k, 0, 0);
				lean(r, 0.12f, w);
			}
		}
	}

	/** Siad na ziemi: nogi przed siebie, łapki na kolanach. */
	private static void sit(Rig r, float w, float t) {
		stand(r, w);
		r.root.move(0, 10f * w, 0);
		r.rightLeg.to(-1.45f, 0.3f, 0.08f, w);
		r.leftLeg.to(-1.45f, -0.3f, -0.08f, w);
		r.rightArm.to(-0.6f, 0f, 0.06f + 0.02f * sin(t * 1.4f), w);
		r.leftArm.to(-0.6f, 0f, -0.06f - 0.02f * sin(t * 1.4f), w);
	}

	/** Wraca do pozy stojącej (kasuje skradanie, machanie rękami przy chodzeniu…); kierunek patrzenia głowy zostaje. */
	static void stand(Rig r, float w) {
		r.head.x += (0 - r.head.x) * w;
		r.head.y += (0 - r.head.y) * w;
		r.head.z += (0 - r.head.z) * w;
		r.body.to(0, 0, 0, w);
		pos(r.body, 0, 0, 0, w);
		r.rightArm.to(0, 0, 0, w);
		pos(r.rightArm, -5, 2, 0, w);
		r.leftArm.to(0, 0, 0, w);
		pos(r.leftArm, 5, 2, 0, w);
		r.rightLeg.to(0, 0, 0, w);
		pos(r.rightLeg, -1.9f, 12, 0, w);
		r.leftLeg.to(0, 0, 0, w);
		pos(r.leftLeg, 1.9f, 12, 0, w);
	}

	private static void pos(Rig.Limb l, float x, float y, float z, float w) {
		l.x += (x - l.x) * w;
		l.y += (y - l.y) * w;
		l.z += (z - l.z) * w;
	}

	/** Pochylenie tułowia (obraca się wokół szyi) – nogi jadą za biodrami, żeby nie było dziury. */
	private static void lean(Rig r, float angle, float w) {
		float a = angle * w;
		r.body.pitch += a;
		float dz = 12f * sin(a), dy = -12f * (1f - (float) Math.cos(a));
		r.rightLeg.move(0, dy, dz);
		r.leftLeg.move(0, dy, dz);
	}

	// ======================== we dwoje ========================

	/**
	 * @param aim kierunek do dłoni partnera w układzie modelu (jednostkowy) – tylko dla PAIR_HANDS
	 * @param rightArm którą ręką trzymam (PAIR_HANDS)
	 */
	public static void pair(int kind, float w, float t, float aimX, float aimY, float aimZ, boolean rightArm, Rig r) {
		if (w <= 0f) return;
		switch (kind) {
			case PAIR_HUG -> {
				float k = 0.04f * sin(t * 2.4f);
				r.rightArm.to(-1.4f + k, -0.62f, 0f, w);
				r.leftArm.to(-1.4f - k, 0.62f, 0f, w);
				lean(r, 0.12f, w);
				r.head.to(0.12f, 0f, 0.2f, w);
			}
			case PAIR_HANDS -> point(r.arm(rightArm), aimX, aimY, aimZ, w);
			case PAIR_CARRY_HOLD -> {
				r.rightArm.to(-1.15f, 0.05f, 0f, w);
				r.leftArm.to(-1.15f, -0.05f, 0f, w);
			}
			case PAIR_CARRIED -> {
				stand(r, w);
				// na plecach w czyichś rękach: nogi zwisają w kolanach, głowa lekko uniesiona
				r.root.to(-HALF_PI, 0f, 0f, w).move(0, 22f * w, 8f * w);
				r.head.to(0.35f, 0f, 0.1f * sin(t * 0.9f), w);
				r.rightLeg.to(0.95f + 0.08f * sin(t * 2f), 0f, 0f, w);
				r.leftLeg.to(0.8f - 0.08f * sin(t * 2f), 0f, 0f, w);
				r.rightArm.to(-0.35f, 0f, 0.15f, w);
				r.leftArm.to(-0.9f, 0.5f, 0f, w);
			}
			case PAIR_PIGGY_HOLD -> {
				// na barana: ręce cofnięte, podtrzymują nogi osoby na plecach; lekko pochylony
				r.rightArm.to(0.45f, 0f, 0.28f, w);
				r.leftArm.to(0.45f, 0f, -0.28f, w);
				lean(r, 0.14f, w);
			}
			case PAIR_PIGGY_RIDER -> {
				// na czyichś plecach: nogi rozstawione obejmują niosącego w pasie, ręce na jego barkach, głowa nad jego głową
				stand(r, w);
				pos(r.rightLeg, -4.6f, 12, 0, w);
				pos(r.leftLeg, 4.6f, 12, 0, w);
				r.rightLeg.to(-1.3f, 0.12f, 0f, w);
				r.leftLeg.to(-1.3f, -0.12f, 0f, w);
				r.rightArm.to(-1.25f, -0.3f, 0f, w);
				r.leftArm.to(-1.25f, 0.3f, 0f, w);
				lean(r, 0.1f, w);
				r.head.add(0.1f * w, 0f, 0.06f * w * sin(t * 1.1f));
			}
			default -> {
			}
		}
	}

	/** Ręka wyciągnięta w podanym kierunku (układ modelu). Ręka w spoczynku wisi wzdłuż +Y. */
	public static void point(Rig.Limb arm, float dx, float dy, float dz, float w) {
		float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1e-4f) return;
		dx /= len;
		dy /= len;
		dz /= len;
		float pitch = (float) Math.asin(Math.max(-1f, Math.min(1f, dz)));
		float roll = (float) Math.atan2(-dx, dy);
		arm.to(pitch, 0f, roll, w);
	}

	/** Kierunek, w którym wskazuje ręka o tych kątach (do testów i sprawdzania point()). */
	public static float[] direction(float pitch, float yaw, float roll) {
		// R = Rz(roll)·Ry(yaw)·Rx(pitch) na wektorze (0,1,0)
		float x = 0, y = (float) Math.cos(pitch), z = (float) Math.sin(pitch);
		float x1 = x * (float) Math.cos(yaw) + z * (float) Math.sin(yaw), z1 = -x * (float) Math.sin(yaw) + z * (float) Math.cos(yaw);
		float x2 = x1 * (float) Math.cos(roll) - y * (float) Math.sin(roll), y2 = x1 * (float) Math.sin(roll) + y * (float) Math.cos(roll);
		return new float[]{x2, y2, z1};
	}

	/** Boop w nosek: głowa odskakuje do tyłu i na moment się spłaszcza. k = 0..1. */
	public static void boop(Rig r, float k) {
		if (k <= 0f) return;
		r.head.add(-0.32f * k, 0f, 0f);
		r.headSquash = 0.14f * k;
	}

	/** Płynne wejście i wyjście: t i ile zostało do końca (sekundy; left < 0 = bez końca). */
	public static float weight(float t, float left) {
		float in = ease(t / 0.28f);
		return left < 0f ? in : in * ease(left / 0.32f);
	}

	public static float ease(float x) {
		x = x < 0f ? 0f : Math.min(1f, x);
		return x * x * (3f - 2f * x);
	}

	private static float sin(float x) {
		return (float) Math.sin(x);
	}
}
