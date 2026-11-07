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
import pl.vsake.femboy.acc.Kit;
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
		// okno GUI na każdej zakładce
		for (int tab = 0; tab < 4; tab++) {
			int t = tab;
			STEPS.add(new Step(10, () -> {
				dress(Style.CATBOY, Kit.CATBOY, 5, Perspective.THIRD_PERSON_FRONT);
				MinecraftClient.getInstance().options.hudHidden = false;
				MinecraftClient.getInstance().getToastManager().clear(); // bez dymków w rogu zrzutu GUI
				FemboyScreen.open(t);
			}));
			STEPS.add(new Step(30, () -> shot("gui-" + t)));
		}
		STEPS.add(new Step(20, () -> MinecraftClient.getInstance().scheduleStop()));
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
		IntegratedServer s = mc.getServer();
		if (s != null) s.execute(() -> {
			for (String cmd : new String[]{"time set noon", "weather clear", "gamerule doDaylightCycle false",
				"gamerule doWeatherCycle false", "gamemode creative @a", "effect give @a minecraft:night_vision infinite 0 true",
				// zapisany świat mógł zostawić gracza w jaskini – na powierzchnię, z dala od ścian (kamera 3. osoby)
				"execute as @a at @s positioned over motion_blocking_no_leaves run tp @s ~ ~ ~ 180 5"})
				s.getCommandManager().parseAndExecute(s.getCommandSource(), cmd);
		});
	}

	/** Ubierz siebie: konkretny skin z puli stylu + gotowy zestaw; ustaw kamerę. */
	private static void dress(Style st, Kit kit, int pick, Perspective view) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.currentScreen != null) mc.setScreen(null);
		FemboyConfig cfg = FemboyMod.CONFIG;
		cfg.enabled = true;
		cfg.self = true;
		cfg.acc = true;
		UUID me = mc.player.getUuid();
		var opts = SkinManager.options(st);
		if (!opts.isEmpty()) cfg.playerOrCreate(me, mc.player.getGameProfile().name()).skin = opts.get(pick % opts.size()).key();
		cfg.accMe = kit.outfit();
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
