package pl.vsake.femboy.acc;

/**
 * Strój zapisany w config/femboy.json (ja / wszyscy / konkretny gracz). Pola to nazwy z komend,
 * null = brak dodatku w tym miejscu. fur: "auto" = kolor włosów ze skina; color: "auto" = losowy dla każdego.
 */
public final class Outfit {
	public String ears, tail, neck, head, legs, paws, skirt;
	public String fur = "auto";
	public String color = "pink";

	public String get(Item.Slot slot) {
		return switch (slot) {
			case EARS -> ears;
			case TAIL -> tail;
			case NECK -> neck;
			case HEAD -> head;
			case LEGS -> legs;
			case PAWS -> paws;
			case SKIRT -> skirt;
		};
	}

	public void set(Item.Slot slot, Item item) {
		String v = item == null ? null : item.id;
		switch (slot) {
			case EARS -> ears = v;
			case TAIL -> tail = v;
			case NECK -> neck = v;
			case HEAD -> head = v;
			case LEGS -> legs = v;
			case PAWS -> paws = v;
			case SKIRT -> skirt = v;
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
