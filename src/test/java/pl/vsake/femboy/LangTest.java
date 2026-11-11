package pl.vsake.femboy;

import org.junit.jupiter.api.Test;
import pl.vsake.femboy.acc.Item;
import pl.vsake.femboy.acc.Kit;
import pl.vsake.femboy.fx.Emote;
import pl.vsake.femboy.fx.Pose;
import pl.vsake.femboy.voice.Voice;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LangTest {
	@Test
	void polishStaysEnglishSwaps() {
		Lang.polish = true;
		assertEquals("Szafa", Lang.tr("Szafa"));
		Lang.polish = false;
		try {
			assertEquals("Wardrobe", Lang.tr("Szafa"));
			assertEquals("Mruczek_PL", Lang.tr("Mruczek_PL")); // nieznane (nicki, nazwy skinów) zostają
		} finally {
			Lang.polish = true;
		}
	}

	/** Każdy tekst, który kod przepuszcza przez tr(…) albo przez pomocniki okna, musi mieć angielski odpowiednik. */
	@Test
	void everyGuiTextHasEnglish() throws Exception {
		Pattern p = Pattern.compile("(?:\\btr|header|hint|toggle|pairBtn|trust|addBtn|release|stop|withdraw|Keys\\.register)\\(\"((?:[^\"\\\\]|\\\\.)*)\"(?:, \"((?:[^\"\\\\]|\\\\.)*)\")?");
		List<String> missing = new ArrayList<>();
		try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
			for (Path f : (Iterable<Path>) files.filter(x -> x.toString().endsWith(".java") && !x.toString().contains("Commands"))::iterator) {
				String src = Files.readString(f);
				Matcher m = p.matcher(src);
				while (m.find()) {
					boolean keys = m.group().startsWith("Keys.register");
					for (int g = 1; g <= (keys ? 2 : 1); g++) {
						String s = m.group(g);
						if (s != null && !s.isEmpty() && !Lang.known(s)) missing.add(f.getFileName() + ": " + s);
					}
				}
			}
		}
		for (Pose x : Pose.values()) {
			if (!Lang.known(x.label)) missing.add("Pose " + x.label);
			if (!Lang.known(x.brief)) missing.add("Pose " + x.brief);
		}
		for (Emote x : Emote.values()) {
			if (!Lang.known(x.label)) missing.add("Emote " + x.label);
			if (!Lang.known(x.symbol + " " + x.label)) missing.add("Emote key " + x.symbol + " " + x.label);
		}
		for (Item x : Item.values()) if (!Lang.known(x.label)) missing.add("Item " + x.label);
		for (Kit x : Kit.values()) if (!Lang.known(x.label)) missing.add("Kit " + x.label);
		for (Voice x : Voice.values()) if (!Lang.known(x.label)) missing.add("Voice " + x.label);
		assertTrue(missing.isEmpty(), "brak angielskiego dla: " + missing);
	}
}
