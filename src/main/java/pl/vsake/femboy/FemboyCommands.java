package pl.vsake.femboy;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.command.CommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import pl.vsake.femboy.fx.Bonds;
import pl.vsake.femboy.fx.Boop;
import pl.vsake.femboy.fx.Clicker;
import pl.vsake.femboy.fx.Pose;
import pl.vsake.femboy.fx.Poses;
import pl.vsake.femboy.fx.Toys;
import pl.vsake.femboy.fx.Trust;
import pl.vsake.femboy.fx.Emote;
import pl.vsake.femboy.fx.Leash;
import pl.vsake.femboy.fx.Reactions;
import pl.vsake.femboy.acc.Accessories;
import pl.vsake.femboy.gen.Style;
import pl.vsake.femboy.pat.PatPat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** /femboy on|off|toggle|status|mode|source|reroll|set|reset|self|tab|layers|pat|patsound|acc|sound|list|export|folder|reload */
public final class FemboyCommands {
	private FemboyCommands() {
	}

	static final SuggestionProvider<FabricClientCommandSource> PLAYERS =
		(ctx, builder) -> CommandSource.suggestMatching(playerNames(), builder);

	public static void register(CommandDispatcher<FabricClientCommandSource> d) {
		d.register(literal("femboy")
			.executes(FemboyCommands::help)
			.then(literal("help").executes(FemboyCommands::help))
			.then(literal("on").executes(c -> setEnabled(c, true)))
			.then(literal("off").executes(c -> setEnabled(c, false)))
			.then(literal("toggle").executes(c -> setEnabled(c, !cfg().enabled)))
			.then(literal("status").executes(FemboyCommands::status))
			.then(literal("mode")
				.executes(c -> msg(c, Text.literal("Tryb: ").append(hl(modeName(cfg().mode)))))
				.then(literal("femboy").executes(c -> mode(c, "femboy")))
				.then(literal("egirl").executes(c -> mode(c, "egirl")))
				.then(literal("catboy").executes(c -> mode(c, "catboy")))
				.then(literal("mix").executes(c -> mode(c, "mix"))))
			.then(literal("source")
				.executes(c -> msg(c, Text.literal("Źródło skinów: ").append(hl(sourceName(cfg().source)))))
				.then(literal("skins").executes(c -> source(c, "skins")))
				.then(literal("generated").executes(c -> source(c, "generated")))
				.then(literal("mix").executes(c -> source(c, "mix"))))
			.then(literal("reroll")
				.executes(FemboyCommands::rerollAll)
				.then(argument("gracz", StringArgumentType.word()).suggests(PLAYERS).executes(FemboyCommands::rerollOne)))
			.then(literal("set")
				.then(argument("gracz", StringArgumentType.word()).suggests(PLAYERS)
					.then(literal("femboy").executes(c -> setPlayer(c, "femboy")))
					.then(literal("egirl").executes(c -> setPlayer(c, "egirl")))
					.then(literal("catboy").executes(c -> setPlayer(c, "catboy")))
					.then(literal("auto").executes(c -> setPlayer(c, null)))
					.then(literal("off").executes(c -> setPlayer(c, "off")))))
			.then(literal("reset")
				.executes(FemboyCommands::resetAll)
				.then(argument("gracz", StringArgumentType.word()).suggests(PLAYERS).executes(FemboyCommands::resetOne)))
			.then(flag("self", "Zmieniaj też mój skin", () -> cfg().self, v -> cfg().self = v))
			.then(flag("tab", "Główki na liście graczy (TAB)", () -> cfg().tab, v -> cfg().tab = v))
			.then(flag("layers", "Wymuszanie warstw skina (włosy 3D, spódniczka)", () -> cfg().layers, v -> cfg().layers = v))
			.then(flag("pat", "Głaskanie (pat pat)", () -> cfg().pat, v -> cfg().pat = v))
			.then(flag("patsound", "Piszczenie przy głaskaniu", () -> cfg().patSound, v -> cfg().patSound = v))
			.then(flag("bells", "Dzwoneczki przy chodzeniu", () -> cfg().bells, v -> cfg().bells = v))
			.then(flag("purr", "Mruczenie głaskanego kota", () -> cfg().patPurr, v -> cfg().patPurr = v))
			.then(flag("hearts", "Serduszka nad głaskanym", () -> cfg().patHearts, v -> cfg().patHearts = v))
			.then(flag("react", "Uszy i ogon reagują na odgłosy i obrażenia", () -> cfg().react, v -> cfg().react = v))
			.then(flag("clicker", "Clicker (słyszę i obracam się do klikającego)", () -> cfg().clicker, v -> cfg().clicker = v)
				.then(literal("click").executes(c -> Clicker.use() ? 1 : 0)))
			.then(flag("leashpull", "Daję się ciągnąć na smyczy", () -> cfg().leashPull, v -> cfg().leashPull = v))
			.then(literal("leash")
				.executes(c -> msg(c, Text.literal(Leash.leading() == null ? "Nikogo nie prowadzisz na smyczy (klawisz " + Keys.name(Leash.key()) + ", celując w gracza)"
					: "Prowadzisz na smyczy: " + Leash.leadingName())))
				.then(literal("off").executes(c -> {
					Leash.release();
					return 1;
				}))
				.then(argument("gracz", StringArgumentType.word()).suggests(PLAYERS).executes(c -> {
					Target t = target(c);
					if (t == null) return 0;
					Leash.take(t.uuid(), t.name());
					return 1;
				})))
			.then(emotes())
			.then(poses())
			.then(pair("boop", (char) 0))
			.then(pair("hands", Bonds.HANDS))
			.then(pair("carry", Bonds.CARRY))
			.then(pair("piggy", Bonds.PIGGY))
			.then(literal("yarn").executes(c -> Toys.throwYarn() ? 1 : 0))
			.then(wardrobe())
			.then(flag("clickerpicked", "Clicker tylko od wybranych osób", () -> cfg().clickerPicked, v -> cfg().clickerPicked = v))
			.then(flag("leashpicked", "Smycz tylko od wybranych osób", () -> cfg().leashPicked, v -> cfg().leashPicked = v))
			.then(flag("leashsee", "Widzę smycz na sobie", () -> cfg().leashSeeOwn, v -> cfg().leashSeeOwn = v))
			.then(flag("yarnfollow", "Gonię cudze kłębki włóczki", () -> cfg().yarnFollow, v -> cfg().yarnFollow = v))
			.then(flag("laserfollow", "Idę za kropką wskaźnika laserowego", () -> cfg().laserFollow, v -> cfg().laserFollow = v))
			.then(flag("laserpicked", "Wskaźnik tylko od wybranych osób", () -> cfg().laserPicked, v -> cfg().laserPicked = v))
			.then(flag("toys", "Widzę cudze zabawki (laser, kłębek)", () -> cfg().toys, v -> cfg().toys = v))
			.then(flag("pawprints", "Ślady łapek na śniegu i piasku", () -> cfg().pawPrints, v -> cfg().pawPrints = v))
			.then(flag("blink", "Mruganie", () -> cfg().blink, v -> cfg().blink = v))
			.then(flag("autopose", "Bochenek i przeciąganie same z siebie", () -> cfg().autoPoses, v -> cfg().autoPoses = v))
			.then(trust())
			.then(AccCommands.node())
			.then(VoiceCommands.node())
			.then(literal("list").executes(FemboyCommands::list))
			.then(literal("export")
				.executes(c -> export(c, null))
				.then(argument("gracz", StringArgumentType.word()).suggests(PLAYERS)
					.executes(c -> export(c, StringArgumentType.getString(c, "gracz")))))
			.then(literal("gui").executes(FemboyCommands::gui))
			.then(literal("folder").executes(FemboyCommands::folder))
			.then(literal("reload").executes(FemboyCommands::reload)));
	}

