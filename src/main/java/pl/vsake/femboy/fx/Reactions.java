package pl.vsake.femboy.fx;

import static pl.vsake.femboy.Lang.tr;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import pl.vsake.femboy.FemboyMod;
import pl.vsake.femboy.Keys;
import pl.vsake.femboy.net.NetClient;
import pl.vsake.femboy.net.Signal;
import pl.vsake.femboy.voice.Voice;
import pl.vsake.femboy.voice.Voices;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.option.KeyBinding;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Reakcje postaci: uszy i ogon odpowiadają na odgłosy i obrażenia (syk/warczenie/cios → uszy po sobie i nastroszony ogon,
 * miau → uszy postawione, hau → szybkie merdanie), a nad głową pokazują się emotki spod klawiszy.
 * Stan trzymany na gracza i przepisywany co klatkę do render state (Anim czyta go przez AccessoryModel).
 */
public final class Reactions {
	private Reactions() {
	}

	/** Dokładane do PlayerEntityRenderState – wypełniane co klatkę przez fill(). */
	public static final class State {
		public float flat, perk, wag, puff;
		/** Emotka nad głową albo null; emoteT = ile już trwa (0..1). */
		public Emote emote;
		public float emoteT;
		/** Nick na identyfikatorze przy obroży. */
		public String name;
		/** Licznik głaskań do identyfikatora; -1 = nieznany (gracz bez moda). */
		public long pats = -1;
		/** Animacja ciała (fx/Poses): która, jak mocno (0..1) i od ilu sekund. */
		public Pose pose;
		public float poseW, poseT;
		/** Poza we dwoje (PoseMath.PAIR_…), jej siła i – przy trzymaniu się za ręce – kierunek do dłoni partnera. */
		public int pair;
		public float pairW, aimX, aimY, aimZ;
		public boolean aimRight;
		/** Powieki 0..1 (1 = zamknięte) i odskok głowy po boopie 0..1. */
		public float blink, boop;
		/** Ogon drga (skradanie = polowanie). */
		public float hunt;
		/** Wiek z klatki – do animacji póz we dwoje. */
		public float age;

		public void reset() {
			flat = perk = wag = puff = 0f;
			emote = null;
			pose = null;
			poseW = poseT = 0f;
			pair = 0;
			pairW = 0f;
			blink = boop = hunt = 0f;
			pats = -1;
		}
	}

	private enum Kind {FLAT, ANGRY, PERK, WAG}

	private static final class Active {
		Kind kind;
		long startMs;
		int durMs;
	}

	private record Shown(Emote emote, long startMs) {
	}

	private static final int ATTACK_MS = 120, RELEASE_MS = 450;
	public static final int EMOTE_MS = 3200;
	private static final long EMOTE_COOLDOWN_MS = 500;

	private static final Map<UUID, Active> ACTIVE = new HashMap<>();
	private static final Map<UUID, Shown> EMOTES = new HashMap<>();
	private static final Map<Emote, KeyBinding> KEYS = new EnumMap<>(Emote.class);
	private static final Random RANDOM = new Random();
	private static long myLastEmoteMs;

