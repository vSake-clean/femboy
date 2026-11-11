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

import static org.junit.jupiter.api.Assertions.*;

/** Animacje ciała i rzeczy we dwoje: matematyka póz nie psuje modelu, sygnały przechodzą w obie strony, klawisze mają nazwy. */
class PoseMathTest {
	private static final Path RES = Path.of("src/main/resources/assets/femboy");

	@Test
	void everyPoseGivesFiniteRigAndChangesSomething() {
		Set<String> ids = new HashSet<>();
		for (Pose p : Pose.values()) {
			assertTrue(ids.add(p.id), p.id);
			assertSame(p, Pose.byId(p.id.toUpperCase()));
			boolean changed = false;
			for (float t = 0f; t < 6f; t += 0.13f) {
				for (float w : new float[]{0.2f, 1f}) {
					Rig r = new Rig().standing();
					PoseMath.apply(p, w, t, r);
					assertTrue(r.finite(), p.id + " t=" + t);
					// nic nie odlatuje: postać zostaje w obrębie kilku bloków od swojego miejsca
					assertTrue(Math.abs(r.root.x) < 40 && Math.abs(r.root.y) < 40 && Math.abs(r.root.z) < 40, p.id);
					changed |= r.root.y != 0 || r.rightArm.pitch != 0 || r.rightArm.roll != 0 || r.head.roll != 0 || r.head.pitch != 0 || r.root.x != 0;
				}
			}
			assertTrue(changed, p.id + ": poza nic nie robi");
		}
		assertNull(Pose.byId("nie-ma"));
		assertTrue(Pose.values().length >= 10);
	}

	@Test
	void zeroWeightLeavesVanillaPose() {
		for (Pose p : Pose.values()) {
			Rig r = new Rig().standing();
			r.rightArm.pitch = 0.4f;
			r.leftLeg.z = 4f;
			PoseMath.apply(p, 0f, 1.3f, r);
			assertEquals(0.4f, r.rightArm.pitch, 1e-6f, p.id);
			assertEquals(4f, r.leftLeg.z, 1e-6f, p.id);
			assertEquals(0f, r.root.y, 1e-6f, p.id);
		}
		Rig r = new Rig().standing();
		PoseMath.pair(PoseMath.PAIR_HUG, 0f, 1f, 0, 1, 0, true, r);
		PoseMath.boop(r, 0f);
		assertEquals(0f, r.rightArm.pitch, 1e-6f);
		assertEquals(0f, r.headSquash, 1e-6f);
	}

	@Test
	void lyingPosesPutBodyOnTheGround() {
		// stopy stojącego są 24 px pod szyją; leżący musi zjechać w dół, a siedzący mniej
		Rig sit = new Rig().standing(), belly = new Rig().standing(), loaf = new Rig().standing(), nap = new Rig().standing();
		PoseMath.apply(Pose.SIT, 1f, 1f, sit);
		PoseMath.apply(Pose.BELLY, 1f, 1f, belly);
		PoseMath.apply(Pose.LOAF, 1f, 1f, loaf);
		PoseMath.apply(Pose.NAP, 1f, 1f, nap);
		assertTrue(sit.root.y > 8 && sit.root.y < 13, "siad: " + sit.root.y);
		for (Rig r : new Rig[]{belly, loaf, nap}) assertTrue(r.root.y > 14 && r.root.y < 23, "leżenie: " + r.root.y);
		assertEquals(-Math.PI / 2, belly.root.pitch, 1e-3);
		assertEquals(Math.PI / 2, loaf.root.pitch, 1e-3);
		assertEquals(Math.PI / 2, nap.root.roll, 1e-3);
	}

	@Test
	void armPointsWhereAsked() {
		float[][] dirs = {{0, 1, 0}, {-1, 0, 0}, {1, 0, 0}, {0, 0, -1}, {-0.5f, 0.6f, -0.3f}, {0.4f, -0.2f, -0.7f}};
		for (float[] d : dirs) {
			Rig r = new Rig().standing();
			PoseMath.point(r.rightArm, d[0], d[1], d[2], 1f);
			float[] got = PoseMath.direction(r.rightArm.pitch, r.rightArm.yaw, r.rightArm.roll);
			float len = (float) Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
			for (int i = 0; i < 3; i++) assertEquals(d[i] / len, got[i], 1e-4f, "oś " + i);
		}
		// zerowy kierunek nic nie zmienia
		Rig r = new Rig().standing();
		PoseMath.point(r.leftArm, 0, 0, 0, 1f);
		assertEquals(0f, r.leftArm.roll, 1e-6f);
	}