	/** /femboy emote <id> – emotka nad głową (jak klawisz). */
	private static LiteralArgumentBuilder<FabricClientCommandSource> emotes() {
		LiteralArgumentBuilder<FabricClientCommandSource> n = literal("emote");
		for (Emote e : Emote.values()) n.then(literal(e.id).executes(c -> Reactions.emote(e) ? 1 : 0));
		return n;
	}

	/** /femboy pose <id> | stop – animacja ciała (jak klawisz). */
	private static LiteralArgumentBuilder<FabricClientCommandSource> poses() {
		LiteralArgumentBuilder<FabricClientCommandSource> n = literal("pose");
		for (Pose p : Pose.values()) n.then(literal(p.id).executes(c -> Poses.play(p) ? 1 : 0));
		n.then(literal("stop").executes(c -> {
			Poses.stopMine();
			return 1;
		}));
		return n;
	}

	/** /femboy boop|hands|carry|piggy <gracz> (albo off) – rzeczy we dwoje. kind 0 = boop. */
	private static LiteralArgumentBuilder<FabricClientCommandSource> pair(String name, char kind) {
		LiteralArgumentBuilder<FabricClientCommandSource> n = literal(name);
		if (kind != 0) n.then(literal("off").executes(c -> {
			Bonds.stop();
			return 1;
		}));
		return n.then(argument("gracz", StringArgumentType.word()).suggests(PLAYERS).executes(c -> {
			Target t = target(c);
			if (t == null) return 0;
			if (kind == 0) return Boop.use(t.uuid()) ? 1 : 0;
			Bonds.start(kind, t.uuid(), t.name());
			return 1;
		}));
	}

