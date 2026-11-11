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
		float[] out = new float[Anim.OUT];
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
			in.flat = step % 4 == 0 ? 1f : 0f;
			in.perk = step % 6 == 0 ? 1f : 0f;
			in.wag = step % 9 == 0 ? 1f : 0f;
			in.puff = step % 8 == 0 ? 1f : 0f;
			for (Parts.Piece p : Parts.PIECES) {
				Anim.apply(p, l, in, out);
				for (float v : out) assertTrue(Float.isFinite(v), p.name);
				// nic nie obraca się o więcej niż ~pół obrotu na klatkę – to byłby błąd we wzorze
				for (int i = 0; i < 3; i++) assertTrue(Math.abs(out[i]) < 3.2f, p.name + " kąt " + out[i]);
				assertTrue(out[6] >= 0f && out[6] <= 1f, p.name + " pogrubienie " + out[6]);
			}
		}
	}

	@Test
	void earsReactToHissAndMeow() {
		Look l = Look.builder().set(Item.EARS_CAT).set(Item.TAIL_CAT).seed(7).build();
		float[] calm = new float[Anim.OUT], flat = new float[Anim.OUT], perk = new float[Anim.OUT];
		Parts.Piece ear = Parts.byName("ear_cat_l"), tail = Parts.byName("tail_cat_0");
		Anim.In in = new Anim.In();
		in.age = 1000.5f; // poza strzyżeniem uszkiem
		while (Anim.twitch(in.age, l.seed, 1) != 0f) in.age += 1f;
		Anim.apply(ear, l, in, calm);
		in.flat = 1f;
		in.puff = 1f;
		Anim.apply(ear, l, in, flat);
		float[] puffed = new float[Anim.OUT];
		Anim.apply(tail, l, in, puffed);
		in.flat = in.puff = 0f;
		in.perk = 1f;
		Anim.apply(ear, l, in, perk);
		assertTrue(flat[0] < calm[0] - 0.5f, "syk: ucho kładzie się do tyłu");
		assertTrue(flat[2] > calm[2] + 0.5f, "syk: lewe ucho rozkłada się na bok");
		assertTrue(perk[4] < calm[4], "miau: ucho idzie w górę");
		assertTrue(perk[2] < calm[2], "miau: ucho się prostuje");
		assertTrue(puffed[6] > 0.3f, "syk: ogon nastroszony");
	}

	// ---------- 1.6: dzwoneczki, bez zakolanówek ----------

	@Test
	void bellItemsJingleAndCatsPurr() {
		for (Item it : new Item[]{Item.NECK_BELL, Item.NECK_BELLS, Item.HEAD_BOW_BELL, Item.EARS_CAT_BELL})
			assertTrue(Look.builder().set(it).build().hasBell(), it.toString());
		assertFalse(Look.builder().set(Item.NECK_HEART).set(Item.HEAD_BOW).build().hasBell());
		assertTrue(Look.builder().set(Item.EARS_CAT_BELL).build().isCat());
		assertTrue(Look.builder().set(Item.TAIL_CAT).build().isCat());
		assertFalse(Look.builder().set(Item.EARS_PUPPY).build().isCat());
	}

	@Test
	void noSocksAnywhere() {
		assertNull(Item.Slot.byId("legs"));
		for (Item it : Item.values()) assertFalse(it.label.contains("zakolan"), it.toString());
	}

	@Test
	void bellAndScarfItemsAppearInRandomLooks() {
		Set<Item> seen = new HashSet<>();
		for (long seed = 0; seed < 20000; seed++) seen.addAll(Kit.random(seed, List.of(Kit.values()), -1).items());
		for (Item it : new Item[]{Item.EARS_CAT_BELL, Item.HEAD_BOW_BELL, Item.NECK_BELLS, Item.NECK_ID, Item.NECK_SCARF})
			assertTrue(seen.contains(it), it + " nigdy nie wypada w losowaniu");
		// stały efekt cząsteczek tylko z własnego wyboru – inaczej cały serwer by iskrzył
		assertFalse(seen.contains(Item.FX_HEARTS) || seen.contains(Item.FX_SPARKLES));
	}

	@Test
	void twitchStaysInRange() {
		for (int t = 0; t < 5000; t += 3) {
			float v = Anim.twitch(t, 1234567L, -1);
			assertTrue(v >= 0f && v <= 1f);
		}
	}
}
