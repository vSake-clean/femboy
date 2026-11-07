package pl.vsake.femboy;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import pl.vsake.femboy.acc.Accessories;
import pl.vsake.femboy.acc.Item;
import pl.vsake.femboy.acc.Kit;
import pl.vsake.femboy.acc.Look;
import pl.vsake.femboy.acc.Outfit;
import pl.vsake.femboy.acc.Palette;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static pl.vsake.femboy.FemboyCommands.PLAYERS;
import static pl.vsake.femboy.FemboyCommands.cfg;
import static pl.vsake.femboy.FemboyCommands.err;
import static pl.vsake.femboy.FemboyCommands.hl;
import static pl.vsake.femboy.FemboyCommands.line;
import static pl.vsake.femboy.FemboyCommands.msg;
import static pl.vsake.femboy.FemboyCommands.prefix;
import static pl.vsake.femboy.FemboyCommands.target;

/**
 * /femboy acc … – dodatki 3D: uszy, ogony, obroże/chokery, ozdoby głowy, okulary/maseczka, skrzydła,
 * ocieplacze, zakolanówki, łapki, spódniczka.
 * Każdy strój (mój / wszystkich / jednego gracza) zmienia się tymi samymi podkomendami.
 */
final class AccCommands {
	private AccCommands() {
	}

	private static final SuggestionProvider<FabricClientCommandSource> COLORS = (ctx, builder) -> {
		List<String> l = new ArrayList<>(Palette.ids());
		l.add("auto");
		return CommandSource.suggestMatching(l, builder);
	};

	/** Czyj strój zmieniamy: mój, wspólny (tryb same) albo jednego gracza. */
	private interface Scope {
		MutableText who();

		/** null = według trybu */
		Outfit get();

		void set(Outfit o);

		/** To, co ma teraz – punkt wyjścia do zmiany jednego dodatku. */
		Outfit current();

		boolean canAuto();

		/** Kolor włosów z ostatnio widzianego skina albo -1. */
		int hair();
	}

	static LiteralArgumentBuilder<FabricClientCommandSource> node() {
		LiteralArgumentBuilder<FabricClientCommandSource> kits = literal("kits");
		kits.executes(AccCommands::kits);
		for (Kit k : Kit.values())
			kits.then(literal(k.id)
				.then(literal("on").executes(c -> kit(c, k, true)))
				.then(literal("off").executes(c -> kit(c, k, false))));
		return literal("acc")
			.executes(AccCommands::help)
			.then(literal("help").executes(AccCommands::help))
			.then(literal("on").executes(c -> setOn(c, true)))
			.then(literal("off").executes(c -> setOn(c, false)))
			.then(literal("mode")
				.executes(c -> msg(c, Text.literal("Tryb dodatków: ").append(hl(modeName(cfg().accMode)))))
				.then(literal("random").executes(c -> mode(c, "random")))
				.then(literal("same").executes(c -> mode(c, "same")))
				.then(literal("me").executes(c -> mode(c, "me"))))
			.then(outfit(literal("me"), AccCommands::me))
			.then(outfit(literal("all"), c -> ALL))
			.then(literal("player")
				.then(outfit(argument("gracz", StringArgumentType.word()).suggests(PLAYERS), AccCommands::player)))
			.then(kits)
			.then(literal("reroll")
				.executes(AccCommands::rerollAll)
				.then(argument("gracz", StringArgumentType.word()).suggests(PLAYERS).executes(AccCommands::rerollOne)))
			.then(literal("items").executes(AccCommands::items));
	}

