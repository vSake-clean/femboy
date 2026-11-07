package pl.vsake.femboy.acc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static pl.vsake.femboy.acc.Item.*;

/**
 * Gotowe zestawy. Służą do szybkiego wyboru (/femboy acc me catboy) i do losowania:
 * w trybie random każdy dostaje jeden zestaw z włączonych, a do niego losowe dodatki.
 * Zasada z poradników: 2–3 dodatki naraz wyglądają najlepiej, więc dokładamy z umiarem.
 */
public enum Kit {
	CATBOY("catboy", "catboy"),
	PUPPY("puppy", "puppyboy"),
	FOX("fox", "lisek"),
	WOLF("wolf", "wilczek"),
	BUNNY("bunny", "króliczek"),
	FEMBOY("femboy", "femboy");

	public final String id;
	public final String label;

	Kit(String id, String label) {
		this.id = id;
		this.label = label;
	}

	public static Kit byId(String s) {
		if (s == null) return null;
		String k = s.toLowerCase(Locale.ROOT);
		for (Kit kit : values()) if (kit.id.equals(k) || kit.label.equals(k)) return kit;
		if (k.equals("cat") || k.equals("catgirl")) return CATBOY;
		if (k.equals("dog") || k.equals("puppyboy") || k.equals("pup")) return PUPPY;
		return null;
	}

	public static List<String> ids() {
		List<String> out = new ArrayList<>();
		for (Kit k : values()) out.add(k.id);
		return out;
	}

	/** Stały zestaw do wybrania komendą. */
	public Outfit outfit() {
		Outfit o = new Outfit();
		switch (this) {
			case CATBOY -> {
				o.set(Slot.EARS, EARS_CAT);
				o.set(Slot.TAIL, TAIL_CAT);
				o.set(Slot.NECK, NECK_BELL);
				o.set(Slot.PAWS, PAWS);
				o.color = "pink";
			}
			case PUPPY -> {
				o.set(Slot.EARS, EARS_PUPPY);
				o.set(Slot.TAIL, TAIL_PUPPY);
				o.set(Slot.NECK, NECK_TAG);
				o.color = "red";
			}
			case FOX -> {
				o.set(Slot.EARS, EARS_FOX);
				o.set(Slot.TAIL, TAIL_FOX);
				o.set(Slot.NECK, NECK_HEART);
				o.fur = "ginger";
				o.color = "black";
			}
			case WOLF -> {
				o.set(Slot.EARS, EARS_WOLF);
				o.set(Slot.TAIL, TAIL_WOLF);
				o.set(Slot.NECK, NECK_TAG);
				o.fur = "gray";
				o.color = "black";
			}
			case BUNNY -> {
				o.set(Slot.EARS, EARS_BUNNY);
				o.set(Slot.TAIL, TAIL_BUNNY);
				o.set(Slot.NECK, NECK_BOW);
				o.fur = "white";
				o.color = "pink";
			}
			case FEMBOY -> {
				o.set(Slot.NECK, NECK_HEART);
				o.set(Slot.HEAD, HEAD_BOW);
				o.set(Slot.LEGS, LEGS_STRIPES);
				o.set(Slot.SKIRT, SKIRT);
				o.color = "pink";
			}
		}
		return o;
	}