	/** /femboy wardrobe [save <nazwa> | wear <nazwa> | remove <nazwa> | next] – szafa ze strojami. */
	private static LiteralArgumentBuilder<FabricClientCommandSource> wardrobe() {
		com.mojang.brigadier.suggestion.SuggestionProvider<FabricClientCommandSource> names = (c, b) -> {
			for (FemboyConfig.Saved sv : Wardrobe.all()) if (sv.name.indexOf(' ') < 0) b.suggest(sv.name);
			return b.buildFuture();
		};
		return literal("wardrobe")
			.executes(c -> {
				StringBuilder sb = new StringBuilder();
				for (FemboyConfig.Saved sv : Wardrobe.all()) sb.append(sb.isEmpty() ? "" : ", ").append(sv.name);
				return msg(c, Text.literal("Szafa (" + Wardrobe.all().size() + "/" + Wardrobe.MAX + "): ").append(hl(sb.isEmpty() ? "pusta" : sb.toString())));
			})
			.then(literal("next").executes(c -> {
				Wardrobe.next();
				return 1;
			}))
			.then(literal("save").then(argument("nazwa", StringArgumentType.greedyString()).executes(c -> {
				MinecraftClient mc = MinecraftClient.getInstance();
				if (mc.player == null) return 0;
				UUID me = mc.player.getUuid();
				String nm = mc.player.getGameProfile().name();
				FemboyConfig.PlayerSetting ps = cfg().player(me);
				pl.vsake.femboy.acc.Outfit o = cfg().accMe != null ? cfg().accMe.copy() : pl.vsake.femboy.acc.Accessories.editable(me, nm);
				int i = Wardrobe.save(StringArgumentType.getString(c, "nazwa"), o, ps == null ? null : ps.skin);
				return msg(c, i < 0 ? Text.literal("Szafa jest pełna").formatted(Formatting.RED) : Text.literal("Zapisano strój: ").append(hl(Wardrobe.all().get(i).name)));
			})))
			.then(literal("wear").then(argument("nazwa", StringArgumentType.greedyString()).suggests(names).executes(c -> {
				FemboyConfig.Saved sv = Wardrobe.byName(StringArgumentType.getString(c, "nazwa"));
				if (sv == null) return msg(c, Text.literal("Nie ma takiego stroju w szafie").formatted(Formatting.RED));
				return Wardrobe.wear(Wardrobe.all().indexOf(sv)) ? 1 : 0;
			})))
			.then(literal("remove").then(argument("nazwa", StringArgumentType.greedyString()).suggests(names).executes(c -> {
				FemboyConfig.Saved sv = Wardrobe.byName(StringArgumentType.getString(c, "nazwa"));
				if (sv == null) return msg(c, Text.literal("Nie ma takiego stroju w szafie").formatted(Formatting.RED));
				Wardrobe.remove(Wardrobe.all().indexOf(sv));
				return msg(c, Text.literal("Usunięto strój: ").append(hl(sv.name)));
			})));
	}

