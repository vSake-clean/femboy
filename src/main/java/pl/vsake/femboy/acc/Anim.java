package pl.vsake.femboy.acc;

/**
 * Ruch dodatków – czysta matematyka, wspólna dla gry i podglądu poza grą.
 * Zwraca przyrosty (do pozy spoczynkowej z Parts): kąty w radianach i przesunięcia w pikselach.
 */
public final class Anim {
	private Anim() {
	}

	/** Dane z render state gracza na tę klatkę. */
	public static final class In {
		/** Wiek w tickach (z częścią klatki). */
		public float age;
		public float limbSwing, limbAmount;
		public boolean sneaking, riding, swimming;
		/** Coś na głowie (hełm, dynia, czaszka) – uszy wychodzą wyżej. */
		public boolean helmet;
		/** Czas głaskania (klatka GIF-a 0..10) albo -1. */
		public float pat = -1f;
		/** Kąty nóg po ułożeniu modelu (spódniczka za nimi podąża). */
		public float rightLegPitch, leftLegPitch;
		/**
		 * Reakcje 0..1 (fx/Reactions): flat – uszy po sobie (syk, warczenie, obrażenia), perk – uszy postawione (miau),
		 * wag – szybkie merdanie (hau), puff – nastroszony ogon (syk).
		 */
		public float flat, perk, wag, puff;
		/** Polowanie 0..1 (skradanie, czajenie się, kropka lasera): końcówka ogona nerwowo drga. */
		public float hunt;
		/** Kąt prawej ręki po ułożeniu modelu – sznurek zabawki wisi mimo to pionowo. */
		public float rightArmPitch, rightArmRoll;
	}

	/** Ile liczb zwraca apply(). */
	public static final int OUT = 7;

