package pl.vsake.femboy.dev;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.integrated.IntegratedServer;
import pl.vsake.femboy.FemboyConfig;
import pl.vsake.femboy.FemboyMod;
import pl.vsake.femboy.SkinManager;
import pl.vsake.femboy.acc.Accessories;
import pl.vsake.femboy.acc.Item;
import pl.vsake.femboy.acc.Kit;
import pl.vsake.femboy.acc.Outfit;
import pl.vsake.femboy.gen.Style;
import pl.vsake.femboy.gui.FemboyScreen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Automatyczne zrzuty ekranu do README – tylko dla autora moda.
 * Włącza się WYŁĄCZNIE z -Dfemboy.shots=&lt;folder&gt; (zadanie ./gradlew runShots); w zwykłej grze nic nie robi.
 * Po wejściu do świata ustawia po kolei różne skiny i stroje, robi zdjęcia z kamery z przodu/z tyłu
 * i z okna GUI, a na końcu zamyka grę. Nie dotyka klawiatury ani myszy.
 */
public final class ShotDriver {
	private ShotDriver() {
	}

	private record Step(int delay, Runnable action) {
	}

	private static final List<Step> STEPS = new ArrayList<>();
	private static Path out;
	private static int index, countdown = -1, inWorld;

	public static void initIfEnabled() {
		String dir = System.getProperty("femboy.shots");
		if (dir == null || dir.isBlank()) return;
		out = Path.of(dir);
		FemboyMod.LOG.info("femboy: tryb zrzutów → {}", out);
		plan();
		ClientTickEvents.END_CLIENT_TICK.register(ShotDriver::tick);
	}

	private static void plan() {
		STEPS.add(new Step(5, ShotDriver::setupWorld));
		if ("wheel".equals(System.getProperty("femboy.shots.only"))) {
			planWheel();
			STEPS.add(new Step(20, () -> MinecraftClient.getInstance().scheduleStop()));
			return;
		}
		if ("new".equals(System.getProperty("femboy.shots.only"))) {
			planNew();
			STEPS.add(new Step(20, () -> MinecraftClient.getInstance().scheduleStop()));
			return;
		}
		Object[][] looks = {
			{Style.CATBOY, Kit.CATBOY, "catboy"}, {Style.FEMBOY, Kit.FOX, "fox"}, {Style.EGIRL, Kit.DEVIL, "devil"},
			{Style.FEMBOY, Kit.ANGEL, "angel"}, {Style.CATBOY, Kit.FAIRY, "fairy"}, {Style.FEMBOY, Kit.BEAR, "bear"},
			{Style.EGIRL, Kit.BUNNY, "bunny"}, {Style.CATBOY, Kit.PUPPY, "puppy"}, {Style.FEMBOY, Kit.WOLF, "wolf"},
			{Style.FEMBOY, Kit.FEMBOY, "femboy"},
		};
		int n = 0;
		for (Object[] l : looks) {
			Style st = (Style) l[0];
			Kit k = (Kit) l[1];
			String name = (String) l[2];
			int pick = 3 + 7 * n++;
			STEPS.add(new Step(10, () -> dress(st, k, pick, Perspective.THIRD_PERSON_FRONT)));
			STEPS.add(new Step(30, () -> shot("front-" + name)));
		}
		for (Kit k : new Kit[]{Kit.ANGEL, Kit.DEVIL, Kit.FAIRY, Kit.FOX}) {
			STEPS.add(new Step(10, () -> dress(Style.CATBOY, k, 11, Perspective.THIRD_PERSON_BACK)));
			STEPS.add(new Step(30, () -> shot("back-" + k.id)));
		}
		// 1.6: dzwoneczki, szalik, identyfikator, efekt, emotka i reakcja na syk
		STEPS.add(new Step(10, () -> dress(Style.CATBOY, fx(Item.EARS_CAT_BELL, Item.TAIL_CAT, Item.NECK_SCARF, Item.HEAD_BOW_BELL), 5, Perspective.THIRD_PERSON_FRONT)));
		STEPS.add(new Step(30, () -> shot("fx-scarf-front")));
		STEPS.add(new Step(10, () -> dress(Style.CATBOY, fx(Item.EARS_CAT_BELL, Item.TAIL_CAT, Item.NECK_SCARF, Item.HEAD_BOW_BELL), 5, Perspective.THIRD_PERSON_BACK)));
		STEPS.add(new Step(30, () -> shot("fx-scarf-back")));
		STEPS.add(new Step(10, () -> {
			dress(Style.FEMBOY, fx(Item.EARS_CAT, Item.TAIL_FOX, Item.NECK_ID, Item.FX_HEARTS), 9, Perspective.THIRD_PERSON_FRONT);
			pl.vsake.femboy.fx.Reactions.emote(pl.vsake.femboy.fx.Emote.HEART);
		}));
		STEPS.add(new Step(30, () -> shot("fx-id-emote")));
		STEPS.add(new Step(10, () -> dress(Style.CATBOY, fx(Item.EARS_CAT, Item.TAIL_CAT, Item.NECK_BELLS, Item.FX_SPARKLES), 5, Perspective.THIRD_PERSON_FRONT)));
		STEPS.add(new Step(25, () -> pl.vsake.femboy.fx.Reactions.voice(MinecraftClient.getInstance().player.getUuid(), pl.vsake.femboy.voice.Voice.HISS)));
		STEPS.add(new Step(12, () -> shot("fx-hiss-front")));
		STEPS.add(new Step(5, () -> {
			dress(Style.CATBOY, fx(Item.EARS_CAT, Item.TAIL_CAT, Item.NECK_BELLS), 5, Perspective.THIRD_PERSON_BACK);
			pl.vsake.femboy.fx.Reactions.voice(MinecraftClient.getInstance().player.getUuid(), pl.vsake.femboy.voice.Voice.HISS);
		}));
		STEPS.add(new Step(14, () -> shot("fx-hiss-back")));
		planNew();
		STEPS.add(new Step(20, () -> MinecraftClient.getInstance().scheduleStop()));
	}

