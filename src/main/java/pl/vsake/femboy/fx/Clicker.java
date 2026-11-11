package pl.vsake.femboy.fx;

import static pl.vsake.femboy.Lang.tr;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import org.lwjgl.glfw.GLFW;
import pl.vsake.femboy.FemboyMod;
import pl.vsake.femboy.Keys;
import pl.vsake.femboy.net.NetClient;
import pl.vsake.femboy.net.Signal;
import pl.vsake.femboy.voice.Voices;

import java.util.UUID;

/**
 * Clicker (jak do tresury): klawisz → „klik-klak” (nagranie prawdziwego clickera, sounds/fx/clicker.ogg). Kto ma clicker WŁĄCZONY u siebie (domyślnie wyłączony, przełącznik
 * w oknie moda) i usłyszy czyjś klik, sam obraca się w stronę klikającego. Z wyłączonym nic nie słychać i nic się nie dzieje.
 */
public final class Clicker {
	private Clicker() {
	}

	private static final SoundEvent SOUND = SoundEvent.of(Identifier.of(FemboyMod.ID, "clicker"));
	private static final Random RANDOM = Random.create();
	private static final java.util.Random NONCE = new java.util.Random();
	private static final long COOLDOWN_MS = 450;
	/** Z jak daleka słychać clicker (i z jak daleka obraca). */
	public static final double RANGE = 24;
	private static final int TURN_TICKS = 16;

	private static KeyBinding key;
	private static long lastMs;
	private static Entity turnTo;
	private static int turnLeft;

	public static void init() {
		key = Keys.register("Zabawa", "Clicker", "clicker", GLFW.GLFW_KEY_V);
		ClientTickEvents.END_CLIENT_TICK.register(Clicker::tick);
	}

	public static KeyBinding key() {
		return key;
	}

	private static void tick(MinecraftClient mc) {
		while (key.wasPressed()) if (mc.player != null && mc.currentScreen == null) use();
		if (mc.player == null || mc.world == null) {
			turnTo = null;
			return;
		}
		if (turnTo != null) {
			if (turnLeft-- <= 0 || turnTo.isRemoved() || !FemboyMod.CONFIG.clicker) {
				turnTo = null;
				return;
			}
			double dx = turnTo.getX() - mc.player.getX(), dz = turnTo.getZ() - mc.player.getZ();
			double dy = turnTo.getEyeY() - mc.player.getEyeY();
			float wantYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
			float wantPitch = (float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180.0 / Math.PI);
			float dYaw = MathHelper.wrapDegrees(wantYaw - mc.player.getYaw()), dPitch = wantPitch - mc.player.getPitch();
			if (Math.abs(dYaw) < 1.5f && Math.abs(dPitch) < 1.5f) {
				turnTo = null;
				return;
			}
			// płynnie, nie skokiem – wygląda jak odwrócenie głowy na dźwięk
			mc.player.setYaw(mc.player.getYaw() + dYaw * 0.32f);
			mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + dPitch * 0.32f, -90f, 90f));
		}
	}

	/** Klawisz / przycisk / komenda. false = wyłączony albo za szybko. */
	public static boolean use() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) return false;
		if (!FemboyMod.CONFIG.clicker) {
			mc.player.sendMessage(Text.literal(tr("Clicker jest wyłączony – włącz go w oknie moda (zakładka Zabawa)")).formatted(Formatting.GRAY), true);
			return false;
		}
		long now = Util.getMeasuringTimeMs();
		if (now - lastMs < COOLDOWN_MS) return false;
		lastMs = now;
		int nonce = NONCE.nextInt();
		Voices.fresh(mc.player.getUuid(), nonce);
		click(mc, mc.player);
		NetClient.sendSignal(Signal.click(nonce));
		return true;
	}

	/** Klik innego gracza (już bez duplikatów). */
	public static void heard(UUID who) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!FemboyMod.CONFIG.clicker || mc.world == null || mc.player == null || Voices.muted(who)) return;
		PlayerEntity p = mc.world.getPlayerByUuid(who);
		if (p == null || p == mc.player || p.squaredDistanceTo(mc.player) > RANGE * RANGE) return;
		// „tylko wybrane osoby”: cudzego clickera wtedy ani nie słyszę, ani się do niego nie obracam
		if (!Trust.allows(Trust.What.CLICKER, who)) return;
		click(mc, p);
		turnTo = p;
		turnLeft = TURN_TICKS;
	}

	/** Nagranie ma już oba dźwięki („klik” i „klak”) – gramy je raz, bez zmiany wysokości. */
	private static void click(MinecraftClient mc, Entity at) {
		mc.getSoundManager().play(new PositionedSoundInstance(SOUND, SoundCategory.PLAYERS, 1f, 1f, RANDOM,
			at.getX(), at.getY() + at.getHeight() * 0.7, at.getZ()));
	}
}
