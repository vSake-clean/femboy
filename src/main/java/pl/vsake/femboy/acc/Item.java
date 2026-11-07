package pl.vsake.femboy.acc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Wszystkie dodatki. W jednym miejscu (slocie) można mieć naraz tylko jeden – np. jedne uszy.
 * Lista z tego, co faktycznie noszą catboye / puppyboye / femboye: opaska z uszami, ogon,
 * obroża / choker (z dzwoneczkiem, kostką, serduszkiem, kokardką), kokardka we włosach, zakolanówki
 * (najlepiej w paski), łapki i plisowana spódniczka. Od 1.5: misie/mysie uszy, diabelski ogonek,
 * chokery z kłódką i ćwiekami, aureolka, różki, tiara, kwiatek, okulary, maseczka, skrzydła
 * (anielskie, nietoperza, motyla) i ocieplacze na ręce.
 */
public enum Item {
	EARS_CAT(Slot.EARS, "cat", "kocie uszy"),
	EARS_FOX(Slot.EARS, "fox", "lisie uszy"),
	EARS_WOLF(Slot.EARS, "wolf", "wilcze uszy"),
	EARS_PUPPY(Slot.EARS, "puppy", "psie uszy"),
	EARS_BUNNY(Slot.EARS, "bunny", "królicze uszy"),
	EARS_BEAR(Slot.EARS, "bear", "misie uszy"),
	EARS_MOUSE(Slot.EARS, "mouse", "mysie uszy"),

	TAIL_CAT(Slot.TAIL, "cat", "koci ogon"),
	TAIL_FOX(Slot.TAIL, "fox", "lisi ogon"),
	TAIL_WOLF(Slot.TAIL, "wolf", "wilczy ogon"),
	TAIL_PUPPY(Slot.TAIL, "puppy", "psi ogonek"),
	TAIL_BUNNY(Slot.TAIL, "bunny", "króliczy puszek"),
	TAIL_DEVIL(Slot.TAIL, "devil", "diabelski ogonek"),

	NECK_BELL(Slot.NECK, "bell", "obroża z dzwoneczkiem"),
	NECK_TAG(Slot.NECK, "tag", "obroża z kostką"),
	NECK_HEART(Slot.NECK, "heart", "choker z serduszkiem"),
	NECK_BOW(Slot.NECK, "bow", "choker z kokardką"),
	NECK_LOCK(Slot.NECK, "lock", "choker z kłódką"),
	NECK_SPIKES(Slot.NECK, "spikes", "choker z ćwiekami"),

	HEAD_BOW(Slot.HEAD, "bow", "kokardka we włosach"),
	HEAD_CLIP(Slot.HEAD, "clip", "spinka-serduszko"),
	HEAD_HALO(Slot.HEAD, "halo", "aureolka"),
	HEAD_HORNS(Slot.HEAD, "horns", "diabelskie różki"),
	HEAD_TIARA(Slot.HEAD, "tiara", "tiara"),
	HEAD_FLOWER(Slot.HEAD, "flower", "kwiatek we włosach"),

	FACE_GLASSES(Slot.FACE, "glasses", "okulary"),
	FACE_MASK(Slot.FACE, "mask", "maseczka"),

	BACK_ANGEL(Slot.BACK, "angel", "anielskie skrzydła"),
	BACK_BAT(Slot.BACK, "bat", "skrzydła nietoperza"),
	BACK_BUTTERFLY(Slot.BACK, "butterfly", "skrzydła motyla"),

	ARMS_WARMERS(Slot.ARMS, "warmers", "ocieplacze na ręce"),
	ARMS_STRIPES(Slot.ARMS, "stripes", "ocieplacze w paski"),

	LEGS_SOCKS(Slot.LEGS, "socks", "zakolanówki"),
	LEGS_STRIPES(Slot.LEGS, "stripes", "zakolanówki w paski"),

	PAWS(Slot.PAWS, "on", "łapki"),
	SKIRT(Slot.SKIRT, "on", "plisowana spódniczka");

	public final Slot slot;
	/** Nazwa w komendzie (w obrębie slotu), np. /femboy acc me ears cat. */
	public final String id;
	public final String label;

	Item(Slot slot, String id, String label) {
		this.slot = slot;
		this.id = id;
		this.label = label;
	}

	public static Item of(Slot slot, String id) {
		if (slot == null || id == null) return null;
		String k = id.toLowerCase(Locale.ROOT);
		if (slot.toggle && (k.equals("on") || k.equals("yes") || k.equals("tak") || k.equals("true"))) k = "on";
		for (Item it : values()) if (it.slot == slot && it.id.equals(k)) return it;
		return null;
	}

	public static List<Item> of(Slot slot) {
		List<Item> out = new ArrayList<>();
		for (Item it : values()) if (it.slot == slot) out.add(it);
		return out;
	}

	public enum Slot {
		EARS("ears", "uszy", false),
		TAIL("tail", "ogon", false),
		NECK("neck", "szyja", false),
		HEAD("head", "włosy", false),
		FACE("face", "twarz", false),
		BACK("back", "plecy", false),
		ARMS("arms", "ręce", false),
		LEGS("legs", "nogi", false),
		PAWS("paws", "łapki", true),
		SKIRT("skirt", "spódniczka", true);

		public final String id;
		public final String label;
		/** Slot z jednym dodatkiem: wartości on/off zamiast nazw. */
		public final boolean toggle;

		Slot(String id, String label, boolean toggle) {
			this.id = id;
			this.label = label;
			this.toggle = toggle;
		}

		public static Slot byId(String s) {
			if (s == null) return null;
			for (Slot sl : values()) if (sl.id.equalsIgnoreCase(s)) return sl;
			return null;
		}
	}
}