	/** /femboy trust clicker|leash|laser|carry <gracz> on|off – na kogo reaguję. */
	private static LiteralArgumentBuilder<FabricClientCommandSource> trust() {
		LiteralArgumentBuilder<FabricClientCommandSource> n = literal("trust");
		for (Trust.What w : Trust.What.values()) {
			String id = w.name().toLowerCase(java.util.Locale.ROOT);
			n.then(literal(id).then(argument("gracz", StringArgumentType.word()).suggests(PLAYERS)
				.executes(c -> {
					Target t = target(c);
					if (t == null) return 0;
					return msg(c, Text.literal(id + " – " + t.name() + ": ").append(onOff(Trust.picked(w, t.uuid()))));
				})
				.then(literal("on").executes(c -> setTrust(c, w, true)))
				.then(literal("off").executes(c -> setTrust(c, w, false)))));
		}
		return n;
	}

	private static int setTrust(CommandContext<FabricClientCommandSource> c, Trust.What w, boolean on) {
		Target t = target(c);
		if (t == null) return 0;
		Trust.setPicked(w, t.uuid(), t.name(), on);
		return msg(c, Text.literal(w.name().toLowerCase(java.util.Locale.ROOT) + " – " + t.name() + ": ").append(onOff(on)));
	}

	// ======================== komendy ========================

