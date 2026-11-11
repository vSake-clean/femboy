package pl.vsake.femboy.voice;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.EntityTrackingSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import pl.vsake.femboy.FemboyConfig;
import pl.vsake.femboy.FemboyMod;
import pl.vsake.femboy.net.NetClient;
import pl.vsake.femboy.Keys;
import pl.vsake.femboy.fx.Reactions;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Klient: klawisz → odgłos (miau, hau…). Gram go u siebie od razu i wysyłam innym graczom z modem
 * (przez internet i przez serwer z modem). Cudze gram przy ich postaci, z ich głośnością z ustawień.
 * Klawisze w Sterowaniu → Femboy Skins; domyślnie miau = Z, hau = X, reszta bez klawisza.
 */
public final class Voices {
	private Voices() {
	}

	/** Bazowa głośność (100% w ustawieniach). */
	private static final float BASE = 0.9f;
	/** Najczęściej, jak można wydać odgłos (trzymanie klawisza nie robi karabinu). */
	private static final long MY_COOLDOWN_MS = 350;
	/** Od jednego gracza gramy najwyżej co tyle – ochrona przed spamem z cudzego klienta. */
	private static final long THEIR_COOLDOWN_MS = 200;
	private static final int NONCES = 64;

	private static final Random RANDOM = new Random();
	private static final Map<Voice, KeyBinding> KEYS = new EnumMap<>(Voice.class);
	private static final Map<Voice, SoundEvent[]> EVENTS = new EnumMap<>(Voice.class);
	/** Ostatnie nonce od każdego gracza – ta sama wiadomość przychodzi z kilku brokerów i z serwera. */
	private static final Map<UUID, ArrayDeque<Integer>> SEEN = new HashMap<>();
	private static final Map<UUID, Long> LAST = new HashMap<>();
	private static long myLastMs;

	public static void init() {
		for (Voice v : Voice.values()) {
			SoundEvent[] ev = new SoundEvent[v.variants()];
			for (int i = 0; i < ev.length; i++) ev[i] = SoundEvent.of(Identifier.of(FemboyMod.ID, v.event(i)));
			EVENTS.put(v, ev);
			int def = switch (v) {
				case MEOW -> GLFW.GLFW_KEY_Z;
				case BARK -> GLFW.GLFW_KEY_X;
				default -> GLFW.GLFW_KEY_UNKNOWN;
			};
			KEYS.put(v, Keys.register("Odgłosy", v.label, "voice." + v.id, def));
		}
		ClientTickEvents.END_CLIENT_TICK.register(Voices::tick);
	}

	private static void tick(MinecraftClient mc) {
		for (Map.Entry<Voice, KeyBinding> e : KEYS.entrySet())
			while (e.getValue().wasPressed()) if (mc.player != null && mc.currentScreen == null) emit(e.getKey());
	}

