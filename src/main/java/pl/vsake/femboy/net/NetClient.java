package pl.vsake.femboy.net;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import pl.vsake.femboy.FemboyMod;
import pl.vsake.femboy.acc.Accessories;
import pl.vsake.femboy.acc.Outfit;
import pl.vsake.femboy.net.Payloads.OutfitC2S;
import pl.vsake.femboy.net.Payloads.OutfitS2C;
import pl.vsake.femboy.net.Payloads.PatC2S;
import pl.vsake.femboy.net.Payloads.PatS2C;
import pl.vsake.femboy.fx.Clicker;
import pl.vsake.femboy.fx.Leash;
import pl.vsake.femboy.fx.Reactions;
import pl.vsake.femboy.net.Payloads.SignalC2S;
import pl.vsake.femboy.net.Payloads.SignalS2C;
import pl.vsake.femboy.net.Payloads.VoiceC2S;
import pl.vsake.femboy.net.Payloads.VoiceS2C;
import pl.vsake.femboy.voice.Voice;
import pl.vsake.femboy.voice.Voices;
import pl.vsake.femboy.pat.PatPat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Klient: wysyła moje dodatki i moje głaskanie, odbiera cudze.
 * Idzie tylko MÓJ wygląd – skiny i dodatki, które ustawiłem innym graczom, nigdy nie wychodzą z mojego komputera.
 */
public final class NetClient {
	private NetClient() {
	}

