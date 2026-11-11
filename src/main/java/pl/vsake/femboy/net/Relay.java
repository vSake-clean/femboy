package pl.vsake.femboy.net;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import pl.vsake.femboy.FemboyMod;
import pl.vsake.femboy.pat.PatPat;
import pl.vsake.femboy.voice.Voice;
import pl.vsake.femboy.voice.Voices;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Wymiana przez internet, niezależna od serwera Minecrafta: publiczne brokery MQTT (bez kont i konfiguracji).
 * Działa na każdym serwerze (vanilla, Paper, sieci…) i z każdego Wi-Fi: gdy sieć blokuje porty MQTT, idzie WebSocketem
 * przez HTTPS. Nie da się tego wyłączyć – łączy się samo po starcie gry.
 *
 * Tematy (UUID = UUID gracza widziany przez serwer, ten sam u wszystkich na tym serwerze):
 *   femboy/v2/look/UUID – mój strój (wiadomość „retained”: broker pamięta ostatnią, nowi dostają od razu); pusta = nie udostępniam
 *   femboy/v2/pat/UUID  – kogo głaszczę (UUID istoty), pusta = przestałem; co sekundę odświeżane
 *   femboy/v2/voice/UUID – mój odgłos (miau, hau…), Voice.Msg.encode(); jednorazowo, bez „retained”
 *   femboy/v2/sig/UUID   – drobne sygnały: emotka, clicker, smycz (Signal.encode()); bez „retained”
 * Subskrybujemy tylko graczy, których widzimy w świecie – nikt nie dostaje całego świata, a ja nie dostaję obcych.
 * Łączymy się z kilkoma brokerami naraz i publikujemy na każdy: gdy jeden padnie, reszta dalej działa.
 */
public final class Relay {
	private Relay() {
	}

	/** v2: strój bez zakolanówek, z miejscem „efekt” – inny układ pól niż w v1. */
	private static final String ROOT = "femboy/v2/";
	private static final int KEEPALIVE_S = 45;
	private static final long PING_MS = 25_000, LINGER_MS = 60_000;
	private static final int MAX_PLAYERS = 120;

	private static final Mqtt.Kind TLS = Mqtt.Kind.TLS, TCP = Mqtt.Kind.TCP, WSS = Mqtt.Kind.WSS;
	/** Publiczne brokery – wszyscy z modem łączą się z każdym, do którego dojdą, i publikują na każdy, więc wystarczy jeden wspólny. */
	private static final List<List<Mqtt.Endpoint>> BROKERS = List.of(
		List.of(new Mqtt.Endpoint("broker.emqx.io", 8883, TLS, null), new Mqtt.Endpoint("broker.emqx.io", 1883, TCP, null),
			new Mqtt.Endpoint("broker.emqx.io", 8084, WSS, "/mqtt")),
		List.of(new Mqtt.Endpoint("broker.hivemq.com", 8883, TLS, null), new Mqtt.Endpoint("broker.hivemq.com", 1883, TCP, null),
			new Mqtt.Endpoint("broker.hivemq.com", 8884, WSS, "/mqtt")),
		// port 443 jak zwykła strona – przechodzi przez szkolne/firmowe Wi-Fi
		List.of(new Mqtt.Endpoint("mqtt.eclipseprojects.io", 443, WSS, "/mqtt"), new Mqtt.Endpoint("mqtt.eclipseprojects.io", 1883, TCP, null)));

	private static final class Conn {
		final String host;
		final List<Mqtt.Endpoint> endpoints;
		/** Który sposób zadziałał ostatnio – próbujemy go najpierw. */
		volatile int good;
		volatile Mqtt mqtt;
		volatile boolean connecting;
		long nextTryMs;
		long backoffMs = 5_000;
		long lastPingMs;
		final Set<String> subs = new HashSet<>();
		/** Co wysłałem na tym połączeniu (po każdym połączeniu od nowa). */
		List<String> sentLook;
		boolean fresh;

		Conn(List<Mqtt.Endpoint> endpoints) {
			this.endpoints = endpoints;
			this.host = endpoints.get(0).host();
		}
	}