	/** Wspólne podkomendy stroju: zestaw, random, none, auto, miejsce+dodatek, fur, color. */
	private static <T extends ArgumentBuilder<FabricClientCommandSource, T>> T outfit(
		T node, Function<CommandContext<FabricClientCommandSource>, Scope> scope) {
		node.executes(c -> show(c, scope.apply(c)));
		for (Kit k : Kit.values()) node.then(literal(k.id).executes(c -> setKit(c, scope.apply(c), k)));
		node.then(literal("random").executes(c -> randomize(c, scope.apply(c))));
		node.then(literal("none").executes(c -> replace(c, scope.apply(c), new Outfit())));
		node.then(literal("auto").executes(c -> auto(c, scope.apply(c))));
		for (Item.Slot slot : Item.Slot.values()) {
			LiteralArgumentBuilder<FabricClientCommandSource> s = literal(slot.id);
			if (slot.toggle) {
				Item only = Item.of(slot).get(0);
				s.then(literal("on").executes(c -> setItem(c, scope.apply(c), slot, only)));
				s.then(literal("off").executes(c -> setItem(c, scope.apply(c), slot, null)));
			} else {
				for (Item it : Item.of(slot)) s.then(literal(it.id).executes(c -> setItem(c, scope.apply(c), slot, it)));
				s.then(literal("none").executes(c -> setItem(c, scope.apply(c), slot, null)));
			}
			node.then(s);
		}
		node.then(literal("fur").then(argument("kolor", StringArgumentType.greedyString()).suggests(COLORS)
			.executes(c -> setColor(c, scope.apply(c), true, StringArgumentType.getString(c, "kolor")))));
		node.then(literal("color").then(argument("kolor", StringArgumentType.greedyString()).suggests(COLORS)
			.executes(c -> setColor(c, scope.apply(c), false, StringArgumentType.getString(c, "kolor")))));
		return node;
	}

	// ======================== czyj strój ========================

	private static final Scope ALL = new Scope() {
		public MutableText who() {
			return Text.literal("Strój dla wszystkich");
		}

		public Outfit get() {
			return cfg().accAll;
		}

		public void set(Outfit o) {
			cfg().accAll = o == null ? Kit.CATBOY.outfit() : o;
		}

		public Outfit current() {
			return cfg().accAll.copy();
		}

		public boolean canAuto() {
			return false;
		}

		public int hair() {
			return -1;
		}
	};