	/**
	 * Losowy wygląd z zestawów z puli.
	 * @param hair kolor włosów ze skina albo -1
	 */
	public static Look random(long seed, List<Kit> pool, int hair) {
		Rng r = new Rng(seed);
		List<Kit> kits = pool == null || pool.isEmpty() ? List.of(values()) : pool;
		Kit kit = kits.get(r.nextInt(kits.size()));
		Look.Builder b = Look.builder().seed(seed);
		switch (kit) {
			case CATBOY -> {
				b.set(EARS_CAT);
				if (r.chance(0.85f)) b.set(TAIL_CAT);
				float n = r.nextFloat();
				b.set(n < 0.6f ? NECK_BELL : n < 0.75f ? NECK_HEART : n < 0.85f ? NECK_BOW : null);
				if (r.chance(0.3f)) b.set(PAWS);
				float l = r.nextFloat();
				b.set(l < 0.3f ? LEGS_STRIPES : l < 0.4f ? LEGS_SOCKS : null);
				if (r.chance(0.15f)) b.set(HEAD_BOW);
				if (r.chance(0.08f)) b.set(SKIRT);
			}
			case PUPPY -> {
				b.set(EARS_PUPPY);
				if (r.chance(0.85f)) b.set(TAIL_PUPPY);
				float n = r.nextFloat();
				b.set(n < 0.7f ? NECK_TAG : n < 0.8f ? NECK_BELL : n < 0.85f ? NECK_HEART : null);
				if (r.chance(0.3f)) b.set(PAWS);
				float l = r.nextFloat();
				b.set(l < 0.15f ? LEGS_SOCKS : l < 0.3f ? LEGS_STRIPES : null);
				if (r.chance(0.05f)) b.set(SKIRT);
			}
			case FOX -> {
				b.set(EARS_FOX);
				if (r.chance(0.9f)) b.set(TAIL_FOX);
				float n = r.nextFloat();
				b.set(n < 0.25f ? NECK_HEART : n < 0.4f ? NECK_BOW : n < 0.5f ? NECK_BELL : null);
				if (r.chance(0.2f)) b.set(LEGS_STRIPES);
				if (r.chance(0.1f)) b.set(HEAD_BOW);
				if (r.chance(0.08f)) b.set(SKIRT);
			}
			case WOLF -> {
				b.set(EARS_WOLF);
				if (r.chance(0.85f)) b.set(TAIL_WOLF);
				float n = r.nextFloat();
				b.set(n < 0.35f ? NECK_TAG : n < 0.55f ? NECK_HEART : null);
				if (r.chance(0.2f)) b.set(PAWS);
				if (r.chance(0.15f)) b.set(LEGS_SOCKS);
			}
			case BUNNY -> {
				b.set(EARS_BUNNY);
				if (r.chance(0.85f)) b.set(TAIL_BUNNY);
				float n = r.nextFloat();
				b.set(n < 0.4f ? NECK_BOW : n < 0.6f ? NECK_HEART : null);
				float l = r.nextFloat();
				b.set(l < 0.25f ? LEGS_SOCKS : l < 0.4f ? LEGS_STRIPES : null);
				if (r.chance(0.15f)) b.set(HEAD_BOW);
				if (r.chance(0.1f)) b.set(SKIRT);
			}
			case FEMBOY -> {
				b.set(r.chance(0.65f) ? LEGS_STRIPES : LEGS_SOCKS);
				float n = r.nextFloat();
				b.set(n < 0.35f ? NECK_HEART : n < 0.7f ? NECK_BOW : n < 0.8f ? NECK_BELL : null);
				float h = r.nextFloat();
				b.set(h < 0.4f ? HEAD_BOW : h < 0.7f ? HEAD_CLIP : null);
				if (r.chance(0.35f)) {
					b.set(EARS_CAT);
					if (r.chance(0.5f)) b.set(TAIL_CAT);
				}
				if (r.chance(0.4f)) b.set(SKIRT);
				if (r.chance(0.1f)) b.set(PAWS);
			}
		}
		// zawsze co najmniej dwa dodatki – inaczej los wygląda jak "nic nie dostał"
		if (b.count() < 2) b.set(kit == PUPPY || kit == WOLF ? NECK_TAG : NECK_HEART);
		Item ears = b.get(Slot.EARS), tail = b.get(Slot.TAIL);
		boolean species = kit == FOX || kit == WOLF || kit == BUNNY;
		// zawsze tyle samo losowań – kolor dodatków nie zmienia się, kiedy skin (a z nim kolor włosów) dopiero się wczyta
		boolean wantHair = r.chance(species ? 0.3f : 0.6f);
		int palette = r.pick(Palette.furs(ears, tail));
		b.fur(wantHair && hair >= 0 ? hair : palette);
		b.accent(r.pick(Palette.ACCENTS));
		return b.build();
	}
}
