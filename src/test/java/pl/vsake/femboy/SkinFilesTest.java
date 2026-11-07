package pl.vsake.femboy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import pl.vsake.femboy.gen.Style;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Skiny w skins/ (trafiają do jara): poprawne PNG, zgodne z listami z laby.net, bez duplikatów. */
class SkinFilesTest {
	private static final Path SKINS = Path.of("skins");

	@Test
	void everyStyleHasFolderWithSkins() throws IOException {
		for (Style st : Style.values()) {
			Path dir = SKINS.resolve(st.id);
			assertTrue(Files.isDirectory(dir), "brak folderu " + dir);
			assertTrue(pngs(dir).size() >= 100, st.id + ": za mało skinów");
		}
	}

	@Test
	void everySkinIsValidMinecraftSkin() throws IOException {
		for (Style st : Style.values())
			for (Path f : pngs(SKINS.resolve(st.id))) {
				BufferedImage img = ImageIO.read(f.toFile());
				assertNotNull(img, "nie PNG: " + f);
				assertEquals(64, img.getWidth(), f.toString());
				assertTrue(img.getHeight() == 64 || img.getHeight() == 32, f + " ma " + img.getHeight() + " px wysokości");
				String n = f.getFileName().toString();
				assertTrue(n.matches("[0-9a-f]{32}_(slim|wide)\\.png"), "dziwna nazwa " + n);
			}
	}

	@Test
	void labyListsMatchFiles() throws IOException {
		for (Style st : Style.values()) {
			Path json = SKINS.resolve("laby-" + st.id + ".json");
			assertTrue(Files.isRegularFile(json), "brak " + json);
			JsonArray arr = JsonParser.parseString(Files.readString(json)).getAsJsonArray();
			Set<String> listed = new HashSet<>();
			for (JsonElement e : arr) {
				JsonObject o = e.getAsJsonObject();
				String file = o.get("file").getAsString();
				assertTrue(file.startsWith(st.id + "/"), file);
				String hash = o.get("hash").getAsString();
				assertTrue(file.contains(hash), file + " nie pasuje do " + hash);
				assertEquals(o.get("slim").getAsBoolean(), file.endsWith("_slim.png"), file);
				listed.add(file.substring(st.id.length() + 1));
			}
			for (Path f : pngs(SKINS.resolve(st.id)))
				assertTrue(listed.contains(f.getFileName().toString()), f + " nie ma w " + json);
		}
	}

	@Test
	void noSkinInTwoPools() throws IOException {
		Set<String> hashes = new HashSet<>();
		for (Style st : Style.values())
			for (Path f : pngs(SKINS.resolve(st.id))) {
				String h = f.getFileName().toString().substring(0, 32);
				assertTrue(hashes.add(h), "skin " + h + " jest w dwóch pulach");
			}
	}

	@Test
	void styleLookup() {
		assertSame(Style.CATBOY, Style.byId("catboy"));
		assertSame(Style.EGIRL, Style.byId("e-girl"));
		assertSame(Style.EGIRL, Style.byId("E_GIRL"));
		assertNull(Style.byId("off"));
		assertNull(Style.byId(null));
		Style s = Style.FEMBOY;
		for (int i = 0; i < Style.values().length; i++) s = s.next();
		assertSame(Style.FEMBOY, s, "next() wraca do początku");
	}

	private static List<Path> pngs(Path dir) throws IOException {
		try (Stream<Path> s = Files.list(dir)) {
			return s.filter(p -> p.toString().endsWith(".png")).sorted().toList();
		}
	}
}