	/** 1.7: animacje ciała, mruganie, rzeczy we dwoje (z dostawionym drugim graczem), zabawki, ślady łapek, nowe zakładki. */
	private static void planNew() {
		Outfit cat = fx(Item.EARS_CAT, Item.TAIL_CAT, Item.NECK_ID, Item.PAWS, Item.TOY_MOUSE);
		STEPS.add(new Step(10, () -> {
			FemboyMod.CONFIG.pats = 142;
			dress(Style.CATBOY, cat, 5, Perspective.THIRD_PERSON_FRONT);
		}));
		STEPS.add(new Step(30, () -> shot("new-toy-mouse")));
		for (pl.vsake.femboy.fx.Pose p : pl.vsake.femboy.fx.Pose.values()) {
			STEPS.add(new Step(12, () -> pl.vsake.femboy.fx.Poses.play(p)));
			STEPS.add(new Step(p.held() ? 16 : 14, () -> shot("pose-" + p.id)));
			if (p == pl.vsake.femboy.fx.Pose.BELLY || p == pl.vsake.femboy.fx.Pose.LOAF || p == pl.vsake.femboy.fx.Pose.NAP) {
				// leżące pozy także z boku/z tyłu – widać, czy postać nie wisi nad ziemią
				STEPS.add(new Step(2, () -> MinecraftClient.getInstance().options.setPerspective(Perspective.THIRD_PERSON_BACK)));
				STEPS.add(new Step(8, () -> shot("pose-" + p.id + "-back")));
				STEPS.add(new Step(2, () -> MinecraftClient.getInstance().options.setPerspective(Perspective.THIRD_PERSON_FRONT)));
			}
		}
		STEPS.add(new Step(5, pl.vsake.femboy.fx.Poses::stopMine));
		STEPS.add(new Step(20, () -> pl.vsake.femboy.fx.Boop.show(MinecraftClient.getInstance().player)));
		STEPS.add(new Step(3, () -> shot("new-boop-blink")));
		// drugi gracz (tylko u mnie na ekranie) do rzeczy we dwoje
		STEPS.add(new Step(15, () -> buddy(1.0, 0.0, 0f)));
		STEPS.add(new Step(5, () -> pl.vsake.femboy.fx.Bonds.start(pl.vsake.femboy.fx.Bonds.HANDS, BUDDY, "Mruczek")));
		STEPS.add(new Step(20, () -> shot("pair-hands")));
		STEPS.add(new Step(5, () -> {
			pl.vsake.femboy.fx.Bonds.stop();
			buddy(0.0, 0.85, 180f);
			pl.vsake.femboy.fx.Bonds.remote(BUDDY, pl.vsake.femboy.fx.Bonds.HUG, MinecraftClient.getInstance().player.getUuid());
			MinecraftClient.getInstance().options.setPerspective(Perspective.THIRD_PERSON_BACK);
		}));
		STEPS.add(new Step(20, () -> shot("pair-hug")));
		for (char kind : new char[]{pl.vsake.femboy.fx.Bonds.CARRY, pl.vsake.femboy.fx.Bonds.PIGGY}) {
			STEPS.add(new Step(5, () -> {
				MinecraftClient mc = MinecraftClient.getInstance();
				mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
				pl.vsake.femboy.fx.Bonds.remote(BUDDY, (char) 0, null);
				buddy(2.0, 0.0, 0f);
				pl.vsake.femboy.fx.Bonds.start(kind, BUDDY, "Mruczek");
				pl.vsake.femboy.fx.Bonds.remoteOk(BUDDY, mc.player.getUuid());
			}));
			STEPS.add(new Step(20, () -> shot("pair-" + (kind == pl.vsake.femboy.fx.Bonds.CARRY ? "carry" : "piggy"))));
			STEPS.add(new Step(2, () -> MinecraftClient.getInstance().options.setPerspective(Perspective.THIRD_PERSON_BACK)));
			STEPS.add(new Step(8, () -> shot("pair-" + (kind == pl.vsake.femboy.fx.Bonds.CARRY ? "carry" : "piggy") + "-back")));
		}
		// zabawki: kłębek leci przed siebie, kropka lasera na ziemi (kot patrzy na nią), ślady łapek na piasku
		STEPS.add(new Step(5, () -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			pl.vsake.femboy.fx.Bonds.stop();
			buddy(1.5, 2.0, 180f);
			mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			IntegratedServer s = mc.getServer();
			if (s != null) s.execute(() -> s.getCommandManager().parseAndExecute(s.getCommandSource(),
				"execute as @a at @s run fill ~-4 ~-1 ~-14 ~4 ~-1 ~4 minecraft:sand"));
		}));
		STEPS.add(new Step(10, pl.vsake.femboy.fx.Toys::throwYarn));
		STEPS.add(new Step(6, () -> shot("toy-yarn-flying")));
		STEPS.add(new Step(40, () -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			net.minecraft.util.math.Vec3d f = net.minecraft.util.math.Vec3d.fromPolar(0f, mc.player.getYaw());
			pl.vsake.femboy.fx.Toys.remoteLaser(BUDDY, new double[]{mc.player.getX() + f.x * 2.5 + 0.8, mc.player.getY() + 0.03, mc.player.getZ() + f.z * 2.5});
		}));
		STEPS.add(new Step(6, () -> shot("toy-yarn-laser")));
		STEPS.add(new Step(5, () -> MinecraftClient.getInstance().options.forwardKey.setPressed(true)));
		STEPS.add(new Step(40, () -> MinecraftClient.getInstance().options.forwardKey.setPressed(false)));
		STEPS.add(new Step(10, () -> shot("new-paw-prints")));
		STEPS.add(new Step(5, () -> {
			pl.vsake.femboy.Wardrobe.save("Kotek", cat, null);
			pl.vsake.femboy.Wardrobe.save("Lisek", Kit.FOX.outfit(), null);
		}));
		planGui();
		planWheel();
	}

	private static final UUID BUDDY = UUID.fromString("0f3b0c1e-7d4a-4c57-9a55-6d1f2a3b4c5d");
	private static net.minecraft.client.network.OtherClientPlayerEntity buddy;

	/** Stawia (albo przestawia) drugiego gracza: right/forward bloków ode mnie, obrócony o turn stopni względem mnie. */
	private static void buddy(double right, double forward, float turn) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (buddy == null || buddy.isRemoved()) {
			buddy = new net.minecraft.client.network.OtherClientPlayerEntity(mc.world, new com.mojang.authlib.GameProfile(BUDDY, "Mruczek"));
			mc.world.addEntity(buddy);
			FemboyMod.CONFIG.playerOrCreate(BUDDY, "Mruczek").outfit = Kit.FOX.outfit();
			Accessories.changed();
		}
		float yaw = mc.player.getYaw();
		net.minecraft.util.math.Vec3d f = net.minecraft.util.math.Vec3d.fromPolar(0f, yaw), r = net.minecraft.util.math.Vec3d.fromPolar(0f, yaw + 90f);
		double x = mc.player.getX() + f.x * forward + r.x * right, z = mc.player.getZ() + f.z * forward + r.z * right;
		buddy.refreshPositionAndAngles(x, mc.player.getY(), z, yaw + turn, 0f);
		buddy.setHeadYaw(yaw + turn);
		buddy.setBodyYaw(yaw + turn);
		buddy.lastBodyYaw = yaw + turn;
		buddy.lastX = x;
		buddy.lastY = mc.player.getY();
		buddy.lastZ = z;
	}

	/** Kółko animacji: otwarte na stałe (bez klawisza), kursor na jednej z póz. */
	private static void planWheel() {
		// ślady łapek z bliska: przejście po piasku i śniegu, potem spojrzenie za siebie w dół
		STEPS.add(new Step(10, () -> {
			dress(Style.CATBOY, Kit.CATBOY, 5, Perspective.FIRST_PERSON);
			MinecraftClient mc = MinecraftClient.getInstance();
			IntegratedServer sv = mc.getServer();
			if (sv != null) sv.execute(() -> {
				sv.getCommandManager().parseAndExecute(sv.getCommandSource(), "execute as @a at @s run fill ~-3 ~-1 ~-12 ~3 ~-1 ~3 minecraft:sand");
				sv.getCommandManager().parseAndExecute(sv.getCommandSource(), "execute as @a at @s run fill ~-3 ~-1 ~-12 ~3 ~-1 ~-4 minecraft:snow_block");
			});
		}));
		STEPS.add(new Step(15, () -> MinecraftClient.getInstance().options.forwardKey.setPressed(true)));
		STEPS.add(new Step(26, () -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			mc.options.forwardKey.setPressed(false);
			mc.player.setYaw(mc.player.getYaw() + 180f);
			mc.player.setPitch(48f);
		}));
		STEPS.add(new Step(12, () -> shot("paw-close")));
		STEPS.add(new Step(4, () -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			mc.player.setYaw(mc.player.getYaw() + 180f);
			mc.player.setPitch(5f);
		}));
		STEPS.add(new Step(10, () -> {
			dress(Style.CATBOY, Kit.CATBOY, 5, Perspective.THIRD_PERSON_FRONT);
			MinecraftClient mc = MinecraftClient.getInstance();
			mc.getToastManager().clear();
			mc.setScreen(new pl.vsake.femboy.gui.PoseWheel(null));
			net.minecraft.client.util.Window w = mc.getWindow();
			net.minecraft.client.util.InputUtil.setCursorParameters(w, org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL, w.getWidth() * 0.66, w.getHeight() * 0.3);
		}));
		STEPS.add(new Step(30, () -> shot("gui-wheel")));
		STEPS.add(new Step(5, () -> {
			MinecraftClient.getInstance().setScreen(null);
			pl.vsake.femboy.fx.Trust.setText(pl.vsake.femboy.fx.Trust.What.CLICKER, "Mruczek\nLisek_PL");
			FemboyScreen.open(5);
		}));
		STEPS.add(new Step(30, () -> shot("gui-nicks")));
		STEPS.add(new Step(5, () -> MinecraftClient.getInstance().setScreen(null)));
	}

	private static void planGui() {
		// okno GUI na każdej zakładce
		for (int tab = 0; tab < 10; tab++) {
			int t = tab;
			STEPS.add(new Step(10, () -> {
				dress(Style.CATBOY, Kit.CATBOY, 5, Perspective.THIRD_PERSON_FRONT);
				MinecraftClient.getInstance().options.hudHidden = false;
				MinecraftClient.getInstance().getToastManager().clear(); // bez dymków w rogu zrzutu GUI
				FemboyScreen.open(t);
			}));
			STEPS.add(new Step(30, () -> shot("gui-" + t)));
		}
	}

	private static void tick(MinecraftClient mc) {
		if (mc.player == null || mc.world == null) return;
		// najpierw daj światu się doczytać
		if (inWorld < 120) {
			inWorld++;
			return;
		}
		if (index >= STEPS.size()) return;
		if (countdown < 0) countdown = STEPS.get(index).delay;
		if (countdown-- > 0) return;
		try {
			STEPS.get(index).action.run();
		} catch (Throwable t) {
			FemboyMod.LOG.error("femboy: krok zrzutów {} nie wyszedł", index, t);
		}
		index++;
		countdown = -1;
	}

	private static void setupWorld() {
		MinecraftClient mc = MinecraftClient.getInstance();
		mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialStep.NONE; // bez dymka „Move with WASD”
		mc.options.pauseOnLostFocus = false; // okno w tle (ktoś pracuje obok) nie może wyskoczyć z menu pauzy na zrzutach
		IntegratedServer s = mc.getServer();
		if (s != null) s.execute(() -> {
			for (String cmd : new String[]{"time set noon", "weather clear", "gamerule doDaylightCycle false",
				"gamerule doWeatherCycle false", "gamemode creative @a", "effect give @a minecraft:night_vision infinite 0 true",
				// zapisany świat mógł zostawić gracza w jaskini – na powierzchnię, z dala od ścian (kamera 3. osoby)
				"execute as @a at @s positioned over motion_blocking_no_leaves run tp @s ~ ~ ~ 180 5"})
				s.getCommandManager().parseAndExecute(s.getCommandSource(), cmd);
		});
	}

	private static Outfit fx(Item... items) {
		Outfit o = new Outfit();
		for (Item it : items) o.set(it.slot, it);
		return o;
	}

	private static void dress(Style st, Kit kit, int pick, Perspective view) {
		dress(st, kit.outfit(), pick, view);
	}

	/** Ubierz siebie: konkretny skin z puli stylu + strój; ustaw kamerę. */
	private static void dress(Style st, Outfit outfit, int pick, Perspective view) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.currentScreen != null) mc.setScreen(null);
		FemboyConfig cfg = FemboyMod.CONFIG;
		cfg.enabled = true;
		cfg.self = true;
		cfg.acc = true;
		UUID me = mc.player.getUuid();
		var opts = SkinManager.options(st);
		if (!opts.isEmpty()) cfg.playerOrCreate(me, mc.player.getGameProfile().name()).skin = opts.get(pick % opts.size()).key();
		cfg.accMe = outfit;
		SkinManager.invalidate(me);
		Accessories.changed();
		mc.options.hudHidden = true;
		mc.options.setPerspective(view);
		mc.player.setPitch(view == Perspective.THIRD_PERSON_BACK ? 10f : 5f);
		// tułów w tę samą stronę co głowa – inaczej postać stoi bokiem i dalsze skrzydło/ucho chowa się za ciałem
		float yaw = mc.player.getYaw();
		mc.player.setHeadYaw(yaw);
		mc.player.setBodyYaw(yaw);
		mc.player.lastBodyYaw = yaw;
	}

	private static void shot(String name) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ScreenshotRecorder.takeScreenshot(mc.getFramebuffer(), img -> {
			try (img) {
				Files.createDirectories(out);
				Path f = out.resolve(name + ".png");
				img.writeTo(f);
				FemboyMod.LOG.info("femboy: zrzut {}", f);
			} catch (Exception e) {
				FemboyMod.LOG.error("femboy: nie zapisałem zrzutu {}", name, e);
			}
		});
	}
}