	@Test
	void pairPosesAreFinite() {
		for (int kind = 0; kind <= PoseMath.PAIR_PIGGY_RIDER; kind++) {
			Rig r = new Rig().standing();
			PoseMath.pair(kind, 1f, 2.5f, -0.4f, 0.8f, -0.2f, kind % 2 == 0, r);
			PoseMath.boop(r, 0.7f);
			assertTrue(r.finite(), "para " + kind);
		}
		Rig hug = new Rig().standing();
		PoseMath.pair(PoseMath.PAIR_HUG, 1f, 0f, 0, 0, 0, true, hug);
		assertTrue(hug.rightArm.pitch < -1f && hug.leftArm.pitch < -1f, "ręce do przodu");
		assertTrue(hug.rightArm.yaw < 0 && hug.leftArm.yaw > 0, "ręce do środka");
	}

	@Test
	void weightFadesInAndOut() {
		assertEquals(0f, PoseMath.weight(0f, -1f), 1e-6f);
		assertEquals(1f, PoseMath.weight(5f, -1f), 1e-6f);
		assertEquals(0f, PoseMath.weight(5f, 0f), 1e-6f);
		assertEquals(1f, PoseMath.weight(1f, 1f), 1e-6f);
		for (float t = 0f; t < 1f; t += 0.01f) {
			float w = PoseMath.weight(t, 1f - t);
			assertTrue(w >= 0f && w <= 1f);
		}
	}

	@Test
	void newSignalsRoundTrip() {
		UUID u = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
		Signal[] all = {Signal.pose(Pose.BELLY, 1), Signal.pose(null, 2), Signal.boop(u, 3), Signal.bond('h', u, 4), Signal.bond('p', u, 5),
			Signal.bond('c', null, 6), Signal.ok(u, 7), Signal.ok(null, 8), Signal.laser(-1234567.84, 64, 20.25, 9), Signal.laserOff(10),
			Signal.yarn(10.5, 70, -3.25, 0.5, 0.14, -0.31, 11)};
		for (Signal s : all) {
			assertTrue(s.arg().length() <= Signal.MAX_ARG, s.encode());
			assertEquals(s, Signal.decode(s.encode()), s.encode());
		}
		assertSame(Pose.BELLY, Signal.pose(Pose.BELLY, 1).poseArg());
		assertNull(Signal.pose(null, 1).poseArg());
		assertEquals(u, Signal.boop(u, 1).uuidArg());
		assertEquals('p', Signal.bond('p', u, 1).bondKind());
		assertEquals(u, Signal.bond('p', u, 1).bondTarget());
		assertEquals(0, Signal.bond('p', null, 1).bondKind());
		assertNull(Signal.ok(null, 1).uuidArg());
		assertArrayEquals(new double[]{-1234567.8, 64, 20.3}, Signal.laser(-1234567.84, 64, 20.25, 9).numbersArg(), 0.051);
		assertNull(Signal.laserOff(1).numbersArg());
		assertEquals(6, Signal.yarn(1, 2, 3, 0.1, 0.2, 0.3, 1).numbersArg().length);
	}

	@Test
	void rejectsBadNewSignals() {
		String u = "123e4567-e89b-12d3-a456-426614174000";
		assertNull(Signal.decode("pose nie-ma 1"));
		assertNull(Signal.decode("boop - 1"));
		assertNull(Signal.decode("boop nie-uuid 1"));
		assertNull(Signal.decode("bond x" + u + " 1"));
		assertNull(Signal.decode("bond h 1"));
		assertNull(Signal.decode("bond " + u + " 1"));
		assertNull(Signal.decode("ok nie-uuid 1"));
		assertNull(Signal.decode("laser 1,2 1"));
		assertNull(Signal.decode("laser 1,2,NaN 1"));
		assertNull(Signal.decode("laser 1,2,1e99 1"));
		assertNull(Signal.decode("yarn 1,2,3 1"));
		assertNull(Signal.decode("yarn 1,2,3,50,0,0 1")); // za szybki rzut
		assertNull(Signal.decode("yarn 1,2,3,a,b,c 1"));
	}