	/** out = {pitch, yaw, roll, dx, dy, dz, pogrubienie} – dodawane do pozy elementu (pogrubienie: 0,5 = o połowę grubszy). */
	public static void apply(Parts.Piece p, Look look, In in, float[] out) {
		out[0] = out[1] = out[2] = out[3] = out[4] = out[5] = out[6] = 0f;
		if (p.motion == Parts.Motion.NONE) return;
		long seed = look.seed;
		float phase = (seed & 0xFFFF) / 65536f * 6.2831855f;
		float t = in.age;
		float walk = clamp01(in.limbAmount);
		float step = in.limbSwing * 0.6662f;
		// głaskanie: uszy po sobie przez cały czas, ogon w górę; lekko w rytm klepnięć
		float pat = in.pat >= 0f ? 0.7f + 0.3f * Math.max(0f, 1f - Math.abs(in.pat - 5f) / 5f) : 0f;
		int s = p.side;
		switch (p.motion) {
			case EAR -> {
				float tw = twitch(t, seed, s);
				out[0] = -0.15f * tw - 0.65f * pat - 0.85f * in.flat + 0.12f * in.perk;
				out[2] = s * (0.4f * tw + 0.35f * pat + 0.75f * in.flat - 0.2f * in.perk);
				out[4] = (in.helmet ? -1.1f : 0f) - 0.5f * in.perk + 0.6f * in.flat;
			}
			case EAR_PUPPY -> {
				// klapnięcia przy chodzeniu, przy głaskaniu radośnie odstają
				float flop = 0.14f * Math.abs(sin(step)) * walk + 0.06f * twitch(t, seed, s) + 0.28f * pat + 0.45f * in.perk - 0.1f * in.flat;
				out[2] = -s * flop;
				out[0] = 0.1f * sin(step * 2f) * walk;
				if (in.helmet) {
					out[3] = s * 1.0f;
					out[4] = -1.0f;
				}
			}
			case EAR_BUNNY -> {
				out[0] = 0.12f * sin(step) * walk - 0.75f * pat - 1.0f * in.flat + 0.1f * in.perk;
				out[2] = s * (0.08f * Math.abs(sin(step)) * walk + 0.04f * sin(t * 0.05f + phase));
				if (in.helmet) out[4] = -1.1f;
			}
			case EAR_BUNNY_TOP -> out[0] = -0.25f * Math.abs(sin(step)) * walk - 0.2f * pat + 0.06f * sin(t * 0.07f + phase) + 0.22f * in.perk - 0.3f * in.flat;
			case TAIL_CAT -> {
				float amp = 0.32f + 0.35f * walk + 0.25f * pat;
				out[1] = amp * (0.35f + 0.16f * p.index) * sin(t * (0.085f + 0.05f * walk) + phase - p.index * 0.6f);
				if (p.index == 0) out[0] = 0.06f * sin(t * 0.045f + phase) + base(in) + 0.35f * pat + 0.45f * in.puff;
				else out[0] = 0.04f * sin(t * 0.05f + phase - p.index * 0.5f) - (in.swimming ? 0.35f : 0f);
				react(p, in, t, phase, out);
			}
			case TAIL_BUSHY -> {
				float amp = 0.12f + 0.18f * walk + 0.2f * pat;
				out[1] = amp * (0.4f + 0.2f * p.index) * sin(t * 0.06f + phase - p.index * 0.5f);
				if (p.index == 0) out[0] = 0.12f * sin(step * 2f) * walk + base(in) + 0.25f * pat;
				else if (in.swimming) out[0] = -0.2f;
				react(p, in, t, phase, out);
			}
			case TAIL_PUPPY -> {
				// merda zawsze, a przy bieganiu i głaskaniu – jak szalony
				float happy = Math.min(1f, 0.3f + 0.8f * walk + pat + in.wag);
				if (p.index == 0) out[6] = 0.4f * in.puff;
				float freq = 0.35f + 0.75f * happy, amp = 0.25f + 0.45f * happy;
				float w = p.index == 0 ? 1f : p.index == 1 ? 0.6f : 0.4f;
				out[1] = amp * w * sin(t * freq + phase - p.index * 0.7f);
				if (p.index == 0) out[0] = base(in);
			}
			case TAIL_BUNNY -> {
				out[0] = 0.12f * sin(step * 2f) * walk + base(in);
				out[1] = 0.5f * in.wag * sin(t * 1.5f + phase) + 0.35f * in.hunt * burst(t, phase) * sin(t * 2.1f + phase);
				out[6] = 0.35f * in.puff;
			}
			case BELL -> {
				// dzwoneczek przy uchu / kokardce: buja się w rytm kroków
				out[0] = 0.3f * sin(step * 2f) * walk + 0.04f * sin(t * 0.15f + phase);
				out[2] = 0.35f * sin(step) * walk;
			}
			case SCARF -> {
				// koniec z tyłu (side < 0) unosi się przy bieganiu, ten z przodu tylko się kołysze; wiatr rusza oba cały czas
				boolean back = s < 0;
				float wind = 0.07f * sin(t * 0.09f + phase + p.index) + 0.05f * sin(t * 0.23f + phase * 1.7f - p.index * 0.8f);
				float flap = 0.14f * sin(t * 0.9f + phase - p.index * 1.1f) * walk;
				if (p.index == 0) out[0] = (back ? 0.75f * walk : -0.08f * walk) + wind + flap - (in.sneaking ? 0.5f : 0f) + (in.swimming && back ? 0.6f : 0f);
				else out[0] = (back ? 0.3f * walk : 0f) + wind * 1.5f + flap * 1.4f;
				out[2] = (back ? -1f : 1f) * (0.06f * sin(t * 0.07f + phase) + 0.1f * sin(step) * walk);
			}
			case PENDANT -> {
				// wisi pionowo nawet przy skradaniu (tułów jest wtedy pochylony)
				out[0] = 0.22f * sin(step * 2f) * walk + 0.05f * sin(t * 0.12f + phase) - (in.sneaking ? 0.5f : 0f);
				out[2] = 0.14f * sin(step) * walk;
			}
			case HAIR -> {
				if (in.helmet) {
					out[4] = -1.0f;
					out[5] = -1.0f;
				}
			}
			case SKIRT_FRONT -> out[0] = -0.85f * Math.max(0f, -Math.min(in.rightLegPitch, in.leftLegPitch));
			case SKIRT_BACK -> out[0] = 0.85f * Math.max(0f, Math.max(in.rightLegPitch, in.leftLegPitch)) + (in.sneaking ? 0.42f : 0f);
			case SKIRT_SIDE -> out[2] = -s * 0.04f * walk;
			case DANGLE -> {
				// sznurek zabawki: wisi pionowo niezależnie od ręki, buja się przy chodzeniu i lekko sam z siebie
				out[0] = -in.rightArmPitch + 0.32f * sin(step * 2f) * walk + 0.06f * sin(t * 0.11f + phase);
				out[2] = -in.rightArmRoll + 0.3f * sin(step) * walk + 0.05f * sin(t * 0.07f + phase);
			}
			case DANGLE_END -> {
				// to, co wisi na końcu: dodatkowo się kręci i spóźnia za sznurkiem
				out[0] = 0.25f * sin(step * 2f + 0.8f) * walk + 0.08f * sin(t * 0.13f + phase);
				out[1] = 0.6f * sin(t * 0.05f + phase) + 0.8f * walk * sin(step);
				out[2] = 0.2f * sin(step + 0.6f) * walk;
			}
			case WING -> {
				// spokojne trzepotanie; przy bieganiu i głaskaniu szybciej i szerzej, przy skradaniu złożone
				float k = Math.min(1f, 0.25f + 0.6f * walk + pat);
				out[1] = s * (0.18f + 0.3f * k) * sin(t * (0.08f + 0.25f * k) + phase) - (in.sneaking ? s * -0.35f : 0f);
				out[2] = s * 0.05f * sin(t * 0.05f + phase);
			}
			default -> {
			}
		}
	}

