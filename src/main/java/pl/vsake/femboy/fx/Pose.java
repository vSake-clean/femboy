package pl.vsake.femboy.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Animacje ciała („emotki ciałem”): kocie pozy pod klawisze i te, które włączają się same (bochenek, przeciąganie).
 * Trzymane (durMs = 0) trwają, dopóki gracz się nie ruszy albo nie naciśnie klawisza drugi raz; pozostałe same się kończą.
 * Czysta Java – matematyka póz jest w PoseMath, a kto co robi w Poses.
 */
public enum Pose {
	SIT("sit", "Siad", "Siad", 0),
	KNEAD("knead", "Ugniatanie łapkami", "Ugniatanie", 0),
	LOAF("loaf", "Bochenek", "Bochenek", 0),
	BELLY("belly", "Brzuszek do głaskania", "Brzuszek", 0),
	NAP("nap", "Drzemka w kłębku", "Drzemka", 0),
	BEG("beg", "Prosi (łapki w górze)", "Prosi", 0),
	DANCE("dance", "Taniec", "Taniec", 0),
	STRETCH("stretch", "Przeciąganie", "Przeciąganie", 2800),
	WAVE("wave", "Machanie", "Machanie", 2200),
	PAW("paw", "Daj łapę", "Daj łapę", 2400),
	GROOM("groom", "Mycie łapki", "Mycie łapki", 3400),
	WIGGLE("wiggle", "Czajenie się przed skokiem", "Czajenie się", 2600),
	SHAKE("shake", "Otrzepanie się", "Otrzepanie", 1300),
	NUZZLE("nuzzle", "Ocieranie główką", "Ocieranie", 2400);

	public final String id;
	public final String label;
	/** Krótka nazwa na przyciski i kółko animacji. */
	public final String brief;
	/** 0 = trzymana (do ruchu albo ponownego naciśnięcia). */
	public final int durMs;

	Pose(String id, String label, String brief, int durMs) {
		this.id = id;
		this.label = label;
		this.brief = brief;
		this.durMs = durMs;
	}

	public boolean held() {
		return durMs == 0;
	}

	/** Postać siedzi albo leży na ziemi (ogon kładzie się jak przy siedzeniu). */
	public boolean onGround() {
		return this == SIT || this == KNEAD || this == LOAF || this == BELLY || this == NAP || this == BEG;
	}

	public static Pose byId(String s) {
		if (s == null) return null;
		String k = s.toLowerCase(Locale.ROOT);
		for (Pose p : values()) if (p.id.equals(k)) return p;
		return null;
	}

	public static List<String> ids() {
		List<String> out = new ArrayList<>();
		for (Pose p : values()) out.add(p.id);
		return out;
	}
}