	private static int help(CommandContext<FabricClientCommandSource> c) {
		FabricClientCommandSource s = c.getSource();
		s.sendFeedback(prefix().append(Text.literal("Femboy Skins – ").formatted(Formatting.WHITE))
			.append(cfg().enabled ? Text.literal("WŁĄCZONY").formatted(Formatting.GREEN) : Text.literal("WYŁĄCZONY").formatted(Formatting.RED))
			.append(Text.literal(", tryb: ").formatted(Formatting.GRAY)).append(hl(modeName(cfg().mode))));
		line(s, "/femboy on | off | toggle", "włącz / wyłącz");
		line(s, "/femboy mode femboy|egirl|catboy|mix", "kto jaki skin dostaje");
		line(s, "/femboy source skins|generated|mix", "pobrane skiny / generator / oba");
		line(s, "/femboy reroll [gracz]", "wylosuj nowe wyglądy");
		line(s, "/femboy set <gracz> femboy|egirl|catboy|auto|off", "wybór dla jednej osoby");
		line(s, "/femboy reset [gracz]", "usuń ustawienia graczy");
		line(s, "/femboy self | tab | layers on|off", "ty / lista TAB / warstwy 3D");
		line(s, "/femboy pat | patsound on|off", "głaskanie: trzymaj " + PatPat.keyName() + ", celując w kogoś / piszczenie");
		line(s, "/femboy acc", "dodatki: kocie uszy, ogony, obroże, dzwoneczki… (" + (cfg().acc ? "włączone" : "wyłączone") + ")");
		line(s, "/femboy sound", "odgłosy pod klawiszami: miau, hau… + głośność / wyciszanie graczy");
		line(s, "/femboy emote <" + String.join("|", Emote.ids()) + ">", "emotka nad głową (klawisze w oknie moda)");
		line(s, "/femboy clicker on|off | click", "clicker: kto ma włączony, obraca się do klikającego");
		line(s, "/femboy leash <gracz> | off, leashpull on|off", "smycz / czy dajesz się ciągnąć");
		line(s, "/femboy bells | purr | hearts | react on|off", "dzwoneczki, mruczenie, serduszka, reakcje uszu i ogona");
		line(s, "/femboy pose <" + String.join("|", Pose.ids()) + "|stop>", "animacje ciała (klawisze w oknie moda)");
		line(s, "/femboy boop | hands | carry | piggy <gracz>", "boop w nosek, za ręce, noszenie (za zgodą drugiej osoby)");
		line(s, "/femboy yarn, laserfollow | toys | pawprints | blink | autopose on|off", "kłębek, laser, ślady łapek, mruganie, bochenek");
		line(s, "/femboy trust clicker|leash|laser|carry <gracz> on|off", "na kogo reagujesz (+ clickerpicked / leashpicked / laserpicked)");
		line(s, "/femboy wardrobe [save|wear|remove <nazwa> | next]", "szafa ze strojami");
		line(s, "/femboy gui", "okno moda: wygląd, odgłosy, zabawa i wszystkie klawisze (klawisz G)");
		line(s, "/femboy list | status", "kto co ma / ustawienia");
		line(s, "/femboy export [gracz]", "zapisz skin do PNG");
		line(s, "/femboy folder | reload", "dodatkowe własne skiny PNG");
		return 1;
	}

	private static int setEnabled(CommandContext<FabricClientCommandSource> c, boolean on) {
		cfg().enabled = on;
		cfg().save();
		return msg(c, on ? Text.literal("Włączony – wszyscy są teraz słodcy ").append(Text.literal("♥").formatted(Formatting.LIGHT_PURPLE))
			: Text.literal("Wyłączony – wracają oryginalne skiny"));
	}

	private static int status(CommandContext<FabricClientCommandSource> c) {
		FemboyConfig f = cfg();
		msg(c, Text.literal("Mod: ").append(onOff(f.enabled))
			.append(Text.literal(" | tryb: ").formatted(Formatting.GRAY)).append(hl(modeName(f.mode)))
			.append(Text.literal(" | źródło: ").formatted(Formatting.GRAY)).append(hl(sourceName(f.source))));
		msg(c, Text.literal("Ja: ").append(onOff(f.self))
			.append(Text.literal(" | TAB: ").formatted(Formatting.GRAY)).append(onOff(f.tab))
			.append(Text.literal(" | warstwy: ").formatted(Formatting.GRAY)).append(onOff(f.layers)));
		msg(c, Text.literal("Głaskanie: ").append(onOff(f.pat))
			.append(Text.literal(" (klawisz " + PatPat.keyName() + ") | piszczenie: ").formatted(Formatting.GRAY)).append(onOff(f.patSound)));
		msg(c, Text.literal("Odgłosy: ").append(onOff(f.voice))
			.append(Text.literal(" | głośność: ").formatted(Formatting.GRAY)).append(hl(f.voiceVolume + "%")));
		msg(c, Text.literal("Wymiana z innymi graczami z modem: ")
			.append(pl.vsake.femboy.net.Relay.connected() > 0
				? Text.literal("połączono (" + pl.vsake.femboy.net.Relay.connected() + "/" + pl.vsake.femboy.net.Relay.brokers() + ")").formatted(Formatting.GREEN)
				: Text.literal("łączenie…").formatted(Formatting.YELLOW))
			.append(Text.literal(" | serwer: ").formatted(Formatting.GRAY))
			.append(pl.vsake.femboy.net.NetClient.available() ? Text.literal("ma moda").formatted(Formatting.GREEN)
				: Text.literal("bez moda").formatted(Formatting.GRAY)));
		msg(c, Text.literal("Dodatki: ").append(onOff(f.acc))
			.append(Text.literal(" | tryb: ").formatted(Formatting.GRAY)).append(hl(AccCommands.modeName(f.accMode)))
			.append(Text.literal(" | ja: ").formatted(Formatting.GRAY))
			.append(f.accMe == null ? Text.literal("według trybu").formatted(Formatting.GRAY) : AccCommands.describe(f.accMe)));
		msg(c, Text.literal("Skiny: " + poolSummary()));
		return msg(c, Text.literal("Gracze z ustawieniami: " + f.players.size()));
	}

