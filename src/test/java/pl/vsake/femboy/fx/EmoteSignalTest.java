package pl.vsake.femboy.fx;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import pl.vsake.femboy.net.Signal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Emotki (wzory pikselowe) i sygnały między graczami (emotka / clicker / smycz): to samo w obie strony, śmieci odrzucane. */
class EmoteSignalTest {
	private static final Path RES = Path.of("src/main/resources/assets/femboy");

	@Test
	void everyEmoteHasPictureAndUniqueId() {
		Set<String> ids = new HashSet<>();
		for (Emote e : Emote.values()) {
			assertTrue(ids.add(e.id), e.id);
			assertSame(e, Emote.byId(e.id));
			assertSame(e, Emote.byId(e.id.toUpperCase()));
			int opaque = 0;
			for (int y = 0; y < Emote.SIZE; y++)
				for (int x = 0; x < Emote.SIZE; x++) if (e.pixel(x, y) >= 0) opaque++;
			assertTrue(opaque >= 16, e.id + ": prawie pusty obrazek");
		}
		assertNull(Emote.byId("nie-ma"));
		assertNull(Emote.byId(null));
		assertTrue(Emote.values().length >= 4);
	}

	@Test
	void signalRoundTrip() {
		UUID u = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
		for (Signal s : new Signal[]{Signal.emote(Emote.ANGER, 0xCAFEBABE), Signal.click(7), Signal.leash(u, -1), Signal.leash(null, 5)})
			assertEquals(s, Signal.decode(s.encode()), s.encode());
		assertEquals(u, Signal.leash(u, 1).leashTarget());
		assertNull(Signal.leash(null, 1).leashTarget());
		assertSame(Emote.HEART, Signal.decode("emote heart 1").emoteArg());
	}

	@Test
	void rejectsBadSignals() {
		assertNull(Signal.decode(null));
		assertNull(Signal.decode(""));
		assertNull(Signal.decode("emote nie-ma 1"));
		assertNull(Signal.decode("teleport - 1"));
		assertNull(Signal.decode("leash nie-uuid 1"));
		assertNull(Signal.decode("click - zz"));
		assertNull(Signal.decode("click -"));
		assertNull(Signal.decode("emote heart 1 " + "a".repeat(90)));
		assertNull(Signal.of("emote", null, 1));
		// clicker nie niesie żadnych danych – cokolwiek przyszło, zostaje "-"
		assertEquals(Signal.NONE, Signal.of("click", "cokolwiek", 1).arg());
	}

	@Test
	void effectSoundsExist() throws IOException {
		JsonObject json = JsonParser.parseString(Files.readString(RES.resolve("sounds.json"))).getAsJsonObject();
		Path dir = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/assets/indexes");
		Set<String> game = new HashSet<>();
		if (Files.isDirectory(dir))
			try (Stream<Path> s = Files.list(dir)) {
				Path index = s.filter(p -> p.getFileName().toString().startsWith("1.21.11")).findFirst().orElse(null);
				if (index != null)
					game.addAll(JsonParser.parseString(Files.readString(index)).getAsJsonObject().getAsJsonObject("objects").keySet());
			}
		for (String ev : new String[]{"bell", "clicker", "purr_loop"}) {
			JsonObject o = json.getAsJsonObject(ev);
			assertNotNull(o, "brak " + ev + " w sounds.json");
			for (var el : o.getAsJsonArray("sounds")) {
				String f = el.getAsJsonObject().get("name").getAsString();
				// własne nagranie (clicker) musi być w jarze, dźwięki z gry – w indeksie zasobów gry
				if (f.startsWith("femboy:")) assertTrue(Files.isRegularFile(RES.resolve("sounds/" + f.substring(7) + ".ogg")), f);
				else if (!game.isEmpty()) assertTrue(game.contains("minecraft/sounds/" + f.substring(10) + ".ogg"), "nie ma w grze: " + f);
			}
		}
	}

	@Test
	void everyKeyHasTranslations() throws IOException {
		for (String lang : new String[]{"pl_pl", "en_us"}) {
			JsonObject l = JsonParser.parseString(Files.readString(RES.resolve("lang/" + lang + ".json"))).getAsJsonObject();
			for (Emote e : Emote.values()) assertNotNull(l.get("key.femboy.emote." + e.id), lang + ": emotka " + e.id);
			for (String k : new String[]{"key.femboy.clicker", "key.femboy.leash", "subtitles.femboy.bell", "subtitles.femboy.clicker"})
				assertNotNull(l.get(k), lang + ": " + k);
		}
	}
}
