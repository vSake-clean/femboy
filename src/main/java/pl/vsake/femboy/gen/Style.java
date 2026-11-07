package pl.vsake.femboy.gen;

import java.util.Locale;

/** Rodzaj skina, jaki dostaje gracz. */
public enum Style {
	FEMBOY("femboy"),
	EGIRL("egirl");

	public final String id;

	Style(String id) {
		this.id = id;
	}

	public static Style byId(String s) {
		if (s == null) return null;
		String k = s.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
		for (Style st : values()) if (st.id.equals(k)) return st;
		return null;
	}
}