	@Test
	void everyNewKeyAndSoundHasTranslations() throws IOException {
		for (String lang : new String[]{"pl_pl", "en_us"}) {
			JsonObject l = JsonParser.parseString(Files.readString(RES.resolve("lang/" + lang + ".json"))).getAsJsonObject();
			for (Pose p : Pose.values()) assertNotNull(l.get("key.femboy.pose." + p.id), lang + ": animacja " + p.id);
			for (String k : new String[]{"boop", "hug", "hands", "carry", "piggy", "laser", "yarn", "wardrobe"})
				assertNotNull(l.get("key.femboy." + k), lang + ": " + k);
			assertNotNull(l.get("subtitles.femboy.boop"), lang);
		}
		JsonObject sounds = JsonParser.parseString(Files.readString(RES.resolve("sounds.json"))).getAsJsonObject();
		assertNotNull(sounds.getAsJsonObject("boop"));
		assertTrue(Files.isRegularFile(RES.resolve("sounds/fx/boop.ogg")));
	}

	@org.junit.jupiter.api.Test
	void wheelPicksByAngle() {
		int n = Pose.values().length;
		// środek = nic
		org.junit.jupiter.api.Assertions.assertEquals(-1, pl.vsake.femboy.gui.PoseWheel.pick(3, -4, 200, 120, n));
		// prosto w górę = pierwsza poza, potem zgodnie z zegarem; elipsa nie przekrzywia kątów
		org.junit.jupiter.api.Assertions.assertEquals(0, pl.vsake.femboy.gui.PoseWheel.pick(0, -100, 200, 120, n));
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n;
			org.junit.jupiter.api.Assertions.assertEquals(i, pl.vsake.femboy.gui.PoseWheel.pick(Math.sin(a) * 200 * 0.72, -Math.cos(a) * 120 * 0.72, 200, 120, n));
			org.junit.jupiter.api.Assertions.assertEquals(i, pl.vsake.femboy.gui.PoseWheel.pick(Math.sin(a + 0.2) * 900, -Math.cos(a + 0.2) * 540, 200, 120, n));
		}
		org.junit.jupiter.api.Assertions.assertEquals(-1, pl.vsake.femboy.gui.PoseWheel.pick(50, 50, 200, 120, 0));
		for (Pose p : Pose.values()) org.junit.jupiter.api.Assertions.assertTrue(p.brief.length() <= 12, p.id);
	}

	@org.junit.jupiter.api.Test
	void nickListOneNamePerLine() {
		java.util.List<String> l = Trust.parse("  Mruczek \n\nLisek_PL\r\nmruczek\n" + "x".repeat(60) + ", Ala; Ola");
		org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("Mruczek", "Lisek_PL", "x".repeat(Trust.MAX_NAME), "Ala", "Ola"), l);
		org.junit.jupiter.api.Assertions.assertTrue(Trust.has(l, "LISEK_pl"));
		org.junit.jupiter.api.Assertions.assertFalse(Trust.has(l, "Lisek"));
		org.junit.jupiter.api.Assertions.assertTrue(Trust.parse(null).isEmpty());
		org.junit.jupiter.api.Assertions.assertTrue(Trust.parse(" \n \n").isEmpty());
	}

	@org.junit.jupiter.api.Test
	void pawHasPadAndFourToes() {
		// środek poduszki pełny, rogi puste, obrazek symetryczny (lewa łapka = lustro prawej)
		org.junit.jupiter.api.Assertions.assertEquals(1f, WorldDraw.paw(8, 11));
		org.junit.jupiter.api.Assertions.assertEquals(0f, WorldDraw.paw(0, 0));
		org.junit.jupiter.api.Assertions.assertEquals(0f, WorldDraw.paw(15, 15));
		int toes = 0;
		boolean inToe = false;
		for (int x = 0; x < 16; x++) {
			boolean on = WorldDraw.paw(x, 4) > 0.5f || WorldDraw.paw(x, 6) > 0.5f;
			if (on && !inToe) toes++;
			inToe = on;
			for (int y = 0; y < 16; y++) org.junit.jupiter.api.Assertions.assertEquals(WorldDraw.paw(x, y), WorldDraw.paw(15 - x, y), 1e-6f);
		}
		org.junit.jupiter.api.Assertions.assertEquals(4, toes);
	}
}
