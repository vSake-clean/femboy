package pl.vsake.femboy.voice;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Odgłosy pod klawiszami (miau, hau, mruczenie…). Każdy ma wiele wariantów – każdy wariant to osobne zdarzenie
 * dźwiękowe femboy:voice.ID.N w sounds.json z jednym plikiem, żeby wszyscy słyszeli DOKŁADNIE ten sam wariant,
 * który wylosował nadawca (vanilla losowałoby plik osobno u każdego).
 * Pliki to dźwięki z samej gry (minecraft:mob/…) – nie trzeba nic dokładać do jara.
 * Bez klas klienta: ładowane też na serwerze (sprawdzanie pakietów) i w testach.
 * Kolejność wariantów jest częścią protokołu – nowe dopisywać tylko NA KOŃCU listy.
 */
public enum Voice {
	MEOW("meow", "Miau", List.of(
		"minecraft:mob/cat/meow1", "minecraft:mob/cat/meow2", "minecraft:mob/cat/meow3", "minecraft:mob/cat/meow4",
		"minecraft:mob/cat/stray/idle1", "minecraft:mob/cat/stray/idle2", "minecraft:mob/cat/stray/idle3", "minecraft:mob/cat/stray/idle4",
		"minecraft:mob/cat/ocelot/idle1", "minecraft:mob/cat/ocelot/idle2", "minecraft:mob/cat/ocelot/idle3", "minecraft:mob/cat/ocelot/idle4",
		"minecraft:mob/cat/beg1", "minecraft:mob/cat/beg2", "minecraft:mob/cat/beg3",
		"minecraft:mob/cat/purreow1", "minecraft:mob/cat/purreow2")),
	BARK("bark", "Hau", wolves("bark1", "bark2", "bark3")),
	PURR("purr", "Mruczenie", List.of("minecraft:mob/cat/purr1", "minecraft:mob/cat/purr2", "minecraft:mob/cat/purr3")),
	HISS("hiss", "Syczenie", List.of("minecraft:mob/cat/hiss1", "minecraft:mob/cat/hiss2", "minecraft:mob/cat/hiss3")),
	GROWL("growl", "Warczenie", wolves("growl1", "growl2", "growl3")),
	WHINE("whine", "Skomlenie", wolves("whine")),
	PANT("pant", "Dyszenie", wolves("panting")),
	FOX("fox", "Lisek", List.of(
		"minecraft:mob/fox/idle1", "minecraft:mob/fox/idle2", "minecraft:mob/fox/idle3",
		"minecraft:mob/fox/idle4", "minecraft:mob/fox/idle5", "minecraft:mob/fox/idle6",
		"minecraft:mob/fox/screech1", "minecraft:mob/fox/screech2", "minecraft:mob/fox/screech3", "minecraft:mob/fox/screech4")),
	SQUEAK("squeak", "Pisk", List.of(
		"minecraft:mob/rabbit/idle1", "minecraft:mob/rabbit/idle2", "minecraft:mob/rabbit/idle3", "minecraft:mob/rabbit/idle4",
		"femboy:petpet/squeak1", "femboy:petpet/squeak2", "femboy:petpet/squeak3", "femboy:petpet/squeak4"));

	/** Odmiany wilków z 1.21.5+ – każda szczeka/warczy inaczej. W osobnej klasie, bo pola enuma powstają dopiero po stałych. */
	private static final class Wolves {
		static final String[] ALL = {"classic", "angry", "big", "cute", "grumpy", "puglin", "sad"};
	}

	public final String id;
	public final String label;
	/** Pliki wariantów (bez .ogg), w kolejności protokołu. */
	public final List<String> files;

	Voice(String id, String label, List<String> files) {
		this.id = id;
		this.label = label;
		this.files = files;
	}

	private static List<String> wolves(String... names) {
		List<String> out = new ArrayList<>();
		for (String w : Wolves.ALL) for (String n : names) out.add("minecraft:mob/wolf/" + w + "/" + n);
		return List.copyOf(out);
	}

	public int variants() {
		return files.size();
	}

	/** Ścieżka zdarzenia dźwiękowego wariantu (w przestrzeni femboy:). */
	public String event(int variant) {
		return "voice." + id + "." + variant;
	}

	/** Krótka nazwa wariantu do GUI, z nazwy pliku: "minecraft:mob/wolf/cute/bark2" → "wolf cute bark2". */
	public String variantLabel(int variant) {
		String f = files.get(variant);
		f = f.substring(f.indexOf(':') + 1);
		if (f.startsWith("mob/")) f = f.substring(4);
		return f.replace("/stray/", "/stray ").replace("/ocelot/", "/ocelot ").replace('/', ' ');
	}

	public static Voice byId(String id) {
		if (id == null) return null;
		String s = id.toLowerCase(Locale.ROOT);
		for (Voice v : values()) if (v.id.equals(s)) return v;
		return null;
	}

	public static List<String> ids() {
		List<String> l = new ArrayList<>();
		for (Voice v : values()) l.add(v.id);
		return l;
	}

	// ======================== przesyłanie ========================

	/** Wysokość dźwięku w setnych (100 = normalnie). */
	public static final int PITCH_MIN = 50, PITCH_MAX = 200;

	/**
	 * Jeden odgłos: co, który wariant, jak wysoko, nonce. Nonce rozpoznaje duplikaty – ta sama wiadomość
	 * przychodzi z kilku brokerów naraz i jeszcze przez serwer z modem, a zagrać ma raz.
	 */
	public record Msg(Voice voice, int variant, int pitch, int nonce) {
		/** Tekst do brokera MQTT: "meow 3 105 1a2b3c4d". */
		public String encode() {
			return voice.id + " " + variant + " " + pitch + " " + Integer.toHexString(nonce);
		}

		/** null = śmieci / nieznany odgłos (np. od nowszej wersji moda). */
		public static Msg decode(String s) {
			if (s == null || s.length() > 64) return null;
			String[] p = s.trim().split(" ");
			if (p.length != 4) return null;
			try {
				return of(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseUnsignedInt(p[3], 16));
			} catch (NumberFormatException e) {
				return null;
			}
		}

		/** Sprawdza wszystko, co przyszło z sieci; null = odrzucić. */
		public static Msg of(String id, int variant, int pitch, int nonce) {
			Voice v = byId(id);
			if (v == null || variant < 0 || variant >= v.variants() || pitch < PITCH_MIN || pitch > PITCH_MAX) return null;
			return new Msg(v, variant, pitch, nonce);
		}

		public float pitchF() {
			return pitch / 100f;
		}
	}
}
