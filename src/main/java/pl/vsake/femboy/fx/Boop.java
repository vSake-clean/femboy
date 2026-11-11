package pl.vsake.femboy.fx;

import static pl.vsake.femboy.Lang.tr;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.lwjgl.glfw.GLFW;
import pl.vsake.femboy.FemboyMod;
import pl.vsake.femboy.Keys;
import pl.vsake.femboy.acc.Look;
import pl.vsake.femboy.net.NetClient;
import pl.vsake.femboy.net.Signal;
import pl.vsake.femboy.pat.PatPat;
import pl.vsake.femboy.voice.Voices;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Boop w nosek: celuję w gracza i krótko naciskam klawisz → on mruga i odskakuje głową, słychać „boop”, przy nosie
 * błyska iskierka. Widzą to wszyscy z modem. Tu też zwykłe mruganie co kilka sekund (powieki rysuje AccessoryFeature).
 */
public final class Boop {
	private Boop() {
	}

	private static final SoundEvent SOUND = SoundEvent.of(Identifier.of(FemboyMod.ID, "boop"));
	private static final Random RANDOM = Random.create();
	private static final java.util.Random NONCE = new java.util.Random();
	private static final long COOLDOWN_MS = 400, BOOP_MS = 480;
	private static final double RANGE = 6;

	private static final Map<UUID, Long> BOOPED = new HashMap<>();
	private static KeyBinding key;
	private static long lastMs;

	public static void init() {
		key = Keys.register("We dwoje", "Boop w nosek (celuj w gracza)", "boop", GLFW.GLFW_KEY_N);
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (key.wasPressed()) if (mc.player != null && mc.currentScreen == null) aimed(mc);
		});
	}

	private static void aimed(MinecraftClient mc) {
		LivingEntity e = PatPat.aim(mc);
		if (e instanceof PlayerEntity p) use(p.getUuid());
		else mc.player.sendMessage(Text.literal(tr("Wyceluj w gracza, żeby zrobić boop")).formatted(Formatting.GRAY), true);
	}

	/** Klawisz / przycisk / komenda. false = za szybko albo gracza nie ma obok. */
	public static boolean use(UUID target) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null || target.equals(mc.player.getUuid())) return false;
		PlayerEntity p = mc.world.getPlayerByUuid(target);
		if (p == null || p.squaredDistanceTo(mc.player) > RANGE * RANGE) return false;
		long now = Util.getMeasuringTimeMs();
		if (now - lastMs < COOLDOWN_MS) return false;
		lastMs = now;
		int nonce = NONCE.nextInt();
		Voices.fresh(mc.player.getUuid(), nonce);
		effect(mc, p);
		NetClient.sendSignal(Signal.boop(target, nonce));
		return true;
	}

	/** Boop od innego gracza (już sprawdzony format). */
	public static void remote(UUID who, UUID target, int nonce) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null || target == null || !Voices.fresh(who, nonce)) return;
		PlayerEntity from = mc.world.getPlayerByUuid(who), to = mc.world.getPlayerByUuid(target);
		// boop tylko z bliska – nikt nie „boopnie” z drugiego końca mapy
		if (from == null || to == null || from == to || from.squaredDistanceTo(to) > RANGE * RANGE) return;
		effect(mc, to);
		if (to == mc.player) mc.player.sendMessage(Text.literal(from.getGameProfile().name()).formatted(Formatting.LIGHT_PURPLE)
			.append(Text.literal(tr(" robi ci boop w nosek")).formatted(Formatting.WHITE)), true);
	}

	/** Sam efekt u mnie na ekranie, bez wysyłania (zrzuty do README). */
	public static void show(PlayerEntity p) {
		if (p != null) effect(MinecraftClient.getInstance(), p);
	}

	private static void effect(MinecraftClient mc, PlayerEntity p) {
		BOOPED.put(p.getUuid(), Util.getMeasuringTimeMs());
		Reactions.perk(p.getUuid(), 1100);
		Vec3d look = Vec3d.fromPolar(0f, p.getHeadYaw());
		double x = p.getX() + look.x * 0.32, y = p.getEyeY() - 0.08, z = p.getZ() + look.z * 0.32;
		float vol = 0.7f * (Voices.muted(p.getUuid()) ? 0f : 1f);
		if (vol > 0f) mc.getSoundManager().play(new PositionedSoundInstance(SOUND, SoundCategory.PLAYERS, vol, 0.95f + RANDOM.nextFloat() * 0.2f, RANDOM, x, y, z));
		boolean firstPerson = p == mc.player && mc.options.getPerspective().isFirstPerson();
		if (!firstPerson)
			for (int i = 0; i < 4; i++)
				mc.world.addParticleClient(ParticleTypes.WAX_OFF, x + look.x * 0.05, y, z + look.z * 0.05,
					(RANDOM.nextDouble() - 0.5) * 1.6 + look.x, RANDOM.nextDouble() * 1.2, (RANDOM.nextDouble() - 0.5) * 1.6 + look.z);
	}

	/** Co klatkę: odskok głowy po boopie i powieki (boop, drzemka, zwykłe mruganie). */
	public static void fill(AbstractClientPlayerEntity player, Reactions.State s, Look look) {
		UUID id = player.getUuid();
		long now = Util.getMeasuringTimeMs();
		Long at = BOOPED.get(id);
		if (at != null) {
			long t = now - at;
			if (t >= BOOP_MS) BOOPED.remove(id);
			else {
				s.boop = t < 80 ? t / 80f : Math.max(0f, 1f - (t - 80) / (float) (BOOP_MS - 80));
				s.blink = t < 40 ? t / 40f : t < 330 ? 1f : Math.max(0f, 1f - (t - 330) / 120f);
			}
		}
		if (!FemboyMod.CONFIG.blink) return;
		if (s.pose == Pose.NAP) s.blink = Math.max(s.blink, PoseMath.ease(s.poseW * 1.5f));
		s.blink = Math.max(s.blink, idleBlink(now, id.getLeastSignificantBits()));
	}

	/** Mrugnięcie co 3–6 s (każdy we własnym rytmie), 150 ms. @return 0..1 */
	public static float idleBlink(long ms, long seed) {
		long period = 3200 + Math.floorMod(seed, 7) * 450L;
		long local = Math.floorMod(ms + Math.floorMod(seed >>> 8, 5000), period);
		return local < 150 ? (float) Math.sin(local / 150.0 * Math.PI) : 0f;
	}

	/** Po rozłączeniu. */
	public static void clear() {
		BOOPED.clear();
	}
}
