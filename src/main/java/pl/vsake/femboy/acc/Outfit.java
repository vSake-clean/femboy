package pl.vsake.femboy.acc;

/**
 * Strój zapisany w config/femboy.json (ja / wszyscy / konkretny gracz). Pola to nazwy z komend,
 * null = brak dodatku w tym miejscu. fur: "auto" = kolor włosów ze skina; color: "auto" = losowy dla każdego.
 */
public final class Outfit {
	public String ears, tail, neck, head, face, back, arms, paws, skirt, fx, hand;
	public String fur = "auto";
	public String color = "pink";

	public String get(Item.Slot slot) {
		return switch (slot) {
			case EARS -> ears;
			case TAIL -> tail;
			case NECK -> neck;
			case HEAD -> head;
			case FACE -> face;
			case BACK -> back;
			case ARMS -> arms;
			case PAWS -> paws;
			case SKIRT -> skirt;
			case FX -> fx;
			case HAND -> hand;
		};
	}

	public void set(Item.Slot slot, Item item) {
		String v = item == null ? null : item.id;
		switch (slot) {
			case EARS -> ears = v;
			case TAIL -> tail = v;
			case NECK -> neck = v;
			case HEAD -> head = v;
			case FACE -> face = v;
			case BACK -> back = v;
			case ARMS -> arms = v;
			case PAWS -> paws = v;
			case SKIRT -> skirt = v;
			case FX -> fx = v;
			case HAND -> hand = v;
		}
	}

	public Item item(Item.Slot slot) {
		return Item.of(slot, get(slot));
	}

	public boolean isEmpty() {
		for (Item.Slot s : Item.Slot.values()) if (item(s) != null) return false;
		return true;
	}

	public Outfit copy() {
		Outfit o = new Outfit();
		for (Item.Slot s : Item.Slot.values()) o.set(s, item(s));
		o.fur = fur;
		o.color = color;
		return o;
	}

	public static Outfit of(Look look, boolean furAuto) {
		Outfit o = new Outfit();
		for (Item.Slot s : Item.Slot.values()) o.set(s, look.get(s));
		o.fur = furAuto ? "auto" : String.format("%06x", look.fur);
		o.color = String.format("%06x", look.accent);
		return o;
	}

	/**
	 * @param hair kolor włosów ze skina albo -1, gdy nieznany
	 * @param seed ziarno osoby (kolor "auto" i animacje)
	 */
	public Look resolve(int hair, long seed) {
		Look.Builder b = Look.builder().seed(seed);
		for (Item.Slot s : Item.Slot.values()) b.set(item(s));
		int f = "auto".equalsIgnoreCase(fur) || fur == null ? hair : Palette.parse(fur);
		if (f < 0) f = Palette.defaultFur(b.get(Item.Slot.EARS), b.get(Item.Slot.TAIL));
		b.fur(f);
		int a = "auto".equalsIgnoreCase(color) ? Palette.ACCENTS[(int) Math.floorMod(Rng.mix(seed ^ 0xACCE57L), Palette.ACCENTS.length)]
			: Palette.parse(color);
		b.accent(a >= 0 ? a : Palette.rgb("pink"));
		return b.build();
	}

	/** Do pakietu: 11 slotów (po kolei jak Item.Slot, "" = brak) + futro + kolor. */
	public java.util.List<String> toWire() {
		java.util.List<String> out = new java.util.ArrayList<>();
		for (Item.Slot s : Item.Slot.values()) {
			Item it = item(s);
			out.add(it == null ? "" : it.id);
		}
		out.add(fur == null ? "auto" : fur);
		out.add(color == null ? "pink" : color);
		return out;
	}

	/** Z pakietu od innego gracza; nieznane dodatki (nowsza wersja moda) po prostu pomija. null = zły format. */
	public static Outfit fromWire(java.util.List<String> w) {
		Item.Slot[] slots = Item.Slot.values();
		if (w == null || w.size() != slots.length + 2) return null;
		Outfit o = new Outfit();
		for (int i = 0; i < slots.length; i++) o.set(slots[i], Item.of(slots[i], w.get(i)));
		String f = w.get(slots.length), c = w.get(slots.length + 1);
		o.fur = "auto".equalsIgnoreCase(f) || Palette.parse(f) < 0 ? "auto" : f;
		o.color = "auto".equalsIgnoreCase(c) || Palette.parse(c) >= 0 ? c : "pink";
		return o;
	}

	public String describeColors() {
		String f = fur == null || "auto".equalsIgnoreCase(fur) ? "jak włosy" : label(fur);
		String c = "auto".equalsIgnoreCase(color) ? "losowy" : label(color);
		return "futro: " + f + ", kolor: " + c;
	}

	private static String label(String s) {
		int rgb = Palette.parse(s);
		return rgb < 0 ? s : Palette.label(rgb);
	}
}
