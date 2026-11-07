package pl.vsake.femboy.gen;

import java.util.Locale;

/** Rodzaj skina, jaki dostaje gracz. */
public enum Style {
	FEMBOY("femboy", "femboy"),
	EGIRL("egirl", "e-girl"),
	CATBOY("catboy", "catboy");

	public final String id;
	/** Do komunikatów i GUI. */
	public final String label;

	Style(String id, String label) {
		this.id = id;
		this.label = label;
	}

	public static Style byId(String s) {
		if (s == null) return null;
		String k = s.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
		for (Style st : values()) if (st.id.equals(k)) return st;
		return null;
	}

	/** Następny styl w kolejce (przełączanie puli w GUI). */
	public Style next() {
		return values()[(ordinal() + 1) % values().length];
	}
}
