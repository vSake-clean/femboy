package pl.vsake.femboy.fx;

import net.minecraft.client.sound.OggAudioStream;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Własne nagrania (.ogg w jarze) muszą się dać odczytać dekoderem samej gry – inaczej w grze jest po prostu cisza. */
class OwnSoundsTest {
	private static final Path SOUNDS = Path.of("src/main/resources/assets/femboy/sounds");

	@Test
	void everyOwnSoundDecodesWithTheGameDecoder() throws IOException {
		try (Stream<Path> files = Files.walk(SOUNDS)) {
			for (Path f : files.filter(p -> p.toString().endsWith(".ogg")).toList()) {
				float[] peak = {0f};
				int[] n = {0};
				try (InputStream in = new BufferedInputStream(Files.newInputStream(f))) {
					OggAudioStream ogg = new OggAudioStream(in);
					assertEquals(1, ogg.getFormat().getChannels(), f + ": dźwięk przy postaci musi być mono");
					while (ogg.read(v -> {
						n[0]++;
						peak[0] = Math.max(peak[0], Math.abs(v));
					})) {
					}
				}
				System.out.println(f.getFileName() + ": " + n[0] + " próbek, szczyt " + peak[0]);
				assertTrue(n[0] > 2000, f + ": prawie nic się nie zdekodowało (" + n[0] + " próbek)");
				assertTrue(peak[0] > 0.1f, f + ": cisza (szczyt " + peak[0] + ")");
			}
		}
	}
}
