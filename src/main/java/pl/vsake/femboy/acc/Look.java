package pl.vsake.femboy.acc;

import java.util.ArrayList;
import java.util.List;

/**
 * Gotowy wygląd jednej osoby: które dodatki (po jednym na slot) i jakie kolory.
 * Niezmienny – może być współdzielony między klatkami i graczami.
 */
public final class Look {
	private final Item[] items;
	/** Kolor futra (uszy, ogon, łapki) 0xRRGGBB. */
	public final int fur;
	/** Kolor dodatków (obroża, kokardki, szalik, spódniczka) 0xRRGGBB. */
	public final int accent;
	/** Do animacji: żeby uszy i ogony różnych osób nie ruszały się równo. */
	public final long seed;
	private final String key;

	private Look(Item[] items, int fur, int accent, long seed) {
		this.items = items;
		this.fur = fur & 0xFFFFFF;
		this.accent = accent & 0xFFFFFF;
		this.seed = seed;
		StringBuilder sb = new StringBuilder();
		for (Item it : items) sb.append(it == null ? "-" : it.name()).append(',');
		sb.append(Integer.toHexString(this.fur)).append(',').append(Integer.toHexString(this.accent));
		key = sb.toString();
	}

	public Item get(Item.Slot slot) {
		return items[slot.ordinal()];
	}

	public boolean has(Item item) {
		return items[item.slot.ordinal()] == item;
	}

	/** Czy coś z dzwoneczkiem – wtedy dzwoni przy chodzeniu. */
	public boolean hasBell() {
		for (Item it : items) if (it != null && it.bell()) return true;
		return false;
	}

	/** Kot (kocie uszy albo ogon) – mruczy przy głaskaniu. */
	public boolean isCat() {
		Item e = items[Item.Slot.EARS.ordinal()];
		return e != null && e.catEars() || has(Item.TAIL_CAT);
	}

	public boolean isEmpty() {
		for (Item it : items) if (it != null) return false;
		return true;
	}

	/** Klucz tekstury: te same dodatki i kolory = ta sama tekstura (ziarno animacji nie ma znaczenia). */
	public String textureKey() {
		return key;
	}

	public List<Item> items() {
		List<Item> out = new ArrayList<>();
		for (Item it : items) if (it != null) out.add(it);
		return out;
	}

	/** "kocie uszy, koci ogon, obroża z dzwoneczkiem" */
	public String describe() {
		StringBuilder sb = new StringBuilder();
		for (Item it : items) {
			if (it == null) continue;
			if (!sb.isEmpty()) sb.append(", ");
			sb.append(it.label);
		}
		return sb.isEmpty() ? "nic" : sb.toString();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private final Item[] items = new Item[Item.Slot.values().length];
		private int fur = -1, accent = -1;
		private long seed;

		public Builder set(Item item) {
			if (item != null) items[item.slot.ordinal()] = item;
			return this;
		}

		public Builder clear(Item.Slot slot) {
			items[slot.ordinal()] = null;
			return this;
		}

		public Item get(Item.Slot slot) {
			return items[slot.ordinal()];
		}

		public int count() {
			int n = 0;
			for (Item it : items) if (it != null) n++;
			return n;
		}

		public Builder fur(int rgb) {
			fur = rgb;
			return this;
		}

		public Builder accent(int rgb) {
			accent = rgb;
			return this;
		}

		public Builder seed(long s) {
			seed = s;
			return this;
		}

		public Look build() {
			Item ears = items[Item.Slot.EARS.ordinal()], tail = items[Item.Slot.TAIL.ordinal()];
			int f = fur >= 0 ? fur : Palette.defaultFur(ears, tail);
			int a = accent >= 0 ? accent : Palette.rgb("pink");
			return new Look(items.clone(), f, a, seed);
		}
	}
}
