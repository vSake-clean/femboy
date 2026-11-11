package pl.vsake.femboy.fx;

import static pl.vsake.femboy.Lang.tr;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import pl.vsake.femboy.FemboyMod;
import pl.vsake.femboy.Keys;
import pl.vsake.femboy.net.NetClient;
import pl.vsake.femboy.net.Signal;
import pl.vsake.femboy.voice.Voices;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Animacje ciała: kto jaką robi i od kiedy. Moje idą spod klawiszy / z okna moda / z komendy i są wysyłane innym
 * (trzymane pozy odświeżam co 2 s, ruch je kończy). Dwie włączają się same i każdy liczy je u siebie, bez sieci:
 * bochenek (kucanie bez ruchu przez kilka sekund) i przeciąganie (po wstaniu z łóżka albo po długim staniu w miejscu).
 */
public final class Poses {
	private Poses() {
	}

	private static final class Active {
		Pose pose;
		long startMs, seenMs;
		/** 0 = trwa; inaczej chwila, od której gaśnie. */
		long endMs;
		double x, y, z;
		/** Włączona sama (bochenek / przeciąganie) – nie nadpisuje tego, co gracz wybrał. */
		boolean auto;
	}

	/** Co widać u gracza, gdy stoi w miejscu: do samoczynnych póz. */
	private static final class Idle {
		double x, y, z;
		float yaw;
		long stillSinceMs, sneakSinceMs;
		boolean sleeping;
	}

	private static final long RESEND_MS = 2000, TIMEOUT_MS = 6500, FADE_MS = 320, COOLDOWN_MS = 350;
	private static final long LOAF_AFTER_MS = 6000, STRETCH_AFTER_MS = 90_000;

	private static final Map<UUID, Active> ACTIVE = new HashMap<>();
	private static final Map<UUID, Idle> IDLE = new HashMap<>();
	private static final Map<Pose, KeyBinding> KEYS = new EnumMap<>(Pose.class);
	private static final Random RANDOM = new Random();
	private static long sentMs, lastMs;
	private static int ticks;

	public static void init() {
		for (Pose p : Pose.values()) {
			int def = switch (p) {
				case SIT -> GLFW.GLFW_KEY_KP_5;
				case KNEAD -> GLFW.GLFW_KEY_KP_6;
				case BELLY -> GLFW.GLFW_KEY_KP_7;
				case WAVE -> GLFW.GLFW_KEY_KP_8;
				case BEG -> GLFW.GLFW_KEY_KP_9;
				default -> GLFW.GLFW_KEY_UNKNOWN;
			};
			KEYS.put(p, Keys.register("Animacje", p.label, "pose." + p.id, def));
		}
		wheelKey = Keys.register("Animacje", "Kółko animacji (przytrzymaj)", "wheel", GLFW.GLFW_KEY_Y);
		ClientTickEvents.END_CLIENT_TICK.register(Poses::tick);
	}

	private static KeyBinding wheelKey;

	public static KeyBinding wheelKey() {
		return wheelKey;
	}

	public static KeyBinding key(Pose p) {
		return KEYS.get(p);
	}

	private static void tick(MinecraftClient mc) {
		while (wheelKey.wasPressed())
			if (mc.player != null && mc.currentScreen == null) mc.setScreen(new pl.vsake.femboy.gui.PoseWheel(wheelKey));
		for (Map.Entry<Pose, KeyBinding> en : KEYS.entrySet())
			while (en.getValue().wasPressed()) if (mc.player != null && mc.currentScreen == null) play(en.getKey());
		if (mc.player == null || mc.world == null) return;
		long now = Util.getMeasuringTimeMs();
		UUID me = mc.player.getUuid();
		if (stretchAtMs != 0 && now >= stretchAtMs) {
			stretchAtMs = 0;
			Active cur = ACTIVE.get(me);
			if (cur == null || cur.endMs != 0) { // nie zacząłem w tym czasie nic innego
				lastMs = 0;
				play(Pose.STRETCH);
			}
		}
		Active mine = ACTIVE.get(me);
		if (mine != null && mine.endMs == 0 && !mine.auto) {
			// ruszyłem się, dostałem obrażenia albo wsiadłem na coś → koniec trzymanej pozy
			boolean moved = mc.player.squaredDistanceTo(mine.x, mine.y, mine.z) > 0.04 || mc.player.hurtTime > 0 || mc.player.hasVehicle()
				|| mc.player.isSleeping();
			if (mine.pose.held() && moved) stopMine();
			else if (mine.pose.held() && now - sentMs >= RESEND_MS) {
				sentMs = now;
				NetClient.sendSignal(Signal.pose(mine.pose, RANDOM.nextInt()));
			}
		}
		if (++ticks % 2 == 0) watch(mc, now);
		if (ticks % 20 == 0) {
			ACTIVE.values().removeIf(a -> a.endMs != 0 ? now - a.endMs > FADE_MS
				: a.pose.held() ? !a.auto && now - a.seenMs > TIMEOUT_MS : now - a.startMs > a.pose.durMs);
			if (ticks % 200 == 0) {
				Set<UUID> here = new HashSet<>();
				for (AbstractClientPlayerEntity p : mc.world.getPlayers()) here.add(p.getUuid());
				IDLE.keySet().retainAll(here);
				ACTIVE.keySet().retainAll(here);
			}
		}
	}