	private static Scope me(CommandContext<FabricClientCommandSource> c) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) {
			err(c, "Najpierw wejdź na świat albo serwer");
			return null;
		}
		UUID id = mc.player.getUuid();
		String name = mc.player.getGameProfile().name();
		return new Scope() {
			public MutableText who() {
				return Text.literal("Twój strój");
			}

			public Outfit get() {
				return cfg().accMe;
			}

			public void set(Outfit o) {
				cfg().accMe = o;
			}

			public Outfit current() {
				return cfg().accMe != null ? cfg().accMe.copy() : Accessories.editable(id, name);
			}

			public boolean canAuto() {
				return true;
			}

			public int hair() {
				return Accessories.knownHair(id);
			}
		};
	}

	private static Scope player(CommandContext<FabricClientCommandSource> c) {
		FemboyCommands.Target t = target(c);
		if (t == null) return null;
		if (SkinManager.isSelf(t.uuid(), t.name())) return me(c); // ja = mój strój, jedno miejsce
		return new Scope() {
			public MutableText who() {
				return Text.literal("Strój gracza ").append(hl(t.name()));
			}

			public Outfit get() {
				FemboyConfig.PlayerSetting ps = cfg().player(t.uuid());
				return ps == null ? null : ps.outfit;
			}

			public void set(Outfit o) {
				if (o != null) {
					cfg().playerOrCreate(t.uuid(), t.name()).outfit = o;
					return;
				}
				FemboyConfig.PlayerSetting ps = cfg().player(t.uuid());
				if (ps != null) ps.outfit = null;
				cfg().tidy(t.uuid());
			}

			public Outfit current() {
				Outfit o = get();
				return o != null ? o.copy() : Accessories.editable(t.uuid(), t.name());
			}

			public boolean canAuto() {
				return true;
			}

			public int hair() {
				return Accessories.knownHair(t.uuid());
			}
		};
	}

	// ======================== zmiany stroju ========================

	private static int show(CommandContext<FabricClientCommandSource> c, Scope s) {
		if (s == null) return 0;
		Outfit o = s.get();
		if (o == null)
			return msg(c, s.who().append(Text.literal(": według trybu (" + modeName(cfg().accMode) + "), teraz: ").formatted(Formatting.WHITE))
				.append(describe(s.current())));
		return msg(c, s.who().append(Text.literal(": ").formatted(Formatting.WHITE)).append(describe(o)));
	}

	private static int replace(CommandContext<FabricClientCommandSource> c, Scope s, Outfit o) {
		if (s == null) return 0;
		s.set(o);
		saved();
		return show(c, s);
	}

	private static int setKit(CommandContext<FabricClientCommandSource> c, Scope s, Kit k) {
		if (s == null) return 0;
		s.set(k.outfit());
		saved();
		return msg(c, s.who().append(Text.literal(": ").formatted(Formatting.WHITE)).append(hl(k.label))
			.append(Text.literal(" – ").formatted(Formatting.DARK_GRAY)).append(describe(k.outfit())));
	}

	private static int randomize(CommandContext<FabricClientCommandSource> c, Scope s) {
		if (s == null) return 0;
		int hair = s.hair();
		Look l = Kit.random(new Random().nextLong(), Accessories.kits(), hair);
		return replace(c, s, Outfit.of(l, hair >= 0 && l.fur == hair));
	}

	private static int auto(CommandContext<FabricClientCommandSource> c, Scope s) {
		if (s == null) return 0;
		if (!s.canAuto()) return err(c, "Strój dla wszystkich nie ma „auto” – wybierz zestaw, random albo none");
		return replace(c, s, null);
	}

	private static int setItem(CommandContext<FabricClientCommandSource> c, Scope s, Item.Slot slot, Item item) {
		if (s == null) return 0;
		Outfit o = s.current();
		o.set(slot, item);
		return replace(c, s, o);
	}

	private static int setColor(CommandContext<FabricClientCommandSource> c, Scope s, boolean fur, String value) {
		if (s == null) return 0;
		String v = value.trim().toLowerCase(Locale.ROOT);
		if (!v.equals("auto") && Palette.parse(v) < 0)
			return err(c, "Nie znam koloru „" + value.trim() + "” – np. pink, black, lightblue, ff88cc albo auto");
		Outfit o = s.current();
		if (fur) o.fur = v;
		else o.color = v;
		return replace(c, s, o);
	}

	private static void saved() {
		cfg().save();
		Accessories.changed();
	}

	// ======================== ogólne ========================

	private static int help(CommandContext<FabricClientCommandSource> c) {
		FabricClientCommandSource s = c.getSource();
		s.sendFeedback(prefix().append(Text.literal("Dodatki – ").formatted(Formatting.WHITE))
			.append(cfg().acc ? Text.literal("WŁĄCZONE").formatted(Formatting.GREEN) : Text.literal("WYŁĄCZONE").formatted(Formatting.RED))
			.append(Text.literal(", tryb: ").formatted(Formatting.GRAY)).append(hl(modeName(cfg().accMode))));
		line(s, "/femboy acc on | off", "uszy, ogony, obroże, skrzydła… włącz / wyłącz");
		line(s, "/femboy acc mode random|same|me", "losowo każdemu / każdemu to samo / tylko ja");
		line(s, "/femboy acc me <zestaw>", String.join(", ", Kit.ids()));
		line(s, "/femboy acc me <miejsce> <dodatek>", "np. ears cat, tail devil, head halo, back angel, face glasses, arms stripes");
		line(s, "/femboy acc me fur | color <kolor>", "futro / dodatki: pink, black, ff88cc, auto…");
		line(s, "/femboy acc me random | none | auto", "wylosuj / zdejmij wszystko / według trybu");
		line(s, "/femboy acc all …", "strój dla wszystkich (tryb same)");
		line(s, "/femboy acc player <gracz> …", "strój jednej osoby");
		line(s, "/femboy acc kits [zestaw on|off]", "z czego losuje tryb random");
		line(s, "/femboy acc reroll [gracz]", "nowe losowanie");
		line(s, "/femboy acc items", "wszystkie dodatki i kolory");
		return 1;
	}

	private static int setOn(CommandContext<FabricClientCommandSource> c, boolean on) {
		cfg().acc = on;
		saved();
		return msg(c, on ? Text.literal("Dodatki włączone – uszka i ogonki ").append(Text.literal("♥").formatted(Formatting.LIGHT_PURPLE))
			: Text.literal("Dodatki wyłączone"));
	}

	private static int mode(CommandContext<FabricClientCommandSource> c, String mode) {
		cfg().accMode = mode;
		saved();
		msg(c, Text.literal("Tryb dodatków: ").append(hl(modeName(mode))));
		if (mode.equals("same"))
			msg(c, Text.literal("Wszyscy noszą: ").append(describe(cfg().accAll))
				.append(Text.literal(" – zmiana: /femboy acc all …").formatted(Formatting.GRAY)));
		else if (mode.equals("me") && cfg().accMe == null)
			msg(c, Text.literal("Twój strój jest losowy – wybierz go: /femboy acc me <zestaw>").formatted(Formatting.GRAY));
		return 1;
	}

	private static int kits(CommandContext<FabricClientCommandSource> c) {
		MutableText t = Text.literal("Zestawy do losowania:");
		for (Kit k : Kit.values()) {
			boolean on = cfg().accKits.contains(k.id);
			t.append(Text.literal(" " + k.id).formatted(on ? Formatting.GREEN : Formatting.DARK_GRAY, on ? Formatting.BOLD : Formatting.STRIKETHROUGH));
		}
		msg(c, t);
		return msg(c, Text.literal("Zmiana: /femboy acc kits <zestaw> on|off").formatted(Formatting.GRAY));
	}

	private static int kit(CommandContext<FabricClientCommandSource> c, Kit k, boolean on) {
		List<String> l = cfg().accKits;
		if (on && !l.contains(k.id)) l.add(k.id);
		if (!on && l.contains(k.id)) {
			if (l.size() == 1) return err(c, "Musi zostać choć jeden zestaw");
			l.remove(k.id);
		}
		saved();
		return kits(c);
	}

	private static int rerollAll(CommandContext<FabricClientCommandSource> c) {
		long s;
		do s = new Random().nextLong(); while (s == cfg().accSeed);
		cfg().accSeed = s;
		saved();
		msg(c, Text.literal("Nowe losowanie dodatków"));
		if (!cfg().accMode.equals("random"))
			msg(c, Text.literal("(widać je w trybie random – teraz jest: " + modeName(cfg().accMode) + ")").formatted(Formatting.GRAY));
		return 1;
	}

	private static int rerollOne(CommandContext<FabricClientCommandSource> c) {
		FemboyCommands.Target t = target(c);
		if (t == null) return 0;
		boolean self = SkinManager.isSelf(t.uuid(), t.name());
		if (self && cfg().accMe != null)
			return err(c, "Masz ustawiony swój strój – /femboy acc me random (nowy losowy) albo /femboy acc me auto");
		FemboyConfig.PlayerSetting ps = cfg().player(t.uuid());
		if (!self && ps != null && ps.outfit != null)
			return err(c, t.name() + " ma ustawiony strój – /femboy acc player " + t.name() + " auto, żeby znowu losować");
		cfg().playerOrCreate(t.uuid(), t.name()).accSalt++;
		saved();
		return msg(c, Text.literal("Nowe dodatki dla ").append(hl(t.name())));
	}

	private static int items(CommandContext<FabricClientCommandSource> c) {
		FabricClientCommandSource s = c.getSource();
		msg(c, Text.literal("Dodatki (w każdym miejscu jeden naraz):"));
		for (Item.Slot slot : Item.Slot.values()) {
			List<Item> its = Item.of(slot);
			StringBuilder ids = new StringBuilder(), labels = new StringBuilder();
			for (Item it : its) {
				if (!ids.isEmpty()) {
					ids.append('|');
					labels.append(", ");
				}
				ids.append(it.id);
				labels.append(it.label);
			}
			line(s, slot.id + " " + (slot.toggle ? "on|off" : ids + "|none"), slot.label + ": " + labels);
		}
		line(s, "fur | color <kolor>", String.join(", ", Palette.ids()) + ", hex (ff88cc) albo auto");
		line(s, "<zestaw>", String.join(", ", Kit.ids()));
		return 1;
	}

	// ======================== tekst ========================

	static String modeName(String mode) {
		return switch (mode) {
			case "same" -> "każdemu to samo";
			case "me" -> "tylko ja";
			default -> "losowo każdemu";
		};
	}

	/** "kocie uszy, koci ogon (futro: jak włosy, kolor: różowy)" */
	static MutableText describe(Outfit o) {
		if (o == null || o.isEmpty()) return Text.literal("nic").formatted(Formatting.GRAY);
		return hl(o.resolve(-1, 0L).describe()).append(Text.literal(" (" + o.describeColors() + ")").formatted(Formatting.GRAY));
	}
}
