package pl.vsake.femboy.acc;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Strój wysyłany innym graczom z modem: to samo w obie strony, śmieci z sieci nie wywalają gry. */
class OutfitWireTest {
	@Test
	void roundTrip() {
		Outfit o = Kit.CATBOY.outfit();
		o.fur = "ff8800";
		o.color = "auto";
		List<String> w = o.toWire();
		assertEquals(pl.vsake.femboy.net.Payloads.OUTFIT_FIELDS - 1, w.size()); // + licznik głaskań, dokładany przy wysyłce
		Outfit back = Outfit.fromWire(w);
		assertNotNull(back);
		assertEquals(w, back.toWire());
	}

	@Test
	void emptyOutfitStaysEmpty() {
		Outfit back = Outfit.fromWire(new Outfit().toWire());
		assertNotNull(back);
		assertTrue(back.isEmpty());
	}

	@Test
	void garbageIsIgnored() {
		assertNull(Outfit.fromWire(List.of("a", "b")));
		List<String> w = new ArrayList<>(new Outfit().toWire());
		w.set(0, "nie-ma-takich-uszu");
		w.set(w.size() - 2, "<script>");
		w.set(w.size() - 1, "zielononiebieski??");
		Outfit back = Outfit.fromWire(w);
		assertNotNull(back);
		assertNull(back.item(Item.Slot.EARS));
		assertEquals("auto", back.fur);
		assertEquals("pink", back.color);
	}
}
