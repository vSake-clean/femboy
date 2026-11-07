package pl.vsake.femboy.acc;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Przedmioty, stroje, zestawy, kolory, malowanie i animacje – logika bez gry. */
class LookTest {
	// ---------- Item ----------

	@Test
	void itemIdsUniqueInSlotAndRoundTrip() {
		for (Item.Slot slot : Item.Slot.values()) {
			Set<String> ids = new HashSet<>();
			for (Item it : Item.of(slot)) {
				assertTrue(ids.add(it.id), "powtórzone id " + it.id + " w " + slot);
				assertSame(it, Item.of(slot, it.id));
				assertSame(it, Item.of(slot, it.id.toUpperCase()));
			}
			assertFalse(Item.of(slot).isEmpty(), "pusty slot " + slot);
			assertSame(slot, Item.Slot.byId(slot.id));
		}
	}

	@Test
	void toggleSlotsHaveOneItemAndAcceptYes() {
		for (Item.Slot slot : Item.Slot.values()) {
			if (!slot.toggle) continue;
			assertEquals(1, Item.of(slot).size());
			assertNotNull(Item.of(slot, "tak"));
			assertNotNull(Item.of(slot, "yes"));
		}
	}

	@Test
	void slotIdsDoNotClashWithKitIds() {
		// /femboy acc me <zestaw> i /femboy acc me <miejsce> są na tym samym poziomie komendy
		for (Item.Slot slot : Item.Slot.values()) assertNull(Kit.byId(slot.id), slot.id);
	}

	// ---------- Outfit ----------

	@Test
	void outfitSetGetEverySlot() {
		for (Item it : Item.values()) {
			Outfit o = new Outfit();
			o.set(it.slot, it);
			assertSame(it, o.item(it.slot));
			assertFalse(o.isEmpty());
			o.set(it.slot, null);
			assertTrue(o.isEmpty());
		}
	}

	@Test
	void outfitCopyIsIndependent() {
		Outfit o = Kit.DEVIL.outfit();
		Outfit c = o.copy();
		for (Item.Slot s : Item.Slot.values()) assertEquals(o.get(s), c.get(s));
		c.set(Item.Slot.HEAD, Item.HEAD_HALO);
		assertEquals(Item.HEAD_HORNS, o.item(Item.Slot.HEAD));
	}

	@Test
	void outfitLookRoundTrip() {
		for (long seed = 0; seed < 300; seed++) {
			Look l = Kit.random(seed, List.of(Kit.values()), -1);
			Outfit o = Outfit.of(l, false);
			Look back = o.resolve(-1, seed);
			for (Item.Slot s : Item.Slot.values()) assertEquals(l.get(s), back.get(s), "seed " + seed + " slot " + s);
			assertEquals(l.fur, back.fur);
			assertEquals(l.accent, back.accent);
		}
	}

	@Test
	void autoFurUsesHairColor() {
		Outfit o = Kit.CATBOY.outfit(); // fur = auto
		assertEquals(0x123456, o.resolve(0x123456, 1L).fur);
	}

	// ---------- Kit ----------

	@Test
	void everyKitHasItemsInRightSlots() {
		for (Kit k : Kit.values()) {
			Outfit o = k.outfit();
			assertFalse(o.isEmpty(), k.id);
			for (Item.Slot s : Item.Slot.values()) {
				Item it = o.item(s);
				if (it != null) assertSame(s, it.slot);
			}
			assertSame(k, Kit.byId(k.id));
			assertSame(k, Kit.byId(k.label));
		}
	}

	@Test
	void randomIsDeterministicAndRich() {
		for (long seed = -500; seed < 1500; seed++) {
			Look a = Kit.random(seed, List.of(Kit.values()), 0xAA7744);
			Look b = Kit.random(seed, List.of(Kit.values()), 0xAA7744);
			assertEquals(a.textureKey(), b.textureKey(), "seed " + seed);
			assertTrue(a.items().size() >= 2, "za mało dodatków dla seed " + seed + ": " + a.describe());
		}
	}

	@Test
	void randomRespectsKitPool() {
		for (long seed = 0; seed < 400; seed++) {
			Look l = Kit.random(seed, List.of(Kit.ANGEL), -1);
			assertSame(Item.HEAD_HALO, l.get(Item.Slot.HEAD), "aniołek zawsze z aureolką");
			assertNull(l.get(Item.Slot.EARS));
		}
	}

