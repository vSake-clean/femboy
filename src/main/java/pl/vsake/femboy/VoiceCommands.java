package pl.vsake.femboy;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import pl.vsake.femboy.voice.Voice;
import pl.vsake.femboy.voice.Voices;

import java.util.Map;
import java.util.UUID;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static pl.vsake.femboy.FemboyCommands.PLAYERS;
import static pl.vsake.femboy.FemboyCommands.cfg;
import static pl.vsake.femboy.FemboyCommands.hl;
import static pl.vsake.femboy.FemboyCommands.line;
import static pl.vsake.femboy.FemboyCommands.msg;
import static pl.vsake.femboy.FemboyCommands.onOff;
import static pl.vsake.femboy.FemboyCommands.prefix;
import static pl.vsake.femboy.FemboyCommands.target;

/**
 * /femboy sound … – odgłosy pod klawiszami (miau, hau…): ogólna głośność / wyciszenie
 * i głośność / wyciszenie pojedynczych graczy.
 */
final class VoiceCommands {
	private VoiceCommands() {
	}

	static LiteralArgumentBuilder<FabricClientCommandSource> node() {
		LiteralArgumentBuilder<FabricClientCommandSource> n = literal("sound").executes(VoiceCommands::help)
			.then(literal("on").executes(c -> setOn(c, true)))
			.then(literal("off").executes(c -> setOn(c, false)))
			.then(literal("volume")
				.executes(c -> msg(c, Text.literal("Ogólna głośność odgłosów: ").append(hl(cfg().voiceVolume + "%"))))
				.then(argument("procent", IntegerArgumentType.integer(0, 100)).executes(VoiceCommands::volumeAll)))
			.then(literal("mute").then(argument("gracz", StringArgumentType.word()).suggests(PLAYERS).executes(c -> mute(c, true))))
			.then(literal("unmute").then(argument("gracz", StringArgumentType.word()).suggests(PLAYERS).executes(c -> mute(c, false))))
			.then(literal("player").then(argument("gracz", StringArgumentType.word()).suggests(PLAYERS)
				.executes(VoiceCommands::showPlayer)
				.then(literal("mute").executes(c -> mute(c, true)))
				.then(literal("unmute").executes(c -> mute(c, false)))
				.then(literal("volume").then(argument("procent", IntegerArgumentType.integer(0, 100)).executes(VoiceCommands::volumeOne)))))
			.then(literal("list").executes(VoiceCommands::list));
		// /femboy sound meow – zagraj (jak klawisz)
		for (Voice v : Voice.values())
			n.then(literal(v.id).executes(c -> Voices.emit(v) ? 1 : 0));
		return n;
	}

	private static int help(CommandContext<FabricClientCommandSource> c) {
		FabricClientCommandSource s = c.getSource();
		s.sendFeedback(prefix().append(Text.literal("Odgłosy: ").formatted(Formatting.WHITE)).append(onOff(cfg().voice))
			.append(Text.literal(", głośność ").formatted(Formatting.GRAY)).append(hl(cfg().voiceVolume + "%")));
		StringBuilder keys = new StringBuilder();
		for (Voice v : Voice.values()) {
			if (!keys.isEmpty()) keys.append(", ");
			keys.append(v.label).append(" [").append(Voices.keyName(v)).append("]");
		}
		s.sendFeedback(Text.literal(" " + keys).formatted(Formatting.GRAY));
		line(s, "/femboy sound <" + String.join("|", Voice.ids()) + ">", "zagraj (klawisze: Sterowanie → Femboy Skins)");
		line(s, "/femboy sound on | off", "słyszę odgłosy / wyciszam wszystkie");
		line(s, "/femboy sound volume <0-100>", "ogólna głośność");
		line(s, "/femboy sound mute | unmute <gracz>", "wycisz / odcisz jedną osobę");
		line(s, "/femboy sound player <gracz> volume <0-100>", "głośność jednej osoby");
		line(s, "/femboy sound list", "kogo przyciszyłem / wyciszyłem");
		return 1;
	}

	private static int setOn(CommandContext<FabricClientCommandSource> c, boolean on) {
		cfg().voice = on;
		cfg().save();
		return msg(c, Text.literal("Odgłosy: ").append(onOff(on)));
	}

	private static int volumeAll(CommandContext<FabricClientCommandSource> c) {
		cfg().voiceVolume = IntegerArgumentType.getInteger(c, "procent");
		cfg().save();
		return msg(c, Text.literal("Ogólna głośność odgłosów: ").append(hl(cfg().voiceVolume + "%")));
	}

	private static int mute(CommandContext<FabricClientCommandSource> c, boolean muted) {
		FemboyCommands.Target t = target(c);
		if (t == null) return 0;
		Voices.setMuted(t.uuid(), t.name(), muted);
		return msg(c, hl(t.name()).append(Text.literal(muted ? " – wyciszony" : " – odciszony (" + Voices.percent(t.uuid()) + "%)")
			.formatted(Formatting.WHITE)));
	}

	private static int volumeOne(CommandContext<FabricClientCommandSource> c) {
		FemboyCommands.Target t = target(c);
		if (t == null) return 0;
		Voices.setPercent(t.uuid(), t.name(), IntegerArgumentType.getInteger(c, "procent"));
		return showPlayer(c);
	}

	private static int showPlayer(CommandContext<FabricClientCommandSource> c) {
		FemboyCommands.Target t = target(c);
		if (t == null) return 0;
		return msg(c, hl(t.name()).append(Text.literal(": głośność " + Voices.percent(t.uuid()) + "%"
			+ (Voices.muted(t.uuid()) ? ", WYCISZONY" : "")).formatted(Formatting.WHITE)));
	}

	private static int list(CommandContext<FabricClientCommandSource> c) {
		int n = 0;
		for (Map.Entry<String, FemboyConfig.PlayerSetting> e : cfg().players.entrySet()) {
			FemboyConfig.PlayerSetting ps = e.getValue();
			if (!ps.voiceMuted && (ps.voiceVolume == null || ps.voiceVolume == 100)) continue;
			UUID id;
			try {
				id = UUID.fromString(e.getKey());
			} catch (IllegalArgumentException ex) {
				continue;
			}
			msg(c, hl(ps.name == null ? e.getKey() : ps.name).append(Text.literal(": " + Voices.percent(id) + "%"
				+ (ps.voiceMuted ? ", wyciszony" : "")).formatted(Formatting.WHITE)));
			n++;
		}
		if (n == 0) msg(c, Text.literal("Nikogo nie przyciszyłeś ani nie wyciszyłeś"));
		return 1;
	}
}