	private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "femboy-relay");
		t.setDaemon(true);
		return t;
	});
	private static final String CLIENT_ID = "femboy-" + Long.toHexString(new java.util.Random().nextLong() & 0xFFFFFFFFFFFFL);
	private static final List<Conn> CONNS = new ArrayList<>();
	/** Gracze w zasięgu → kiedy ostatnio widziani (subskrypcje trzymamy jeszcze minutę, żeby nie mrugały). */
	private static final Map<UUID, Long> SEEN = new HashMap<>();
	private static boolean warned;

	static {
		for (List<Mqtt.Endpoint> b : BROKERS) CONNS.add(new Conn(b));
	}

	/** Wszystkie sposoby połączenia (do testu na żywo). */
	static List<Mqtt.Endpoint> endpoints() {
		List<Mqtt.Endpoint> all = new ArrayList<>();
		BROKERS.forEach(all::addAll);
		return all;
	}

	/** Ile brokerów jest połączonych (do /femboy status). */
	public static int connected() {
		int n = 0;
		for (Conn c : CONNS) if (c.mqtt != null && c.mqtt.isOpen()) n++;
		return n;
	}

	public static int brokers() {
		return CONNS.size();
	}

	/** Co sekundę z wątku gry. look = mój strój do wysłania (pusta lista = nie udostępniam), null = nie jestem w świecie. */
	static void tick(MinecraftClient mc, List<String> look) {
		long now = System.currentTimeMillis();
		boolean inWorld = mc.world != null && mc.player != null;
		// poza światem tylko trzymamy połączenia, żeby po wejściu na serwer wszystko działało od razu
		Set<String> topics = inWorld ? topics(mc, now) : null;
		String myLook = inWorld ? ROOT + "look/" + mc.player.getUuid() : null;
		for (Conn c : CONNS) {
			Mqtt m = c.mqtt;
			if (m == null || !m.isOpen()) {
				if (m != null) { // zerwane – połączymy od nowa
					c.mqtt = null;
					c.backoffMs = Math.min(c.backoffMs * 2, 120_000);
					c.nextTryMs = now + c.backoffMs;
				}
				if (!c.connecting && now >= c.nextTryMs) connect(c);
				continue;
			}
			try {
				if (c.fresh) {
					c.fresh = false;
					c.subs.clear();
					c.sentLook = null;
					c.backoffMs = 5_000;
				}
				if (now - c.lastPingMs > PING_MS) {
					m.ping();
					c.lastPingMs = now;
				}
				if (!inWorld) continue;
				List<String> add = new ArrayList<>(), remove = new ArrayList<>();
				for (String t : topics) if (!c.subs.contains(t)) add.add(t);
				for (String t : c.subs) if (!topics.contains(t)) remove.add(t);
				for (int i = 0; i < add.size(); i += 40) m.subscribe(add.subList(i, Math.min(add.size(), i + 40)));
				for (int i = 0; i < remove.size(); i += 40) m.unsubscribe(remove.subList(i, Math.min(remove.size(), i + 40)));
				c.subs.addAll(add);
				remove.forEach(c.subs::remove);
				if (look != null && !look.equals(c.sentLook)) {
					// pusta wiadomość „retained” kasuje mój strój u brokera
					m.publish(myLook, look.isEmpty() ? new byte[0] : String.join("\n", look).getBytes(StandardCharsets.UTF_8), true);
					c.sentLook = look;
				}
			} catch (Exception e) {
				m.close();
			}
		}
	}

	/** Głaszczę tę istotę (null = przestałem). Z wątku gry. */
	static void pat(MinecraftClient mc, LivingEntity target) {
		if (mc.player == null) return;
		byte[] payload = target == null ? new byte[0] : target.getUuid().toString().getBytes(StandardCharsets.UTF_8);
		String topic = ROOT + "pat/" + mc.player.getUuid();
		for (Conn c : CONNS) {
			Mqtt m = c.mqtt;
			if (m == null || !m.isOpen()) continue;
			try {
				m.publish(topic, payload, false);
			} catch (Exception e) {
				m.close();
			}
		}
	}

	/** Wydałem odgłos. Z wątku gry. */
	static void voice(MinecraftClient mc, Voice.Msg msg) {
		if (mc.player == null) return;
		byte[] payload = msg.encode().getBytes(StandardCharsets.UTF_8);
		String topic = ROOT + "voice/" + mc.player.getUuid();
		for (Conn c : CONNS) {
			Mqtt m = c.mqtt;
			if (m == null || !m.isOpen()) continue;
			try {
				m.publish(topic, payload, false);
			} catch (Exception e) {
				m.close();
			}
		}
	}

	/** Emotka / clicker / smycz. Z wątku gry. */
	static void signal(MinecraftClient mc, Signal s) {
		if (mc.player == null) return;
		byte[] payload = s.encode().getBytes(StandardCharsets.UTF_8);
		String topic = ROOT + "sig/" + mc.player.getUuid();
		for (Conn c : CONNS) {
			Mqtt m = c.mqtt;
			if (m == null || !m.isOpen()) continue;
			try {
				m.publish(topic, payload, false);
			} catch (Exception e) {
				m.close();
			}
		}
	}

	private static Set<String> topics(MinecraftClient mc, long now) {
		UUID self = mc.player.getUuid();
		for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
			if (p.getUuid().equals(self)) continue;
			SEEN.put(p.getUuid(), now);
		}
		SEEN.values().removeIf(t -> now - t > LINGER_MS);
		Set<String> topics = new HashSet<>();
		// najświeżej widziani pierwsi (zatłoczone lobby nie zasypie brokera)
		SEEN.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue())).limit(MAX_PLAYERS).forEach(e -> {
			topics.add(ROOT + "look/" + e.getKey());
			topics.add(ROOT + "pat/" + e.getKey());
			topics.add(ROOT + "voice/" + e.getKey());
			topics.add(ROOT + "sig/" + e.getKey());
		});
		return topics;
	}

	private static void connect(Conn c) {
		c.connecting = true;
		IO.execute(() -> {
			Mqtt m = null;
			Exception last = null;
			// najpierw sposób, który ostatnio zadziałał, potem reszta (część sieci blokuje któryś port)
			int n = c.endpoints.size();
			for (int i = 0; i < n && m == null; i++) {
				int idx = (c.good + i) % n;
				try {
					m = Mqtt.connect(c.endpoints.get(idx), CLIENT_ID, KEEPALIVE_S, LISTENER);
					c.good = idx;
				} catch (Exception e) {
					last = e;
				}
			}
			Mqtt done = m;
			Exception err = last;
			MinecraftClient.getInstance().execute(() -> {
				c.connecting = false;
				if (done == null) {
					c.backoffMs = Math.min(c.backoffMs * 2, 120_000);
					c.nextTryMs = System.currentTimeMillis() + c.backoffMs;
					if (!warned) {
						warned = true;
						FemboyMod.LOG.info("Femboy: nie mogę połączyć z {} ({}), próbuję dalej w tle", c.host, err == null ? "?" : err.toString());
					}
					return;
				}
				FemboyMod.LOG.info("Femboy: połączono z {} – wymiana dodatków i głaskania działa", c.endpoints.get(c.good));
				c.mqtt = done;
				c.fresh = true;
				c.lastPingMs = System.currentTimeMillis();
			});
		});
	}

	private static final Mqtt.Listener LISTENER = new Mqtt.Listener() {
		@Override
		public void message(String topic, byte[] payload) {
			if (!topic.startsWith(ROOT) || payload.length > 1024) return;
			String rest = topic.substring(ROOT.length());
			int slash = rest.indexOf('/');
			if (slash < 0) return;
			String kind = rest.substring(0, slash);
			UUID who;
			try {
				who = UUID.fromString(rest.substring(slash + 1));
			} catch (IllegalArgumentException e) {
				return;
			}
			String text = new String(payload, StandardCharsets.UTF_8);
			MinecraftClient mc = MinecraftClient.getInstance();
			switch (kind) {
				case "look" -> mc.execute(() -> NetClient.receivedLook(who, text.isEmpty() ? List.of() : Arrays.asList(text.split("\n", -1))));
				case "pat" -> mc.execute(() -> receivedPat(mc, who, text.trim()));
				case "voice" -> {
					Voice.Msg msg = Voice.Msg.decode(text);
					if (msg != null) mc.execute(() -> Voices.heard(who, msg));
				}
				case "sig" -> {
					Signal sig = Signal.decode(text);
					if (sig != null) mc.execute(() -> NetClient.receivedSignal(who, sig));
				}
				default -> {
				}
			}
		}

		@Override
		public void closed(Mqtt mqtt, Throwable why) {
			// tick() zauważy i połączy od nowa
		}
	};

	private static void receivedPat(MinecraftClient mc, UUID patterId, String target) {
		if (mc.world == null) return;
		PlayerEntity patter = mc.world.getPlayerByUuid(patterId);
		if (patter == null) return;
		if (target.isEmpty()) {
			PatPat.remote(patter.getId(), -1);
			return;
		}
		UUID t;
		try {
			t = UUID.fromString(target);
		} catch (IllegalArgumentException e) {
			return;
		}
		Entity found = mc.world.getPlayerByUuid(t);
		if (found == null)
			for (Entity e : mc.world.getEntities())
				if (t.equals(e.getUuid())) {
					found = e;
					break;
				}
		// głaskany musi być naprawdę obok głaszczącego (inaczej ktoś mógłby „głaskać” z drugiego końca mapy)
		if (found instanceof LivingEntity && found != patter && found.squaredDistanceTo(patter) <= 12 * 12)
			PatPat.remote(patter.getId(), found.getId());
	}

	/** Po wyjściu z serwera: wypisujemy się ze wszystkiego, po wejściu „retained” stroje przyjdą od nowa. */
	static void leftServer() {
		SEEN.clear();
		for (Conn c : CONNS) {
			Mqtt m = c.mqtt;
			if (m != null && m.isOpen() && !c.subs.isEmpty()) {
				List<String> all = new ArrayList<>(c.subs);
				try {
					for (int i = 0; i < all.size(); i += 40) m.unsubscribe(all.subList(i, Math.min(all.size(), i + 40)));
				} catch (Exception e) {
					m.close();
				}
			}
			c.subs.clear();
		}
	}
}