	/** Stroje udostępnione przez innych graczy z modem. */
	private static final Map<UUID, Outfit> SHARED = new HashMap<>();
	private static final Map<UUID, Long> PATS = new HashMap<>();
	/** Ostatnio wysłany strój (null = jeszcze nic w tym połączeniu). */
	private static List<String> sent;
	private static int ticks;

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(OutfitS2C.ID, (p, ctx) -> receivedLook(p.player(), p.outfit()));
		ClientPlayNetworking.registerGlobalReceiver(PatS2C.ID, (p, ctx) -> PatPat.remote(p.patter(), p.target()));
		ClientPlayNetworking.registerGlobalReceiver(VoiceS2C.ID, (p, ctx) -> {
			Voice.Msg m = Voice.Msg.of(p.voice(), p.variant(), p.pitch(), p.nonce());
			if (m != null) Voices.heard(p.player(), m);
		});
		ClientPlayNetworking.registerGlobalReceiver(SignalS2C.ID, (p, ctx) -> {
			Signal s = Signal.of(p.kind(), p.arg(), p.nonce());
			if (s != null) receivedSignal(p.player(), s);
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> sent = null));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(NetClient::clear));
		ClientTickEvents.END_CLIENT_TICK.register(NetClient::tick);
	}

	/** Czy serwer ma moda (da się coś wysłać). */
	public static boolean available() {
		try {
			return MinecraftClient.getInstance().getNetworkHandler() != null && ClientPlayNetworking.canSend(OutfitC2S.ID);
		} catch (Throwable t) {
			return false;
		}
	}

	/** Strój od gracza (z serwera z modem albo z brokera); pusta lista = przestał udostępniać. Wątek gry. */
	static void receivedLook(UUID player, List<String> wire) {
		// ostatnie pole to licznik głaskań, reszta – strój
		long pats = -1;
		if (wire.size() == Payloads.OUTFIT_FIELDS) {
			try {
				pats = Math.max(0, Math.min(999_999_999L, Long.parseLong(wire.get(wire.size() - 1))));
			} catch (NumberFormatException ignored) {
			}
			wire = wire.subList(0, wire.size() - 1);
		}
		if (pats >= 0) PATS.put(player, pats);
		else PATS.remove(player);
		Outfit o = wire.isEmpty() ? null : Outfit.fromWire(wire);
		Outfit old = o == null ? SHARED.remove(player) : SHARED.put(player, o);
		if (old != null || o != null) Accessories.forget(player);
	}

	/** Ile razy pogłaskano tego gracza (sam to liczy i przysyła) albo -1. */
	public static long pats(UUID player) {
		Long n = PATS.get(player);
		return n == null ? -1 : n;
	}

	/** Strój, który ten gracz sam sobie ustawił (null = nie ma moda / jeszcze nie przyszedł). */
	public static Outfit shared(UUID player) {
		return SHARED.get(player);
	}

	/** Raz na sekundę: jeśli mój wygląd się zmienił, wysyłam go od nowa. */
	private static void tick(MinecraftClient mc) {
		if (++ticks % 20 != 0) return;
		List<String> now = null;
		if (mc.player != null) {
			Outfit o = Accessories.forSharing(mc.player.getUuid(), mc.player.getGameProfile().name());
			now = (o == null ? new Outfit() : o).toWire(); // pusty strój = „noszę nic”
			now.add(Long.toString(FemboyMod.CONFIG.pats)); // licznik głaskań jedzie razem ze strojem
		}
		try {
			Relay.tick(mc, now); // przez internet – każdy serwer
		} catch (Throwable t) {
			FemboyMod.LOG.warn("Femboy: błąd wymiany przez internet", t);
		}
		if (now == null || !available() || now.equals(sent)) return; // przez serwer – gdy ma moda
		sent = now;
		ClientPlayNetworking.send(new OutfitC2S(now));
	}

	/** Głaszczę tę istotę, null = przestałem. */
	public static void sendPat(LivingEntity target) {
		MinecraftClient mc = MinecraftClient.getInstance();
		try {
			Relay.pat(mc, target);
			if (mc.getNetworkHandler() != null && ClientPlayNetworking.canSend(PatC2S.ID))
				ClientPlayNetworking.send(new PatC2S(target == null ? -1 : target.getId()));
		} catch (Throwable ignored) { // rozłączanie w trakcie
		}
	}

	/** Wydałem odgłos – przez internet i przez serwer z modem (odbiorca odrzuci duplikat po nonce). */
	public static void sendVoice(Voice.Msg msg) {
		MinecraftClient mc = MinecraftClient.getInstance();
		try {
			Relay.voice(mc, msg);
			if (mc.getNetworkHandler() != null && ClientPlayNetworking.canSend(VoiceC2S.ID))
				ClientPlayNetworking.send(new VoiceC2S(msg.voice().id, msg.variant(), msg.pitch(), msg.nonce()));
		} catch (Throwable ignored) { // rozłączanie w trakcie
		}
	}

	/** Emotka / clicker / smycz ode mnie – przez internet i przez serwer z modem. */
	public static void sendSignal(Signal s) {
		MinecraftClient mc = MinecraftClient.getInstance();
		try {
			Relay.signal(mc, s);
			if (mc.getNetworkHandler() != null && ClientPlayNetworking.canSend(SignalC2S.ID))
				ClientPlayNetworking.send(new SignalC2S(s.kind().id, s.arg(), s.nonce()));
		} catch (Throwable ignored) { // rozłączanie w trakcie
		}
	}

	/** Sygnał od innego gracza (z brokera albo serwera). Wątek gry. */
	static void receivedSignal(UUID who, Signal s) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null || mc.player == null || who.equals(mc.player.getUuid())) return;
		switch (s.kind()) {
			case EMOTE -> {
				if (Voices.fresh(who, s.nonce())) Reactions.shown(who, s.emoteArg());
			}
			case CLICK -> {
				if (Voices.fresh(who, s.nonce())) Clicker.heard(who);
			}
			// smycz jest odświeżana co 2 s – to samo kilka razy nie szkodzi
			case LEASH -> Leash.remote(who, s.leashTarget());
			case POSE -> pl.vsake.femboy.fx.Poses.remote(who, s.poseArg(), s.nonce());
			case BOOP -> pl.vsake.femboy.fx.Boop.remote(who, s.uuidArg(), s.nonce());
			case BOND -> pl.vsake.femboy.fx.Bonds.remote(who, s.bondKind(), s.bondTarget());
			case OK -> pl.vsake.femboy.fx.Bonds.remoteOk(who, s.uuidArg());
			case LASER -> pl.vsake.femboy.fx.Toys.remoteLaser(who, s.numbersArg());
			case YARN -> {
				if (Voices.fresh(who, s.nonce())) pl.vsake.femboy.fx.Toys.remoteYarn(who, s.numbersArg());
			}
		}
	}

	private static void clear() {
		SHARED.clear();
		PATS.clear();
		sent = null;
		Relay.leftServer();
	}
}