	/** Klawisz miau/hau…: losuję wariant i wysokość, gram u siebie i wysyłam. false = za szybko. */
	public static boolean emit(Voice v) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) return false;
		long now = Util.getMeasuringTimeMs();
		if (now - myLastMs < MY_COOLDOWN_MS) return false;
		myLastMs = now;
		List<Integer> on = enabled(v);
		Voice.Msg msg = new Voice.Msg(v, on.get(RANDOM.nextInt(on.size())), 92 + RANDOM.nextInt(24), RANDOM.nextInt());
		remember(mc.player.getUuid(), msg.nonce());
		play(mc.player, msg);
		NetClient.sendVoice(msg);
		return true;
	}

	/** Odsłuch jednego wariantu w GUI – tylko u mnie, nic nie leci do innych. */
	public static void preview(Voice v, int variant) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null) play(mc.player, new Voice.Msg(v, variant, 100, 0));
	}

	// ======================== warianty ========================

	/** Warianty, z których losuję (nigdy pusta). */
	public static List<Integer> enabled(Voice v) {
		List<Integer> l = FemboyMod.CONFIG.voiceVariants.get(v.id);
		if (l != null && !l.isEmpty()) return l;
		List<Integer> all = new ArrayList<>();
		for (int i = 0; i < v.variants(); i++) all.add(i);
		return all;
	}

	public static boolean isEnabled(Voice v, int variant) {
		return enabled(v).contains(variant);
	}

	/** Włącz/wyłącz wariant. false = to był ostatni włączony (musi zostać choć jeden). */
	public static boolean toggle(Voice v, int variant) {
		List<Integer> l = new ArrayList<>(enabled(v));
		if (l.contains(variant)) {
			if (l.size() == 1) return false;
			l.remove(Integer.valueOf(variant));
		} else {
			l.add(variant);
			Collections.sort(l);
		}
		setEnabled(v, l);
		return true;
	}

	/** Zostaw tylko ten jeden wariant. */
	public static void only(Voice v, int variant) {
		setEnabled(v, List.of(variant));
	}

	public static void enableAll(Voice v) {
		setEnabled(v, List.of());
	}

	private static void setEnabled(Voice v, List<Integer> l) {
		FemboyConfig cfg = FemboyMod.CONFIG;
		if (l.isEmpty() || l.size() >= v.variants()) cfg.voiceVariants.remove(v.id);
		else cfg.voiceVariants.put(v.id, new ArrayList<>(l));
		cfg.save();
	}

	// ======================== klawisze ========================

	/** Przypisz klawisz odgłosowi (InputUtil.UNKNOWN_KEY = bez klawisza); zapisuje się w options.txt jak w Sterowaniu. */
	public static void bind(Voice v, InputUtil.Key key) {
		Keys.bind(KEYS.get(v), key);
	}

	public static KeyBinding key(Voice v) {
		return KEYS.get(v);
	}

	/** true = pierwszy raz widzę tę wiadomość od tego gracza (odgłos, emotka, klik – wspólne rozpoznawanie duplikatów). */
	public static boolean fresh(UUID who, int nonce) {
		return remember(who, nonce);
	}

	/** Odgłos od innego gracza (z brokera albo serwera). Wątek gry. */
	public static void heard(UUID who, Voice.Msg msg) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null || mc.player == null || who.equals(mc.player.getUuid())) return;
		if (!remember(who, msg.nonce())) return; // duplikat
		long now = Util.getMeasuringTimeMs();
		Long last = LAST.get(who);
		if (last != null && now - last < THEIR_COOLDOWN_MS) return;
		LAST.put(who, now);
		PlayerEntity p = mc.world.getPlayerByUuid(who);
		if (p == null || p.squaredDistanceTo(mc.player) > 64 * 64) return; // nie widzę go – i tak bym nie usłyszał
		play(p, msg);
	}

	/** true = nowa wiadomość, false = już była. */
	private static boolean remember(UUID who, int nonce) {
		ArrayDeque<Integer> q = SEEN.computeIfAbsent(who, k -> new ArrayDeque<>());
		if (q.contains(nonce)) return false;
		q.addLast(nonce);
		if (q.size() > NONCES) q.removeFirst();
		return true;
	}

	private static void play(PlayerEntity at, Voice.Msg msg) {
		// uszy i ogon reagują także wtedy, gdy kogoś wyciszyłem
		if (msg.nonce() != 0) Reactions.voice(at.getUuid(), msg.voice()); // nonce 0 = odsłuch wariantu w oknie moda
		float vol = volume(at.getUuid());
		if (vol <= 0f) return;
		SoundEvent ev = EVENTS.get(msg.voice())[msg.variant()];
		// dźwięk jedzie razem z postacią; kategoria Gracze → działa też suwak z ustawień dźwięku gry
		MinecraftClient.getInstance().getSoundManager().play(
			new EntityTrackingSoundInstance(ev, SoundCategory.PLAYERS, vol, msg.pitchF(), at, RANDOM.nextLong()));
	}

	/** Głośność odgłosów tego gracza u mnie (0 = nie słyszę): ogólna × jego. */
	public static float volume(UUID who) {
		FemboyConfig cfg = FemboyMod.CONFIG;
		if (!cfg.voice) return 0f;
		FemboyConfig.PlayerSetting ps = cfg.player(who);
		if (ps != null && ps.voiceMuted) return 0f;
		int their = ps == null || ps.voiceVolume == null ? 100 : ps.voiceVolume;
		return BASE * cfg.voiceVolume / 100f * their / 100f;
	}

	/** Głośność gracza w procentach (bez wyciszenia). */
	public static int percent(UUID who) {
		FemboyConfig.PlayerSetting ps = FemboyMod.CONFIG.player(who);
		return ps == null || ps.voiceVolume == null ? 100 : ps.voiceVolume;
	}

	public static boolean muted(UUID who) {
		FemboyConfig.PlayerSetting ps = FemboyMod.CONFIG.player(who);
		return ps != null && ps.voiceMuted;
	}

	public static void setPercent(UUID who, String name, int pct) {
		FemboyConfig cfg = FemboyMod.CONFIG;
		pct = Math.max(0, Math.min(100, pct));
		cfg.playerOrCreate(who, name).voiceVolume = pct == 100 ? null : pct;
		cfg.tidy(who);
		cfg.save();
	}

	public static void setMuted(UUID who, String name, boolean muted) {
		FemboyConfig cfg = FemboyMod.CONFIG;
		cfg.playerOrCreate(who, name).voiceMuted = muted;
		cfg.tidy(who);
		cfg.save();
	}

	/** Nazwa klawisza odgłosu do komunikatów ("—" = bez klawisza). */
	public static String keyName(Voice v) {
		KeyBinding k = KEYS.get(v);
		if (k == null || k.isUnbound()) return "—";
		return k.getBoundKeyLocalizedText().getString();
	}

	/** Po rozłączeniu. */
	public static void clear() {
		SEEN.clear();
		LAST.clear();
	}
}