	/** Reakcje długiego ogona: hau = szybkie merdanie całym ogonem, syk = nastroszony (grubszy od nasady). */
	private static void react(Parts.Piece p, In in, float t, float phase, float[] out) {
		if (in.wag > 0f) out[1] += in.wag * (p.index == 0 ? 0.5f : 0.22f) * sin(t * 1.3f + phase - p.index * 0.6f);
		// polowanie: sama końcówka drga krótkimi seriami, reszta ogona prawie nieruchoma
		if (in.hunt > 0f && p.index >= 3) out[1] += in.hunt * 0.5f * burst(t, phase) * sin(t * 2.1f + phase);
		if (p.index == 0) out[6] = 0.5f * in.puff;
	}

	/** 0..1: seria drgnięć co ok. 1,5 s (ogon polującego kota nie drga bez przerwy). */
	static float burst(float t, float phase) {
		float k = sin(t * 0.21f + phase);
		return k > 0.2f ? Math.min(1f, (k - 0.2f) * 3f) : 0f;
	}

	/** Poprawka pierwszego segmentu ogona: skradanie pochyla tułów, siedzenie kładzie ogon. */
	private static float base(In in) {
		return (in.sneaking ? -0.45f : 0f) + (in.riding ? -0.5f : 0f);
	}

	/**
	 * Strzyżenie uszkiem: co kilka sekund (każdy ma swój rytm) jedno albo oba uszy drgną na 0,28 s.
	 * @return 0..1
	 */
	public static float twitch(float ageTicks, long seed, int side) {
		float sec = ageTicks / 20f + (seed & 0xFF) / 37f;
		float period = 3.2f + ((seed >>> 8) & 3) * 0.9f;
		int cycle = (int) Math.floor(sec / period);
		float local = sec - cycle * period;
		if (local > 0.28f) return 0f;
		int who = (int) (Rng.mix(seed + cycle) & 3); // 0,3: prawe, 1: lewe, 2: oba
		boolean mine = who == 2 || (side < 0 ? who == 0 || who == 3 : who == 1);
		return mine ? (float) Math.sin(local / 0.28f * Math.PI) : 0f;
	}

	private static float sin(float x) {
		return (float) Math.sin(x);
	}

	private static float clamp01(float v) {
		return v < 0f ? 0f : Math.min(1f, v);
	}
}
