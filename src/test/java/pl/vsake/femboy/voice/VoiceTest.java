package pl.vsake.femboy.voice;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Odgłosy: sounds.json zgodny z listą wariantów (kolejność = protokół), pliki istnieją, wiadomości z sieci są sprawdzane. */
class VoiceTest {
	private static final Path RES = Path.of("src/main/resources/assets/femboy");

	private static JsonObject sounds() throws IOException {
		return JsonParser.parseString(Files.readString(RES.resolve("sounds.json"))).getAsJsonObject();
	}

	@Test
	void everyVariantHasItsOwnEventWithExactlyThatFile() throws IOException {
		JsonObject json = sounds();
		for (Voice v : Voice.values()) {
			assertTrue(v.variants() >= 3, v.id + ": za mało wariantów");
			assertEquals(v.variants(), new HashSet<>(v.files).size(), v.id + ": powtórzone pliki");
			for (int i = 0; i < v.variants(); i++) {
				JsonObject ev = json.getAsJsonObject(v.event(i));
				assertNotNull(ev, "brak " + v.event(i) + " w sounds.json");
				var list = ev.getAsJsonArray("sounds");
				assertEquals(1, list.size(), v.event(i) + ": ma mieć dokładnie jeden plik (wszyscy słyszą ten sam)");
				assertEquals(v.files.get(i), list.get(0).getAsJsonObject().get("name").getAsString(), v.event(i));
			}
			assertNull(json.get(v.event(v.variants())), v.id + ": w sounds.json jest więcej wariantów niż w Voice");
		}
	}

	@Test
	void manyMeowsAndBarks() {
		assertTrue(Voice.MEOW.variants() >= 10);
		assertTrue(Voice.BARK.variants() >= 10);
	}

	@Test
	void ownFilesExistAndGameFilesAreInAssetIndex() throws IOException {
		Path index = null;
		Path dir = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/assets/indexes");
		if (Files.isDirectory(dir))
			try (Stream<Path> s = Files.list(dir)) {
				index = s.filter(p -> p.getFileName().toString().startsWith("1.21.11")).findFirst().orElse(null);
			}
		Set<String> game = new HashSet<>();
		if (index != null)
			game.addAll(JsonParser.parseString(Files.readString(index)).getAsJsonObject().getAsJsonObject("objects").keySet());
		for (Voice v : Voice.values())
			for (String f : v.files) {
				String ns = f.substring(0, f.indexOf(':')), path = f.substring(f.indexOf(':') + 1);
				if (ns.equals("femboy")) assertTrue(Files.isRegularFile(RES.resolve("sounds/" + path + ".ogg")), f);
				else if (!game.isEmpty()) assertTrue(game.contains("minecraft/sounds/" + path + ".ogg"), "nie ma w grze: " + f);
			}
	}

	@Test
	void messageRoundTrip() {
		Voice.Msg m = new Voice.Msg(Voice.BARK, 20, 107, 0xCAFEBABE);
		assertEquals(m, Voice.Msg.decode(m.encode()));
		assertEquals(m, Voice.Msg.decode("bark 20 107 cafebabe"));
	}

	@Test
	void rejectsBadMessages() {
		assertNull(Voice.Msg.decode(""));
		assertNull(Voice.Msg.decode("moo 0 100 1"));                    // nieznany odgłos
		assertNull(Voice.Msg.decode("meow 999 100 1"));                 // wariant spoza listy
		assertNull(Voice.Msg.decode("meow -1 100 1"));
		assertNull(Voice.Msg.decode("meow 0 5000 1"));                  // absurdalna wysokość
		assertNull(Voice.Msg.decode("meow 0 100"));
		assertNull(Voice.Msg.decode("meow x 100 1"));
		assertNull(Voice.Msg.decode("meow 0 100 1 " + "a".repeat(80)));
	}

	@Test
	void variantLabelsAreShortAndUnique() {
		assertEquals("wolf cute bark2", Voice.BARK.variantLabel(Voice.BARK.files.indexOf("minecraft:mob/wolf/cute/bark2")));
		assertEquals("cat stray idle1", Voice.MEOW.variantLabel(4));
		for (Voice v : Voice.values()) {
			Set<String> seen = new HashSet<>();
			for (int i = 0; i < v.variants(); i++) assertTrue(seen.add(v.variantLabel(i)), v.id + ": powtórzona nazwa " + v.variantLabel(i));
		}
	}
}