	/** Samoczynne pozy i kończenie cudzych, gdy widać, że gracz już poszedł (zgubiony sygnał „koniec”). */
	private static void watch(MinecraftClient mc, long now) {
		boolean auto = FemboyMod.CONFIG.autoPoses;
		for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
			UUID id = p.getUuid();
			Idle i = IDLE.get(id);
			if (i == null) {
				i = new Idle();
				i.stillSinceMs = now;
				IDLE.put(id, i);
			}
			boolean moved = p.squaredDistanceTo(i.x, i.y, i.z) > 0.0004;
			boolean turned = Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(p.getYaw() - i.yaw)) > 2f;
			long still = now - i.stillSinceMs;
			Active a = ACTIVE.get(id);
			if (a != null && a.endMs == 0 && a.pose.held() && (a.auto ? moved || !p.isSneaking() : p.squaredDistanceTo(a.x, a.y, a.z) > 0.25))
				a.endMs = now;
			if (auto && !p.isSpectator() && !p.hasVehicle()) {
				// przeciąganie: po wstaniu z łóżka albo gdy ruszam po długim staniu
				if (i.sleeping && !p.isSleeping() || moved && still > STRETCH_AFTER_MS && !p.isSleeping()) start(id, p, Pose.STRETCH, now, true);
				// bochenek: kucam i nie ruszam się od kilku sekund
				if (p.isSneaking() && !moved) {
					if (i.sneakSinceMs == 0) i.sneakSinceMs = now;
					else if (now - i.sneakSinceMs > LOAF_AFTER_MS && (a == null || a.endMs != 0 && now - a.endMs > FADE_MS)) start(id, p, Pose.LOAF, now, true);
				} else i.sneakSinceMs = 0;
			}
			i.sleeping = p.isSleeping();
			if (moved || turned) {
				i.x = p.getX();
				i.y = p.getY();
				i.z = p.getZ();
				i.yaw = p.getYaw();
				i.stillSinceMs = now;
			}
		}
	}

	private static void start(UUID id, AbstractClientPlayerEntity p, Pose pose, long now, boolean auto) {
		Active a = ACTIVE.get(id);
		if (a != null && a.endMs == 0) {
			if (auto) return; // gracz coś właśnie robi – samoczynna poza nie przerywa
			if (a.pose == pose && !a.auto) { // odświeżenie trzymanej pozy
				a.seenMs = now;
				return;
			}
		}
		if (a == null) ACTIVE.put(id, a = new Active());
		a.pose = pose;
		a.startMs = a.seenMs = now;
		a.endMs = 0;
		a.auto = auto;
		a.x = p.getX();
		a.y = p.getY();
		a.z = p.getZ();
	}

	/** Klawisz / przycisk / komenda: zaczynam animację (trzymaną drugi raz = kończę). false = za szybko albo nie da się teraz. */
	public static boolean play(Pose pose) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || pose == null) return false;
		long now = Util.getMeasuringTimeMs();
		if (now - lastMs < COOLDOWN_MS) return false;
		lastMs = now;
		UUID me = mc.player.getUuid();
		Active a = ACTIVE.get(me);
		if (a != null && a.endMs == 0 && a.pose == pose && pose.held()) {
			stopMine();
			return true;
		}
		if (mc.player.hasVehicle() || mc.player.isSleeping() || mc.player.isSpectator()) return false;
		stretchAtMs = 0;
		if (a != null) a.endMs = now; // żeby start() nie uznał tego za odświeżenie
		start(me, mc.player, pose, now, false);
		sentMs = now;
		int nonce = RANDOM.nextInt();
		Voices.fresh(me, nonce);
		NetClient.sendSignal(Signal.pose(pose, nonce));
		// w pierwszej osobie siebie nie widać – krótka informacja nad paskiem
		mc.player.sendMessage(Text.literal(tr(pose.label)).formatted(Formatting.LIGHT_PURPLE)
			.append(Text.literal(pose.held() ? tr(" (ruch albo ten sam klawisz kończy)") : "").formatted(Formatting.GRAY)), true);
		return true;
	}

	/** Kończę swoją pozę (ruch, klawisz, „Stop” w oknie moda). */
	public static void stopMine() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) return;
		Active a = ACTIVE.get(mc.player.getUuid());
		if (a == null || a.endMs != 0) return;
		a.endMs = Util.getMeasuringTimeMs();
		if (!a.auto) NetClient.sendSignal(Signal.pose(null, RANDOM.nextInt()));
		// wstaję z ziemi (siad, drzemka, brzuszek…) → za chwilę się przeciągam
		if (a.pose.onGround() && FemboyMod.CONFIG.autoPoses && a.endMs - a.startMs > GOT_UP_MIN_MS) stretchAtMs = a.endMs + FADE_MS + 40;
	}

	private static final long GOT_UP_MIN_MS = 1500;
	private static long stretchAtMs;

	/** Moja trwająca poza albo null. */
	public static Pose mine() {
		MinecraftClient mc = MinecraftClient.getInstance();
		Active a = mc.player == null ? null : ACTIVE.get(mc.player.getUuid());
		return a != null && a.endMs == 0 && !a.auto ? a.pose : null;
	}

	/** Poza innego gracza z sieci (null = skończył). Trzymane przychodzą co 2 s – to samo kilka razy nie szkodzi. */
	public static void remote(UUID who, Pose pose, int nonce) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null) return;
		long now = Util.getMeasuringTimeMs();
		if (pose == null) {
			Active a = ACTIVE.get(who);
			if (a != null && a.endMs == 0 && !a.auto) a.endMs = now;
			return;
		}
		if (!pose.held() && !Voices.fresh(who, nonce)) return;
		if (mc.world.getPlayerByUuid(who) instanceof AbstractClientPlayerEntity p) start(who, p, pose, now, false);
	}

	/** Czy gracz siedzi / leży w pozie (ogon kładzie się wtedy jak przy siedzeniu). */
	public static boolean grounded(Reactions.State s) {
		return s.pose != null && s.pose.onGround() && s.poseW > 0.5f;
	}

	/** Co klatkę dla rysowanego gracza. */
	public static void fill(AbstractClientPlayerEntity player, Reactions.State s) {
		Active a = ACTIVE.get(player.getUuid());
		if (a == null) return;
		long now = Util.getMeasuringTimeMs();
		float t = (now - a.startMs) / 1000f;
		boolean endless = a.endMs == 0 && a.pose.held();
		float left = endless ? -1f : a.endMs != 0 ? (FADE_MS - (now - a.endMs)) / 1000f : (a.pose.durMs - (now - a.startMs)) / 1000f;
		if (!endless && left <= 0f) return;
		s.pose = a.pose;
		s.poseT = t;
		s.poseW = PoseMath.weight(t, left);
		if (a.pose == Pose.WIGGLE) s.hunt = Math.max(s.hunt, s.poseW);
		// drzemka: „Zzz” nad głową, jeśli nie ma innej emotki
		if (a.pose == Pose.NAP && s.emote == null && s.poseW > 0.9f) {
			s.emote = Emote.SLEEP;
			s.emoteT = 0.2f + 0.6f * ((t * 0.25f) % 1f);
		}
	}

	/** Po rozłączeniu. */
	public static void clear() {
		ACTIVE.clear();
		IDLE.clear();
	}
}
