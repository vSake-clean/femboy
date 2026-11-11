package pl.vsake.femboy.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.List;
import java.util.UUID;

/**
 * Pakiety między graczami z modem (przez serwer, który też ma moda – albo hosta LAN / singla).
 * Bez klas klienta: ładowane też na serwerze dedykowanym.
 * Strój leci jako lista napisów (Outfit.toWire): pusta = „nie udostępniam”, 14 pól = mój strój i licznik głaskań.
 */
public final class Payloads {
	private Payloads() {
	}

	public static final String NS = "femboy";
	/** Pola stroju: 11 slotów + futro + kolor + licznik głaskań. */
	public static final int OUTFIT_FIELDS = 14;
	private static final PacketCodec<ByteBuf, List<String>> OUTFIT =
		PacketCodecs.string(32).collect(PacketCodecs.toList(OUTFIT_FIELDS));

	/** Klient → serwer: głaszczę tę istotę (id sieciowe), -1 = przestałem. */
	public record PatC2S(int target) implements CustomPayload {
		public static final Id<PatC2S> ID = new Id<>(Identifier.of(NS, "pat"));
		public static final PacketCodec<RegistryByteBuf, PatC2S> CODEC =
			PacketCodec.tuple(PacketCodecs.VAR_INT, PatC2S::target, PatC2S::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Serwer → klient: ten gracz (id sieciowe) głaszcze tę istotę, -1 = przestał. */
	public record PatS2C(int patter, int target) implements CustomPayload {
		public static final Id<PatS2C> ID = new Id<>(Identifier.of(NS, "pat_seen"));
		public static final PacketCodec<RegistryByteBuf, PatS2C> CODEC =
			PacketCodec.tuple(PacketCodecs.VAR_INT, PatS2C::patter, PacketCodecs.VAR_INT, PatS2C::target, PatS2C::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Klient → serwer: moje dodatki (tylko moje – ustawień innych graczy nikt nie dostaje). */
	public record OutfitC2S(List<String> outfit) implements CustomPayload {
		public static final Id<OutfitC2S> ID = new Id<>(Identifier.of(NS, "outfit"));
		public static final PacketCodec<RegistryByteBuf, OutfitC2S> CODEC =
			PacketCodec.tuple(OUTFIT, OutfitC2S::outfit, OutfitC2S::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Serwer → klient: dodatki tego gracza. */
	public record OutfitS2C(UUID player, List<String> outfit) implements CustomPayload {
		public static final Id<OutfitS2C> ID = new Id<>(Identifier.of(NS, "outfit_seen"));
		public static final PacketCodec<RegistryByteBuf, OutfitS2C> CODEC =
			PacketCodec.tuple(Uuids.PACKET_CODEC, OutfitS2C::player, OUTFIT, OutfitS2C::outfit, OutfitS2C::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Klient → serwer: drobny sygnał (emotka, clicker, smycz) – patrz Signal. */
	public record SignalC2S(String kind, String arg, int nonce) implements CustomPayload {
		public static final Id<SignalC2S> ID = new Id<>(Identifier.of(NS, "signal"));
		public static final PacketCodec<RegistryByteBuf, SignalC2S> CODEC = PacketCodec.tuple(
			PacketCodecs.string(16), SignalC2S::kind, PacketCodecs.string(Signal.MAX_ARG), SignalC2S::arg,
			PacketCodecs.INTEGER, SignalC2S::nonce, SignalC2S::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Serwer → klient: sygnał od tego gracza. */
	public record SignalS2C(UUID player, String kind, String arg, int nonce) implements CustomPayload {
		public static final Id<SignalS2C> ID = new Id<>(Identifier.of(NS, "signal_seen"));
		public static final PacketCodec<RegistryByteBuf, SignalS2C> CODEC = PacketCodec.tuple(
			Uuids.PACKET_CODEC, SignalS2C::player, PacketCodecs.string(16), SignalS2C::kind, PacketCodecs.string(Signal.MAX_ARG), SignalS2C::arg,
			PacketCodecs.INTEGER, SignalS2C::nonce, SignalS2C::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Klient → serwer: wydałem odgłos (miau, hau…) – patrz voice/Voice. */
	public record VoiceC2S(String voice, int variant, int pitch, int nonce) implements CustomPayload {
		public static final Id<VoiceC2S> ID = new Id<>(Identifier.of(NS, "voice"));
		public static final PacketCodec<RegistryByteBuf, VoiceC2S> CODEC = PacketCodec.tuple(
			PacketCodecs.string(16), VoiceC2S::voice, PacketCodecs.VAR_INT, VoiceC2S::variant,
			PacketCodecs.VAR_INT, VoiceC2S::pitch, PacketCodecs.INTEGER, VoiceC2S::nonce, VoiceC2S::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Serwer → klient: ten gracz wydał odgłos. */
	public record VoiceS2C(UUID player, String voice, int variant, int pitch, int nonce) implements CustomPayload {
		public static final Id<VoiceS2C> ID = new Id<>(Identifier.of(NS, "voice_heard"));
		public static final PacketCodec<RegistryByteBuf, VoiceS2C> CODEC = PacketCodec.tuple(
			Uuids.PACKET_CODEC, VoiceS2C::player, PacketCodecs.string(16), VoiceS2C::voice, PacketCodecs.VAR_INT, VoiceS2C::variant,
			PacketCodecs.VAR_INT, VoiceS2C::pitch, PacketCodecs.INTEGER, VoiceS2C::nonce, VoiceS2C::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}
