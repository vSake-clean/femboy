package pl.vsake.femboy.acc;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Geometria dodatków: wszystko się mieści w teksturze 128x128 i nic na siebie nie nachodzi. */
class PartsTest {
	@Test
	void piecesLoadWithoutOverflow() {
		// pakowanie dzieje się w bloku static – przepełnienie rzuciłoby wyjątkiem i wyłączyło wszystkie dodatki w grze
		assertFalse(Parts.PIECES.isEmpty());
	}

	@Test
	void everyItemHasGeometry() {
		for (Item it : Item.values()) {
			boolean found = false;
			for (Parts.Piece p : Parts.PIECES) if (p.items.contains(it)) found = true;
			assertTrue(found, "brak modelu dla " + it);
		}
	}

	@Test
	void boxesStayInsideTexture() {
		for (Parts.Piece p : Parts.PIECES)
			for (Parts.Box b : p.boxes) {
				assertTrue(b.u >= 0 && b.v >= 0, p.name + "/" + b.tag);
				assertTrue(b.u + b.texW() <= Parts.TEX_W, p.name + "/" + b.tag + " za szeroki");
				assertTrue(b.v + b.texH() <= Parts.TEX_H, p.name + "/" + b.tag + " za wysoki");
				assertTrue(b.sx > 0 && b.sy > 0 && b.sz > 0, p.name + "/" + b.tag + " pusty wymiar");
			}
	}

	@Test
	void textureRegionsDoNotOverlap() {
		List<int[]> rects = new ArrayList<>();
		List<String> names = new ArrayList<>();
		for (Parts.Piece p : Parts.PIECES)
			for (Parts.Box b : p.boxes) {
				rects.add(new int[]{b.u, b.v, b.u + b.texW(), b.v + b.texH()});
				names.add(p.name + "/" + b.tag);
			}
		for (int i = 0; i < rects.size(); i++)
			for (int j = i + 1; j < rects.size(); j++) {
				int[] a = rects.get(i), c = rects.get(j);
				boolean overlap = a[0] < c[2] && c[0] < a[2] && a[1] < c[3] && c[1] < a[3];
				assertFalse(overlap, names.get(i) + " nachodzi na " + names.get(j));
			}
	}

	@Test
	void pieceNamesUnique() {
		List<String> seen = new ArrayList<>();
		for (Parts.Piece p : Parts.PIECES) {
			assertFalse(seen.contains(p.name), "powtórzona nazwa " + p.name);
			seen.add(p.name);
		}
	}

	@Test
	void sidedPiecesComeInPairs() {
		// uszy, różki, skrzydła, zakolanówki itd. – jeśli jest prawy, musi być lewy
		for (Parts.Piece p : Parts.PIECES) {
			if (p.parent != null || p.side == 0) continue;
			String other = p.name.contains("_r") ? p.name.replaceFirst("_r(_|$)", "_l$1") : p.name.replaceFirst("_l(_|$)", "_r$1");
			if (other.equals(p.name)) continue;
			assertNotNull(Parts.byName(other), "brak pary dla " + p.name);
		}
	}

	@Test
	void pawsAndWarmersHaveBothFits() {
		for (String base : new String[]{"paw_r", "paw_l", "warmer_r", "warmer_l"}) {
			assertNotNull(Parts.byName(base + "_wide"), base + "_wide");
			assertNotNull(Parts.byName(base + "_slim"), base + "_slim");
		}
	}
}