	private static int mode(CommandContext<FabricClientCommandSource> c, String mode) {
		cfg().mode = mode;
		cfg().save();
		SkinManager.invalidateAll();
		return msg(c, Text.literal("Tryb: ").append(hl(modeName(mode))));
	}

	private static int source(CommandContext<FabricClientCommandSource> c, String source) {
		cfg().source = source;
		cfg().save();
		SkinManager.invalidateAll();
		return msg(c, Text.literal("Źródło skinów: ").append(hl(sourceName(source))));
	}

	private static int rerollAll(CommandContext<FabricClientCommandSource> c) {
		long s;
		do s = new Random().nextLong(); while (s == cfg().seed);
		cfg().seed = s;
		cfg().save();
		SkinManager.invalidateAll();
		return msg(c, Text.literal("Nowe losowanie – wszyscy mają nowe skiny"));
	}

	private static int rerollOne(CommandContext<FabricClientCommandSource> c) {
		Target t = target(c);
		if (t == null) return 0;
		FemboyConfig.PlayerSetting ps = cfg().playerOrCreate(t.uuid, t.name);
		ps.salt++;
		cfg().save();
		SkinManager.invalidate(t.uuid);
		return msg(c, Text.literal("Nowy wygląd dla ").append(hl(t.name)));
	}

	private static int setPlayer(CommandContext<FabricClientCommandSource> c, String style) {
		Target t = target(c);
		if (t == null) return 0;
		cfg().playerOrCreate(t.uuid, t.name).style = style;
		cfg().tidy(t.uuid);
		cfg().save();
		SkinManager.invalidate(t.uuid);
		String what = style == null ? "według trybu (" + modeName(cfg().mode) + ")"
			: switch (style) {
				case "off" -> "oryginalny skin";
				case "egirl" -> "e-girl";
				case "catboy" -> "catboy";
				default -> "femboy";
			};
		return msg(c, hl(t.name).append(Text.literal(": " + what).formatted(Formatting.WHITE)));
	}

	private static int resetAll(CommandContext<FabricClientCommandSource> c) {
		int n = cfg().players.size();
		cfg().players.clear();
		cfg().save();
		SkinManager.invalidateAll();
		Accessories.changed();
		return msg(c, Text.literal("Usunięto ustawienia graczy (" + n + ")"));
	}

