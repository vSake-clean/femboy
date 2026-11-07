package pl.vsake.femboy.acc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Nazwane kolory (do komend) i palety futer. Kolory jako 0xRRGGBB. */
public final class Palette {
	private Palette() {
	}

	public record Named(String id, String label, int rgb) {
	}

	private static final Map<String, Named> BY_ID = new LinkedHashMap<>();

	private static void add(String id, String label, int rgb) {
		BY_ID.put(id, new Named(id, label, rgb));
	}

	static {
		add("pink", "różowy", 0xFF8FBE);
		add("hotpink", "mocny róż", 0xFF4FA3);
		add("red", "czerwony", 0xE03A3E);
		add("orange", "pomarańczowy", 0xF08A2E);
		add("yellow", "żółty", 0xF5D04A);
		add("mint", "miętowy", 0x7FE0BC);
		add("green", "zielony", 0x4CB050);
		add("lightblue", "błękitny", 0x8CCBFF);
		add("blue", "niebieski", 0x3C6FE0);
		add("lavender", "lawendowy", 0xC4A8FF);
		add("purple", "fioletowy", 0x8A4FD8);
		add("black", "czarny", 0x2A282C);
		add("white", "biały", 0xF4F2EF);
		add("gray", "szary", 0x8E8E96);
		add("silver", "srebrny", 0xC6CBD3);
		add("brown", "brązowy", 0x7A5034);
		add("cream", "kremowy", 0xEBDCC0);
		add("ginger", "rudy", 0xD2773A);
		add("golden", "złoty", 0xD8A848);
	}

	/** Kolory dodatków (obroże, kokardki, zakolanówki) – losowanie częściej trafia w róże i czerń. */
	public static final int[] ACCENTS = {
		rgb("pink"), rgb("pink"), rgb("hotpink"), rgb("black"), rgb("black"), rgb("white"),
		rgb("lightblue"), rgb("lavender"), rgb("purple"), rgb("red"), rgb("mint"), rgb("blue"),
	};

	public static List<String> ids() {
		return new ArrayList<>(BY_ID.keySet());
	}

	public static int rgb(String id) {
		Named n = BY_ID.get(id);
		if (n == null) throw new IllegalArgumentException(id);
		return n.rgb;
	}

	/** Nazwa ("pink") albo hex ("ff88cc" / "#ff88cc"); -1 = nie rozumiem. */
	public static int parse(String s) {
		if (s == null) return -1;
		String k = s.toLowerCase(Locale.ROOT).trim();
		Named n = BY_ID.get(k);
		if (n != null) return n.rgb;
		if (k.startsWith("#")) k = k.substring(1);
		if (k.matches("[0-9a-f]{6}")) return Integer.parseInt(k, 16);
		if (k.matches("[0-9a-f]{3}")) {
			int r = Character.digit(k.charAt(0), 16), g = Character.digit(k.charAt(1), 16), b = Character.digit(k.charAt(2), 16);
			return (r * 17) << 16 | (g * 17) << 8 | b * 17;
		}
		return -1;
	}

	/** Do komunikatów: "różowy" albo "#a1b2c3". */
	public static String label(int rgb) {
		for (Named n : BY_ID.values()) if (n.rgb == rgb) return n.label;
		return String.format(Locale.ROOT, "#%06x", rgb & 0xFFFFFF);
	}

	/** Naturalne futra dla danych uszu/ogona (gdy "auto" nie ma skąd wziąć koloru włosów albo los tak chce). */
	public static int[] furs(Item ears, Item tail) {
		Item k = ears != null ? ears : tail;
		String which = k == null ? "cat" : k.id;
		return switch (which) {
			case "fox" -> new int[]{rgb("ginger"), rgb("ginger"), rgb("orange"), rgb("white"), rgb("silver"), rgb("brown")};
			case "wolf" -> new int[]{rgb("gray"), rgb("gray"), rgb("black"), rgb("white"), rgb("silver"), rgb("brown")};
			case "puppy" -> new int[]{rgb("brown"), rgb("brown"), rgb("cream"), rgb("golden"), rgb("white"), rgb("black")};
			case "bunny" -> new int[]{rgb("white"), rgb("white"), rgb("cream"), rgb("gray"), rgb("brown"), rgb("pink")};
			case "bear" -> new int[]{rgb("brown"), rgb("brown"), rgb("cream"), rgb("golden"), rgb("white"), rgb("black")};
			case "mouse" -> new int[]{rgb("gray"), rgb("gray"), rgb("silver"), rgb("white"), rgb("brown"), rgb("black")};
			default -> new int[]{rgb("black"), rgb("black"), rgb("white"), rgb("gray"), rgb("ginger"), rgb("cream"), rgb("brown"), rgb("pink"), rgb("lavender")};
		};
	}

	/** Domyślne futro, gdy nie wiadomo nic (pierwsze z palety). */
	public static int defaultFur(Item ears, Item tail) {
		return furs(ears, tail)[0];
	}
}