	@Test
	void newItemsAppearInRandomLooks() {
		Set<Item> seen = new HashSet<>();
		for (long seed = 0; seed < 20000; seed++) seen.addAll(Kit.random(seed, List.of(Kit.values()), -1).items());
		for (Item it : new Item[]{Item.EARS_BEAR, Item.TAIL_DEVIL, Item.HEAD_HALO, Item.HEAD_HORNS, Item.BACK_BAT,
			Item.BACK_ANGEL, Item.BACK_BUTTERFLY, Item.FACE_GLASSES, Item.FACE_MASK, Item.ARMS_STRIPES, Item.NECK_LOCK})
			assertTrue(seen.contains(it), it + " nigdy nie wypada w losowaniu");
	}

	// ---------- Palette ----------

	@Test
	void paletteParsing() {
		assertEquals(0xFF8FBE, Palette.parse("pink"));
		assertEquals(0xFF8FBE, Palette.parse(" PINK "));
		assertEquals(0xFF88CC, Palette.parse("ff88cc"));
		assertEquals(0xFF88CC, Palette.parse("#FF88CC"));
		assertEquals(0xFF88CC, Palette.parse("f8c"));
		assertEquals(-1, Palette.parse("niebieskawy"));
		assertEquals(-1, Palette.parse(null));
		assertEquals(-1, Palette.parse("12345"));
		for (String id : Palette.ids()) assertTrue(Palette.parse(id) >= 0, id);
		assertEquals("różowy", Palette.label(0xFF8FBE));
		assertEquals("#010203", Palette.label(0x010203));
	}

	@Test
	void everyEarAndTailHasNaturalFurs() {
		for (Item it : Item.values()) {
			if (it.slot != Item.Slot.EARS && it.slot != Item.Slot.TAIL) continue;
			int[] f = it.slot == Item.Slot.EARS ? Palette.furs(it, null) : Palette.furs(null, it);
			assertTrue(f.length >= 3, it.toString());
		}
	}

	// ---------- Painter ----------

	@Test
	void everyItemPaintsVisiblePixels() {
		for (Item it : Item.values()) {
			Look l = Look.builder().set(it).fur(0x804020).accent(0x3060C0).build();
			int[] px = Painter.paint(l);
			assertEquals(Parts.TEX_W * Parts.TEX_H, px.length);
			for (Parts.Piece p : Parts.PIECES) {
				if (!p.usedBy(l)) continue;
				for (Parts.Box b : p.boxes) {
					int[] r = Painter.rect(b, Painter.Face.FRONT);
					int opaque = 0;
					for (int y = r[1]; y < r[1] + r[3]; y++)
						for (int x = r[0]; x < r[0] + r[2]; x++) if ((px[y * Parts.TEX_W + x] >>> 24) != 0) opaque++;
					assertTrue(opaque > 0, it + ": " + p.name + "/" + b.tag + " ma niewidoczny przód");
				}
			}
		}
	}

	@Test
	void differentLooksGetDifferentTextureKeys() {
		Look a = Kit.DEVIL.outfit().resolve(-1, 1);
		Look b = Kit.ANGEL.outfit().resolve(-1, 1);
		assertNotEquals(a.textureKey(), b.textureKey());
		assertEquals(a.textureKey(), Kit.DEVIL.outfit().resolve(-1, 999).textureKey(), "ziarno animacji nie zmienia tekstury");
	}

	// ---------- Anim ----------

	@Test
	void animationOutputsAreFinite() {
		Look l = Look.builder().seed(42).build();
		float[] out = new float[6];
		Anim.In in = new Anim.In();
		for (int step = 0; step < 400; step++) {
			in.age = step * 1.7f;
			in.limbSwing = step * 0.3f;
			in.limbAmount = (step % 10) / 10f;
			in.sneaking = step % 7 == 0;
			in.riding = step % 11 == 0;
			in.swimming = step % 13 == 0;
			in.helmet = step % 5 == 0;
			in.pat = step % 3 == 0 ? (step % 10) : -1f;
			for (Parts.Piece p : Parts.PIECES) {
				Anim.apply(p, l, in, out);
				for (float v : out) assertTrue(Float.isFinite(v), p.name);
				// nic nie obraca się o więcej niż ~pół obrotu na klatkę – to byłby błąd we wzorze
				for (int i = 0; i < 3; i++) assertTrue(Math.abs(out[i]) < 3.2f, p.name + " kąt " + out[i]);
			}
		}
	}

	@Test
	void twitchStaysInRange() {
		for (int t = 0; t < 5000; t += 3) {
			float v = Anim.twitch(t, 1234567L, -1);
			assertTrue(v >= 0f && v <= 1f);
		}
	}
}