	private static int resetOne(CommandContext<FabricClientCommandSource> c) {
		Target t = target(c);
		if (t == null) return 0;
		cfg().players.remove(t.uuid.toString());
		cfg().save();
		SkinManager.invalidate(t.uuid);
		Accessories.changed();
		return msg(c, Text.literal("Usunięto ustawienia: ").append(hl(t.name)));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> flag(String name, String label, BooleanSupplier get, Consumer<Boolean> set) {
		return literal(name)
			.executes(c -> msg(c, Text.literal(label + ": ").append(onOff(get.getAsBoolean()))))
			.then(literal("on").executes(c -> {
				set.accept(true);
				cfg().save();
				return msg(c, Text.literal(label + ": ").append(onOff(true)));
			}))
			.then(literal("off").executes(c -> {
				set.accept(false);
				cfg().save();
				return msg(c, Text.literal(label + ": ").append(onOff(false)));
			}));
	}

	private static int list(CommandContext<FabricClientCommandSource> c) {
		Map<UUID, String> players = onlinePlayers();
		if (players.isEmpty()) return err(c, "Nie ma żadnych graczy (wejdź na świat albo serwer)");
		msg(c, Text.literal("Gracze (" + players.size() + "):"));
		for (Map.Entry<UUID, String> e : players.entrySet()) {
			String d = SkinManager.describe(e.getKey(), e.getValue());
			MutableText line = Text.literal(" • ").formatted(Formatting.DARK_GRAY).append(Text.literal(e.getValue()).formatted(Formatting.WHITE))
				.append(Text.literal(" – ").formatted(Formatting.DARK_GRAY))
				.append(d == null ? Text.literal("oryginał").formatted(Formatting.GRAY) : Text.literal(d).formatted(Formatting.LIGHT_PURPLE));
			String acc = Accessories.describe(e.getKey(), e.getValue());
			if (acc != null) line.append(Text.literal(" + ").formatted(Formatting.DARK_GRAY)).append(Text.literal(acc).formatted(Formatting.AQUA));
			if (SkinManager.isSelf(e.getKey(), e.getValue())) line.append(Text.literal(" (ty)").formatted(Formatting.GRAY));
			c.getSource().sendFeedback(line);
		}
		return players.size();
	}

	private static int export(CommandContext<FabricClientCommandSource> c, String name) {
		Target t;
		if (name == null) {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.player == null) return err(c, "Podaj gracza: /femboy export <gracz>");
			t = new Target(mc.player.getUuid(), mc.player.getGameProfile().name());
		} else {
			t = resolve(name);
			if (t == null) return err(c, "Nie znam gracza " + name);
		}
		try {
			Path out = SkinManager.export(t.uuid, t.name);
			return msg(c, Text.literal("Zapisano skin ").append(hl(t.name)).append(Text.literal(": ").formatted(Formatting.WHITE)).append(fileLink(out)));
		} catch (Exception e) {
			FemboyMod.LOG.warn("Eksport skina {} nie wyszedł", t.name, e);
			return err(c, "Nie udało się zapisać: " + e.getMessage());
		}
	}

	private static int gui(CommandContext<FabricClientCommandSource> c) {
		MinecraftClient mc = MinecraftClient.getInstance();
		mc.execute(pl.vsake.femboy.gui.FemboyScreen::open);
		return 1;
	}

	private static int folder(CommandContext<FabricClientCommandSource> c) {
		msg(c, Text.literal("Twoje skiny (PNG) wrzucaj do: ").append(fileLink(SkinManager.skinsDir())));
		return msg(c, Text.literal("Podfolder femboy/, egirl/ albo catboy/, potem /femboy reload. Skiny z laby.net są już wbudowane w mod.").formatted(Formatting.GRAY));
	}

	private static int reload(CommandContext<FabricClientCommandSource> c) {
		int n = SkinManager.reloadPool();
		Accessories.forgetSkins();
		return msg(c, Text.literal("Wczytano skiny: " + poolSummary()
			+ (n == 0 ? " (brak – używam generatora)" : "")));
	}

	/** "femboy 150 (wbudowane 150, twoje 0), e-girl …, catboy …" */
	private static String poolSummary() {
		StringBuilder sb = new StringBuilder();
		for (Style st : Style.values()) {
			int all = SkinManager.poolCount(st), inJar = SkinManager.bundledCount(st);
			if (!sb.isEmpty()) sb.append(", ");
			sb.append(st.label).append(' ').append(all)
				.append(" (wbudowane ").append(inJar).append(", twoje ").append(all - inJar).append(')');
		}
		return sb.toString();
	}

	// ======================== gracze ========================

	record Target(UUID uuid, String name) {
	}

