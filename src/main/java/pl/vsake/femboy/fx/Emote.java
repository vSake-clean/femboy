package pl.vsake.femboy.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Emotki nad głową pod klawiszami (♥ ? ! 💢 …), przesyłane innym graczom z modem tak samo jak odgłosy.
 * Każda to pikselowy obrazek 10×10 (znaki: '.' nic, 'R' kolor główny, 'L' jasny, 'D' ciemny) – ciemną obwódkę
 * dorabia EmoteRender. Bez klas klienta: ładowane też na serwerze (sprawdzanie pakietów) i w testach.
 * Id jest częścią protokołu – nowe dopisywać na końcu.
 */
public enum Emote {
	HEART("heart", "♥", "Serduszko", 0xFF4B7D, 0xFFC2D3, 0xC22D58,
		".RR....RR.",
		"RLLR..RRRR",
		"RLRRRRRRRR",
		"RRRRRRRRRR",
		"RRRRRRRRRD",
		".RRRRRRRD.",
		"..RRRRRD..",
		"...RRRD...",
		"....RD....",
		".........."),
	QUESTION("question", "?", "Pytajnik", 0x5AA9FF, 0xD2E8FF, 0x2F6FC0,
		"..RRRRRR..",
		".RRLLRRRR.",
		".RR....RR.",
		".......RR.",
		".....RRRD.",
		"....RRR...",
		"....RD....",
		"..........",
		"....RR....",
		"....RD...."),
	EXCLAIM("exclaim", "!", "Wykrzyknik", 0xFFC83D, 0xFFF1B8, 0xC98A12,
		"....RR....",
		"...RLRR...",
		"...RLRR...",
		"...RRRR...",
		"...RRRD...",
		"....RR....",
		"....RD....",
		"..........",
		"....RR....",
		"....RD...."),
	ANGER("anger", "#", "Złość", 0xE8332B, 0xFF8A80, 0xA31C16,
		"..RR..RR..",
		"..RR..RR..",
		"RRRR..RRRR",
		"RRR....RRR",
		"..........",
		"..........",
		"RRR....RRR",
		"RRRR..RRRR",
		"..RR..RR..",
		"..RR..RR.."),
	NOTE("note", "♪", "Nutka", 0xB07CFF, 0xE4D2FF, 0x7446C4,
		"....RRRRRR",
		"....RLLLLR",
		"....RR..RR",
		"....RR..RR",
		"....RR..RR",
		"....RR..RR",
		".RRRRR.RRR",
		"RLRRRRRRRR",
		"RRRRD.RRRD",
		".RRD...RD."),
	SLEEP("sleep", "z", "Zzz", 0xA9C4EA, 0xFFFFFF, 0x6D8DBE,
		"RRRRR.....",
		"...RR.....",
		"..RR......",
		".RR..RRRR.",
		"RRRRR..RR.",
		"......RR..",
		".....RRRR.",
		"..........",
		"......RRR.",
		"......RRR."),
	STAR("star", "✦", "Gwiazdka", 0xFFD84A, 0xFFFFFF, 0xD49A1C,
		"....RR....",
		"....RR....",
		"...RLLR...",
		"RRRRLLRRRR",
		".RRLLLLRR.",
		"..RRLLRR..",
		"..RRRRRR..",
		".RRD..RRD.",
		".RD....RD.",
		".........."),
	TEAR("tear", "●", "Łezka", 0x5EC8FF, 0xD4F1FF, 0x2B8FCB,
		"....RR....",
		"....RR....",
		"...RRRR...",
		"...RLRR...",
		"..RRLRRR..",
		"..RLRRRR..",
		".RRLRRRRD.",
		".RRRRRRRD.",
		"..RRRRDD..",
		"...RDDD...");

	/** Bok obrazka w pikselach. */
	public static final int SIZE = 10;

	public final String id;
	/** Znak do przycisków w GUI (z czcionki gry). */
	public final String symbol;
	public final String label;
	public final int main, light, dark;
	private final String[] rows;

	Emote(String id, String symbol, String label, int main, int light, int dark, String... rows) {
		this.id = id;
		this.symbol = symbol;
		this.label = label;
		this.main = main;
		this.light = light;
		this.dark = dark;
		this.rows = rows;
		if (rows.length != SIZE) throw new IllegalStateException(id + ": zła wysokość obrazka");
		for (String r : rows) if (r.length() != SIZE) throw new IllegalStateException(id + ": zła szerokość wiersza '" + r + "'");
	}

	/** Kolor piksela 0xRRGGBB albo -1 (przezroczysty). */
	public int pixel(int x, int y) {
		return switch (rows[y].charAt(x)) {
			case 'R' -> main;
			case 'L' -> light;
			case 'D' -> dark;
			default -> -1;
		};
	}

	public static Emote byId(String id) {
		if (id == null) return null;
		String s = id.toLowerCase(Locale.ROOT);
		for (Emote e : values()) if (e.id.equals(s)) return e;
		return null;
	}

	public static List<String> ids() {
		List<String> l = new ArrayList<>();
		for (Emote e : values()) l.add(e.id);
		return l;
	}
}
