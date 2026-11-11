package pl.vsake.femboy.net;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Util;
import pl.vsake.femboy.net.Payloads.OutfitC2S;
import pl.vsake.femboy.net.Payloads.OutfitS2C;
import pl.vsake.femboy.net.Payloads.PatC2S;
import pl.vsake.femboy.net.Payloads.PatS2C;
import pl.vsake.femboy.net.Payloads.SignalC2S;
import pl.vsake.femboy.net.Payloads.SignalS2C;
import pl.vsake.femboy.net.Payloads.VoiceC2S;
import pl.vsake.femboy.net.Payloads.VoiceS2C;
import pl.vsake.femboy.voice.Voice;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wspólna część (klient i serwer): rejestracja pakietów i przekaźnik na serwerze.
 * Serwer nic nie rysuje – tylko rozsyła głaskanie i stroje graczom z modem.
 * Działa, gdy mod jest na serwerze (Fabric) albo w singlu/LAN-ie u hosta; na serwerze bez moda nic nie leci.
 */
public final class FemboyNet implements ModInitializer {
	/** Jak daleko od głaszczącego może być głaskany (z zapasem na lag, klient pozwala na 6/10). */
	private static final double PAT_RANGE = 12.0;
	private static final long OUTFIT_MIN_MS = 250, PAT_MIN_MS = 50, VOICE_MIN_MS = 200, SIGNAL_MIN_MS = 100;

	/** Ostatni strój każdego gracza (tylko udostępniające). */
	private static final Map<UUID, List<String>> OUTFITS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_OUTFIT = new HashMap<>(), LAST_PAT = new HashMap<>(), LAST_VOICE = new HashMap<>(), LAST_SIGNAL = new HashMap<>();

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.playC2S().register(PatC2S.ID, PatC2S.CODEC);
		PayloadTypeRegistry.playS2C().register(PatS2C.ID, PatS2C.CODEC);
		PayloadTypeRegistry.playC2S().register(OutfitC2S.ID, OutfitC2S.CODEC);
		PayloadTypeRegistry.playS2C().register(OutfitS2C.ID, OutfitS2C.CODEC);
		PayloadTypeRegistry.playC2S().register(VoiceC2S.ID, VoiceC2S.CODEC);
		PayloadTypeRegistry.playS2C().register(VoiceS2C.ID, VoiceS2C.CODEC);
		PayloadTypeRegistry.playC2S().register(SignalC2S.ID, SignalC2S.CODEC);
		PayloadTypeRegistry.playS2C().register(SignalS2C.ID, SignalS2C.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(PatC2S.ID, (p, ctx) -> pat(ctx.player(), p.target()));
		ServerPlayNetworking.registerGlobalReceiver(VoiceC2S.ID, (p, ctx) -> voice(ctx.player(), p));
		ServerPlayNetworking.registerGlobalReceiver(SignalC2S.ID, (p, ctx) -> signal(ctx.player(), p));
		ServerPlayNetworking.registerGlobalReceiver(OutfitC2S.ID, (p, ctx) -> outfit(ctx.server(), ctx.player(), p.outfit()));

		// nowy gracz dostaje stroje wszystkich obecnych
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity joined = handler.getPlayer();
			if (!ServerPlayNetworking.canSend(joined, OutfitS2C.ID)) return;
			OUTFITS.forEach((id, o) -> {
				if (!id.equals(joined.getUuid())) ServerPlayNetworking.send(joined, new OutfitS2C(id, o));
			});
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.getPlayer().getUuid();
			LAST_OUTFIT.remove(id);
			LAST_PAT.remove(id);
			LAST_VOICE.remove(id);
			LAST_SIGNAL.remove(id);
			if (OUTFITS.remove(id) != null) broadcast(server, id, List.of());
		});
	}

	private static boolean tooSoon(Map<UUID, Long> last, UUID id, long minMs) {
		long now = Util.getMeasuringTimeMs();
		Long prev = last.get(id);
		if (prev != null && now - prev < minMs) return true;
		last.put(id, now);
		return false;
	}

	private static void pat(ServerPlayerEntity patter, int targetId) {
		// koniec głaskania zawsze przechodzi, start/odświeżenie najwyżej co 50 ms
		if (targetId >= 0 && tooSoon(LAST_PAT, patter.getUuid(), PAT_MIN_MS)) return;
		int target = -1;
		if (targetId >= 0) {
			Entity e = patter.getEntityWorld().getEntityById(targetId);
			if (e instanceof LivingEntity && e != patter && e.isAlive() && e.squaredDistanceTo(patter) <= PAT_RANGE * PAT_RANGE)
				target = targetId;
		}
		PatS2C out = new PatS2C(patter.getId(), target);
		for (ServerPlayerEntity p : PlayerLookup.tracking(patter))
			if (p != patter && ServerPlayNetworking.canSend(p, PatS2C.ID)) ServerPlayNetworking.send(p, out);
	}

	/** Odgłos idzie do graczy z modem, którzy widzą nadawcę (dalej i tak by go nie usłyszeli). */
	private static void voice(ServerPlayerEntity from, VoiceC2S p) {
		if (Voice.Msg.of(p.voice(), p.variant(), p.pitch(), p.nonce()) == null) return;
		if (tooSoon(LAST_VOICE, from.getUuid(), VOICE_MIN_MS)) return;
		VoiceS2C out = new VoiceS2C(from.getUuid(), p.voice(), p.variant(), p.pitch(), p.nonce());
		for (ServerPlayerEntity pl : PlayerLookup.tracking(from))
			if (pl != from && ServerPlayNetworking.canSend(pl, VoiceS2C.ID)) ServerPlayNetworking.send(pl, out);
	}

	/** Emotka / clicker / smycz: do graczy z modem, którzy widzą nadawcę. */
	private static void signal(ServerPlayerEntity from, SignalC2S p) {
		Signal s = Signal.of(p.kind(), p.arg(), p.nonce());
		if (s == null || tooSoon(LAST_SIGNAL, from.getUuid(), SIGNAL_MIN_MS)) return;
		SignalS2C out = new SignalS2C(from.getUuid(), s.kind().id, s.arg(), s.nonce());
		for (ServerPlayerEntity pl : PlayerLookup.tracking(from))
			if (pl != from && ServerPlayNetworking.canSend(pl, SignalS2C.ID)) ServerPlayNetworking.send(pl, out);
	}

	private static void outfit(MinecraftServer server, ServerPlayerEntity player, List<String> outfit) {
		if (!outfit.isEmpty() && outfit.size() != Payloads.OUTFIT_FIELDS) return;
		UUID id = player.getUuid();
		if (tooSoon(LAST_OUTFIT, id, OUTFIT_MIN_MS)) return;
		List<String> copy = List.copyOf(outfit);
		if (copy.isEmpty()) {
			if (OUTFITS.remove(id) == null) return;
		} else if (copy.equals(OUTFITS.put(id, copy))) return;
		broadcast(server, id, copy);
	}

	private static void broadcast(MinecraftServer server, UUID id, List<String> outfit) {
		OutfitS2C out = new OutfitS2C(id, outfit);
		for (ServerPlayerEntity p : PlayerLookup.all(server))
			if (!p.getUuid().equals(id) && ServerPlayNetworking.canSend(p, OutfitS2C.ID)) ServerPlayNetworking.send(p, out);
	}
}