	static Target target(CommandContext<FabricClientCommandSource> c) {
		String name = StringArgumentType.getString(c, "gracz");
		Target t = resolve(name);
		if (t == null) err(c, "Nie znam gracza " + name + " (musi być na serwerze albo w ustawieniach)");
		return t;
	}

	static Target resolve(String name) {
		for (Map.Entry<UUID, String> e : onlinePlayers().entrySet())
			if (e.getValue().equalsIgnoreCase(name)) return new Target(e.getKey(), e.getValue());
		for (Map.Entry<String, FemboyConfig.PlayerSetting> e : cfg().players.entrySet())
			if (name.equalsIgnoreCase(e.getValue().name)) {
				try {
					return new Target(UUID.fromString(e.getKey()), e.getValue().name);
				} catch (IllegalArgumentException ignored) {
				}
			}
		return null;
	}

	/** Gracze z listy TAB + gracze widoczni w świecie (np. NPC spoza listy). */
	static Map<UUID, String> onlinePlayers() {
		Map<UUID, String> out = new LinkedHashMap<>();
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.getNetworkHandler() != null)
			for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) out.put(e.getProfile().id(), e.getProfile().name());
		if (mc.world != null)
			for (AbstractClientPlayerEntity p : mc.world.getPlayers()) out.putIfAbsent(p.getUuid(), p.getGameProfile().name());
		return out;
	}

	static List<String> playerNames() {
		TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		names.addAll(onlinePlayers().values());
		for (FemboyConfig.PlayerSetting ps : cfg().players.values()) if (ps.name != null) names.add(ps.name);
		return new ArrayList<>(names);
	}

	// ======================== tekst ========================

	static FemboyConfig cfg() {
		return FemboyMod.CONFIG;
	}

	private static String modeName(String mode) {
		return switch (mode) {
			case "femboy" -> "femboy";
			case "egirl" -> "e-girl";
			case "catboy" -> "catboy";
			default -> "mix (femboy + e-girl + catboy)";
		};
	}

	private static String sourceName(String source) {
		return switch (source) {
			case "generated" -> "generator";
			case "mix" -> "mix (pliki + generator)";
			default -> "pobrane skiny (laby.net + twoje)";
		};
	}

	static MutableText prefix() {
		return Text.literal("♥ ").formatted(Formatting.LIGHT_PURPLE)
			.append(Text.literal("femboy").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD))
			.append(Text.literal(" » ").formatted(Formatting.DARK_GRAY));
	}

	static MutableText hl(String s) {
		return Text.literal(s).formatted(Formatting.LIGHT_PURPLE);
	}

	static MutableText onOff(boolean on) {
		return on ? Text.literal("tak").formatted(Formatting.GREEN) : Text.literal("nie").formatted(Formatting.RED);
	}

	private static MutableText fileLink(Path p) {
		String s = p.toAbsolutePath().toString();
		return Text.literal(s).styled(st -> st.withColor(Formatting.AQUA).withUnderline(true)
			.withClickEvent(new ClickEvent.OpenFile(s))
			.withHoverEvent(new HoverEvent.ShowText(Text.literal("Kliknij, żeby otworzyć"))));
	}

	static void line(FabricClientCommandSource s, String cmd, String desc) {
		s.sendFeedback(Text.literal(" " + cmd).formatted(Formatting.LIGHT_PURPLE)
			.append(Text.literal(" – " + desc).formatted(Formatting.GRAY)));
	}

	static int msg(CommandContext<FabricClientCommandSource> c, MutableText t) {
		// biały jako kolor domyślny, ale wyróżnienia (np. nick) zachowują swój
		c.getSource().sendFeedback(prefix().append(Text.literal("").formatted(Formatting.WHITE).append(t)));
		return 1;
	}

	static int err(CommandContext<FabricClientCommandSource> c, String s) {
		c.getSource().sendError(prefix().append(Text.literal(s).formatted(Formatting.RED)));
		return 0;
	}
}