	public static void init() {
		for (Emote e : Emote.values()) {
			int def = switch (e) {
				case HEART -> GLFW.GLFW_KEY_KP_1;
				case QUESTION -> GLFW.GLFW_KEY_KP_2;
				case EXCLAIM -> GLFW.GLFW_KEY_KP_3;
				case ANGER -> GLFW.GLFW_KEY_KP_4;
				default -> GLFW.GLFW_KEY_UNKNOWN;
			};
			KEYS.put(e, Keys.register("Emotki", e.symbol + " " + e.label, "emote." + e.id, def));
		}
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			for (Map.Entry<Emote, KeyBinding> en : KEYS.entrySet())
				while (en.getValue().wasPressed()) if (mc.player != null && mc.currentScreen == null) emote(en.getKey());
		});
	}

	// ======================== uszy i ogon ========================

	/** Ktoś wydał odgłos (ja albo inny gracz) – jego uszy/ogon reagują. */
	public static void voice(UUID who, Voice v) {
		switch (v) {
			case HISS, GROWL -> trigger(who, Kind.ANGRY, 2400);
			case WHINE -> trigger(who, Kind.FLAT, 2200);
			case MEOW, FOX, SQUEAK -> trigger(who, Kind.PERK, 1600);
			case BARK, PANT -> trigger(who, Kind.WAG, 2200);
			default -> {
			}
		}
	}

	/** Uszy stają na chwilę (boop, coś ciekawego). */
	public static void perk(UUID who, int durMs) {
		trigger(who, Kind.PERK, durMs);
	}

	private static void trigger(UUID who, Kind kind, int durMs) {
		long now = Util.getMeasuringTimeMs();
		Active a = ACTIVE.computeIfAbsent(who, k -> new Active());
		boolean running = a.kind == kind && now - a.startMs < a.durMs;
		a.kind = kind;
		a.durMs = durMs;
		// ta sama reakcja jeszcze trwa → przedłużamy bez ponownego „wejścia” (uszy nie podskakują co klatkę przy obrażeniach)
		a.startMs = running ? Math.max(a.startMs, now - ATTACK_MS) : now;
	}

	/** Co klatkę dla każdego rysowanego gracza. */
	public static void fill(AbstractClientPlayerEntity player, State s) {
		s.reset();
		s.name = player.getGameProfile().name();
		UUID id = player.getUuid();
		long now = Util.getMeasuringTimeMs();
		if (FemboyMod.CONFIG.react) {
			if (player.hurtTime > 0) trigger(id, Kind.FLAT, 900);
			Active a = ACTIVE.get(id);
			if (a != null) {
				long t = now - a.startMs;
				if (t >= a.durMs) ACTIVE.remove(id);
				else {
					float e = Math.min(1f, t / (float) ATTACK_MS) * Math.min(1f, (a.durMs - t) / (float) RELEASE_MS);
					e = e * e * (3f - 2f * e);
					switch (a.kind) {
						case FLAT -> s.flat = e;
						case ANGRY -> {
							s.flat = e;
							s.puff = e;
						}
						case PERK -> s.perk = e;
						case WAG -> {
							s.wag = e;
							s.perk = 0.5f * e;
						}
					}
				}
			}
		}
		Shown sh = EMOTES.get(id);
		if (sh != null) {
			long t = now - sh.startMs;
			if (t >= EMOTE_MS) EMOTES.remove(id);
			else {
				s.emote = sh.emote;
				s.emoteT = t / (float) EMOTE_MS;
			}
		}
	}

	// ======================== emotki ========================

	/** Klawisz / przycisk / komenda: pokazuję emotkę nad sobą i wysyłam innym. false = za szybko. */
	public static boolean emote(Emote e) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) return false;
		long now = Util.getMeasuringTimeMs();
		if (now - myLastEmoteMs < EMOTE_COOLDOWN_MS) return false;
		myLastEmoteMs = now;
		int nonce = RANDOM.nextInt();
		Voices.fresh(mc.player.getUuid(), nonce);
		EMOTES.put(mc.player.getUuid(), new Shown(e, now));
		// w pierwszej osobie swojej głowy nie widać – krótka informacja nad paskiem
		mc.player.sendMessage(Text.literal(e.symbol + " " + tr(e.label)).formatted(Formatting.LIGHT_PURPLE), true);
		NetClient.sendSignal(Signal.emote(e, nonce));
		return true;
	}

	/** Emotka od innego gracza (już bez duplikatów). */
	public static void shown(UUID who, Emote e) {
		EMOTES.put(who, new Shown(e, Util.getMeasuringTimeMs()));
	}

	public static KeyBinding key(Emote e) {
		return KEYS.get(e);
	}

	/** Po rozłączeniu. */
	public static void clear() {
		ACTIVE.clear();
		EMOTES.clear();
	}
}
