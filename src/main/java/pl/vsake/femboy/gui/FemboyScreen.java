package pl.vsake.femboy.gui;

import static pl.vsake.femboy.Lang.tr;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.client.option.KeyBinding;
import pl.vsake.femboy.FemboyConfig;
import pl.vsake.femboy.FemboyMod;
import pl.vsake.femboy.Keys;
import pl.vsake.femboy.SkinManager;
import pl.vsake.femboy.fx.Clicker;
import pl.vsake.femboy.fx.Emote;
import pl.vsake.femboy.fx.Leash;
import pl.vsake.femboy.fx.Reactions;
import pl.vsake.femboy.fx.Bonds;
import pl.vsake.femboy.fx.Boop;
import pl.vsake.femboy.fx.Pose;
import pl.vsake.femboy.fx.Poses;
import pl.vsake.femboy.fx.Toys;
import pl.vsake.femboy.fx.Trust;
import pl.vsake.femboy.Wardrobe;
import pl.vsake.femboy.acc.Item;
import pl.vsake.femboy.acc.Kit;
import pl.vsake.femboy.acc.Outfit;
import pl.vsake.femboy.acc.Palette;
import pl.vsake.femboy.gen.Style;
import pl.vsake.femboy.voice.Voice;
import pl.vsake.femboy.voice.Voices;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Okno moda: po lewej lista osób (zaznacz dowolną ilość albo „Wszyscy”), w środku żywy podgląd 3D,
 * po prawej zakładki – wygląd (Skin / Dodatki / Zestawy / Kolory), Odgłosy, Zabawa (clicker, smycz, rzeczy we dwoje, emotki),
 * Animacje (kocie pozy), Zabawki (laser, kłębek, efekty), Szafa (zapisane stroje) i Klawisze (wszystkie klawisze moda). Zmiany wyglądu idą do wszystkich zaznaczonych naraz.
 */
public final class FemboyScreen extends Screen {
	private static final int ACCENT = 0xFFFF8FBE;
	private static final int PANEL = 0xC0141018;
	private static final int ROW = 0xA02A2630;
	private static final int ROW_SEL = 0xC0FF8FBE;
	private static final int LINE = 0xFF3A3340;

	private enum Tab {SKIN, ACC, KITS, COLOR, SOUND, FUN, ANIM, TOYS, WARDROBE, KEYS}

	private static final class Cycler {
		final int x, y, w;
		final Supplier<String> value;

		Cycler(int x, int y, int w, Supplier<String> value) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.value = value;
		}
	}

	/** Jedna osoba na liście (albo „Wszyscy”). */
	private static final class Row {
		final EditTarget target;
		final UUID uuid; // null = wszyscy
		boolean selected;

		Row(EditTarget t, UUID u) {
			this.target = t;
			this.uuid = u;
		}
	}

	private final List<Row> rows = new ArrayList<>();
	private final List<Cycler> cyclers = new ArrayList<>();
	/** Przyciski zawartości zakładki (przewijane); zakładki i dolny pasek zostają na miejscu. */
	private final List<net.minecraft.client.gui.widget.ClickableWidget> content = new ArrayList<>();
	private int rightScroll, rightContentH;
	private Row focus;
	private Tab tab = Tab.SKIN;
	private Style browse = Style.FEMBOY;
	/** Zakładka odgłosów: który odgłos edytuję i czy czekam na klawisz do przypisania. */
	private Voice soundEdit = Voice.MEOW;
	/** Klawisz, który czeka na naciśnięcie (zakładki Odgłosy i Klawisze); null = nie przypisuję. */
	private KeyBinding capturing;
	/** Szafa: nazwa wpisywana dla nowego stroju i czy zapisać razem ze skinem. */
	private String wardrobeName = "";
	private boolean wardrobeSkin = true;
	private int listScroll;
	private long openedAt;

	// obszary rysowane ręcznie (liczone w init)
	private int listX, listY, listW, listBottom, rowH = 14;
	private int pvX1, pvY1, pvX2, pvY2;
	private int rx, ry, rw;

	public FemboyScreen() {
		super(Text.literal("Femboy Skins"));
	}

	public static void open() {
		MinecraftClient.getInstance().setScreen(new FemboyScreen());
	}

	/** Otwórz od razu na zakładce (0 skin, 1 dodatki, 2 zestawy, 3 kolory, 4 odgłosy, 5 zabawa, 6 animacje, 7 zabawki, 8 szafa, 9 klawisze) – np. do zrzutów do README. */
	public static void open(int tabIndex) {
		FemboyScreen s = new FemboyScreen();
		Tab[] tabs = Tab.values();
		s.tab = tabs[Math.floorMod(tabIndex, tabs.length)];
		MinecraftClient.getInstance().setScreen(s);
	}

	@Override
	protected void init() {
		openedAt = System.currentTimeMillis();
		buildRows();
		listX = 8;
		listY = 30;
		listW = 112;
		listBottom = height - 30;
		rw = Math.min(180, Math.max(162, width / 4)); // 4 zakładki w rzędzie muszą się zmieścić
		rx = width - rw - 8;
		pvX1 = listX + listW + 10;
		pvY1 = 30;
		pvX2 = rx - 10;
		pvY2 = height - 30;
		rebuild();
	}

	private void buildRows() {
		UUID prevFocus = focus != null ? focus.uuid : null;
		List<UUID> prevSel = new ArrayList<>();
		for (Row r : rows) if (r.selected) prevSel.add(r.uuid);
		rows.clear();
		rows.add(new Row(EditTarget.everyoneTarget(), null));
		Map<UUID, String> players = onlinePlayers();
		MinecraftClient mc = this.client;
		UUID me = mc != null && mc.player != null ? mc.player.getUuid() : null;
		// ja na górze
		if (me != null && players.containsKey(me))
			rows.add(new Row(EditTarget.playerTarget(me, players.get(me)), me));
		for (Map.Entry<UUID, String> e : players.entrySet()) {
			if (e.getKey().equals(me)) continue;
			rows.add(new Row(EditTarget.playerTarget(e.getKey(), e.getValue()), e.getKey()));
		}
		// przywróć zaznaczenie / fokus
		boolean anySel = false;
		for (Row r : rows) {
			if (prevSel.contains(r.uuid)) {
				r.selected = true;
				anySel = true;
			}
			if (java.util.Objects.equals(r.uuid, prevFocus)) focus = r;
		}
		if (focus == null || !rows.contains(focus)) focus = rows.size() > 1 ? rows.get(1) : rows.get(0);
		if (!anySel) focus.selected = true;
		if (!focus.selected) focus.selected = true;
	}

	// ======================== budowa prawego panelu ========================

	private void rebuild() {
		clearChildren();
		cyclers.clear();
		content.clear();
		// zakładki w dwóch rzędach: wygląd, a pod nim odgłosy / zabawa / klawisze
		tabRow(new String[]{"Skin", "Dodatki", "Zestawy", "Kolory"}, new Tab[]{Tab.SKIN, Tab.ACC, Tab.KITS, Tab.COLOR}, 30);
		tabRow(new String[]{"Odgłosy", "Zabawa", "Animacje"}, new Tab[]{Tab.SOUND, Tab.FUN, Tab.ANIM}, 51);
		tabRow(new String[]{"Zabawki", "Szafa", "Klawisze"}, new Tab[]{Tab.TOYS, Tab.WARDROBE, Tab.KEYS}, 72);
		int top = contentTop();
		ry = top - rightScroll;
		switch (tab) {
			case SKIN -> buildSkinTab();
			case ACC -> buildAccTab();
			case KITS -> buildKitsTab();
			case COLOR -> buildColorTab();
			case SOUND -> buildSoundTab();
			case FUN -> buildFunTab();
			case ANIM -> buildAnimTab();
			case TOYS -> buildToysTab();
			case WARDROBE -> buildWardrobeTab();
			case KEYS -> buildKeysTab();
		}
		rightContentH = ry + rightScroll - top;
		// to, co wyjechało poza panel, chowamy (przewijanie kółkiem nad prawym panelem)
		int bottom = contentBottom();
		for (net.minecraft.client.gui.widget.ClickableWidget b : content) b.visible = b.getY() >= top && b.getY() + 20 <= bottom;
		// dół
		int by = height - 26;
		addDrawableChild(ButtonWidget.builder(Text.literal(tr("Losuj od nowa")), b -> applyToSelected(t -> {
			t.rerollSkin();
			t.rerollAcc();
		})).dimensions(rx, by, rw / 2 - 2, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal(tr("Wyczyść")), b -> applyToSelected(EditTarget::reset))
			.dimensions(rx + rw / 2 + 2, by, rw / 2 - 2, 20).build());
	}

	private int contentTop() {
		return 30 + 21 * 2 + 24;
	}

	/** Rząd zakładek: szerokości według napisów, nadmiar po równo (stałe szerokości ucinały „Dodatki” i „Zestawy”). */
	private void tabRow(String[] labels, Tab[] tabs, int y) {
		labels = labels.clone();
		for (int i = 0; i < labels.length; i++) labels[i] = tr(labels[i]);
		int[] tw = new int[labels.length];
		int sum = 0;
		for (int i = 0; i < labels.length; i++) sum += tw[i] = textRenderer.getWidth(labels[i]) + 8;
		int extra = Math.max(0, rw - sum), x = rx;
		for (int i = 0; i < labels.length; i++) {
			int w = i == labels.length - 1 ? rx + rw - x : tw[i] + extra / labels.length;
			addTab(labels[i], x, y, w, tabs[i]);
			x += w;
		}
	}

	private int contentBottom() {
		return height - 30;
	}

	private void addTab(String label, int x, int y, int w, Tab t) {
		// aktywna zakładka: różowa i z kropką (wyszarzony przycisk wyglądał jak wyłączony)
		// podkreślenie zamiast kropki/pogrubienia – nie poszerza napisu, więc „Dodatki” się mieści
		Text txt = tab == t ? Text.literal(label).formatted(Formatting.LIGHT_PURPLE, Formatting.UNDERLINE)
			: Text.literal(label).formatted(Formatting.GRAY);
		addDrawableChild(ButtonWidget.builder(txt, btn -> {
			tab = t;
			capturing = null;
			rightScroll = 0;
			rebuild();
		}).dimensions(x, y, w, 20).build());
	}

	private void buildSkinTab() {
		rowOf(new String[]{"auto", "femboy", "egirl", "catboy", "off"}, id -> {
			Style st = Style.byId(id);
			if (st != null) browse = st; // od razu przeglądaj pulę wybranego stylu
			applyToSelected(t -> t.setStyle(id));
		}, () -> focus.target.styleId());
		ry += 6;
		// przełącznik puli do przeglądania
		addBtn(tr("Pula: ") + browse.label + " (" + SkinManager.poolCount(browse) + ")", rx, ry, rw, () -> {
			browse = browse.next();
			rebuild();
		});
		ry += 22;
		// przeglądarka skinów
		cycler(() -> {
			String k = focus.target.skinKey();
			if (k == null) return tr("losowy skin");
			String l = SkinManager.labelOf(k);
			return l != null ? l : k;
		}, () -> stepSkin(-1), () -> stepSkin(1));
		ry += 4;
		addBtn("Losowy skin", rx, ry, rw / 2 - 2, () -> applyToSelected(t -> t.setSkin(null)));
		addBtn("Daj wszystkim ten", rx + rw / 2 + 2, ry, rw / 2 - 2, () -> {
			String k = focus.target.skinKey();
			FemboyMod.CONFIG.skinAll = k;
			SkinManager.invalidateAll();
			FemboyMod.CONFIG.save();
		});
		ry += 22;
	}

	private void buildAccTab() {
		for (Item.Slot slot : Item.Slot.values()) {
			cycler(() -> {
				Item it = focus.target.outfit().item(slot);
				return slotName(slot) + ": " + (it == null ? "—" : tr(it.label));
			}, () -> stepSlot(slot, -1), () -> stepSlot(slot, 1));
		}
		ry += 4;
		addBtn("Bez dodatków", rx, ry, rw, () -> applyToSelected(t -> t.setOutfit(new Outfit())));
		ry += 22;
	}

	/** Gotowe zestawy, po dwa w rzędzie; + losowy strój i powrót do losowania według trybu. */
	private void buildKitsTab() {
		Kit[] kits = Kit.values();
		int bw = rw / 2 - 2;
		for (int i = 0; i < kits.length; i++) {
			Kit k = kits[i];
			int col = i % 2;
			if (col == 0 && i > 0) ry += 22;
			addBtn(tr(k.label), rx + col * (bw + 4), ry, bw, () -> applyToSelected(t -> t.setOutfit(k.outfit())));
		}
		ry += 26;
		addBtn("Losowy strój", rx, ry, rw, () -> applyToSelected(t -> {
			java.util.Random r = new java.util.Random();
			int hair = t.hair();
			pl.vsake.femboy.acc.Look l = Kit.random(r.nextLong(), pl.vsake.femboy.acc.Accessories.kits(), hair);
			t.setOutfit(Outfit.of(l, hair >= 0 && l.fur == hair));
		}));
		ry += 22;
		addBtn("Według trybu (auto)", rx, ry, rw, () -> applyToSelected(t -> t.setOutfit(null)));
		ry += 22;
	}

	private void buildColorTab() {
		cycler(() -> tr("Futro: ") + colorLabel(focus.target.outfit().fur, true),
			() -> stepColor(true, -1), () -> stepColor(true, 1));
		cycler(() -> tr("Dodatki: ") + colorLabel(focus.target.outfit().color, false),
			() -> stepColor(false, -1), () -> stepColor(false, 1));
		ry += 4;
		addBtn("Futro = kolor włosów", rx, ry, rw, () -> applyToSelected(t -> {
			Outfit o = t.outfit();
			o.fur = "auto";
			t.setOutfit(o);
		}));
		ry += 22;
		addBtn("Kolor dodatków losowy", rx, ry, rw, () -> applyToSelected(t -> {
			Outfit o = t.outfit();
			o.color = "auto";
			t.setOutfit(o);
		}));
		ry += 22;
	}

	/**
	 * Odgłosy (miau, hau…): głośność i wyciszenie zaznaczonych osób („Wszyscy” = ogólna głośność i wyłącznik),
	 * a pod spodem przyciski do zagrania własnego odgłosu (to samo co klawisze ze Sterowania).
	 */
	private void buildSoundTab() {
		cycler(() -> {
			if (focus.uuid == null) return tr("Ogólnie: ") + (FemboyMod.CONFIG.voice ? FemboyMod.CONFIG.voiceVolume + "%" : tr("wyciszone"));
			return tr("Głośność: ") + (Voices.muted(focus.uuid) ? tr("wyciszony") : Voices.percent(focus.uuid) + "%");
		}, () -> stepVolume(-10), () -> stepVolume(10));
		boolean muted = focus.uuid == null ? !FemboyMod.CONFIG.voice : Voices.muted(focus.uuid);
		addBtn(muted ? "Odcisz" : "Wycisz", rx, ry, rw / 2 - 2, () -> setMuted(!muted));
		addBtn("100%", rx + rw / 2 + 2, ry, rw / 2 - 2, () -> forSelectedSound(
			() -> FemboyMod.CONFIG.voiceVolume = 100, r -> Voices.setPercent(r.uuid, onlinePlayers().get(r.uuid), 100)));
		ry += 28;
		// mój odgłos: który, pod jakim klawiszem, z których wariantów losować
		Voice v = soundEdit;
		cycler(() -> tr("Odgłos: ") + tr(soundEdit.label), () -> stepVoice(-1), () -> stepVoice(1));
		boolean cap = capturing != null && capturing == Voices.key(v);
		Text keyTxt = cap ? Text.literal(tr("> naciśnij klawisz <")).formatted(Formatting.YELLOW)
			: Text.literal(tr("Klawisz: ")).append(Text.literal(Voices.keyName(v)).formatted(Formatting.LIGHT_PURPLE));
		addBtn(keyTxt, rx, ry, rw - 62, () -> {
			capturing = cap ? null : Voices.key(v);
			rebuild();
		});
		addBtn(Text.literal(tr("▶ Zagraj")), rx + rw - 60, ry, 60, () -> Voices.emit(v));
		ry += 22;
		int on = Voices.enabled(v).size();
		addBtn(Text.literal(tr("Włącz wszystkie (") + on + "/" + v.variants() + ")"), rx, ry, rw, () -> {
			Voices.enableAll(v);
			rebuild();
		});
		ry += 22;
		// warianty: ▶ odsłuch (tylko u mnie) | ✔/✘ wariant | „tylko” = zostaw sam ten
		int onlyW = textRenderer.getWidth(tr("tylko")) + 10;
		for (int i = 0; i < v.variants(); i++) {
			int idx = i;
			boolean en = Voices.isEnabled(v, i);
			addBtn(Text.literal("▶"), rx, ry, 18, () -> Voices.preview(v, idx));
			Text lbl = Text.literal((en ? "✔ " : "✘ ") + trim((i + 1) + ". " + v.variantLabel(i), rw - 18 - onlyW - 24))
				.formatted(en ? Formatting.GREEN : Formatting.DARK_GRAY);
			addBtn(lbl, rx + 20, ry, rw - 20 - onlyW - 2, () -> {
				Voices.toggle(v, idx); // ostatni włączony zostaje – musi być z czego losować
				rebuild();
			});
			addBtn(Text.literal(tr("tylko")), rx + rw - onlyW, ry, onlyW, () -> {
				Voices.only(v, idx);
				rebuild();
			});
			ry += 22;
		}
	}

	private void stepVoice(int d) {
		Voice[] vs = Voice.values();
		soundEdit = vs[Math.floorMod(soundEdit.ordinal() + d, vs.length)];
		capturing = null;
		rebuild();
	}

	/**
	 * Zabawa: clicker i smycz (obie rzeczy działają na mnie tylko, gdy sam je włączę – i mogę je zawęzić do wybranych osób),
	 * rzeczy we dwoje z zaznaczonym graczem (boop, za rękę, noszenie) i emotki nad głową.
	 */
	private void buildFunTab() {
		FemboyConfig c = FemboyMod.CONFIG;
		header("Clicker");
		toggle("Clicker", c.clicker, v -> c.clicker = v, rx, rw - 62);
		addBtn(Text.literal(tr("Kliknij")), rx + rw - 60, ry, 60, Clicker::use);
		ry += 22;
		toggle("Tylko wybrane osoby", c.clickerPicked, v -> c.clickerPicked = v, rx, rw);
		ry += 22;
		trust("Reaguję na", Trust.What.CLICKER);
		header("Smycz");
		toggle("Daję się ciągnąć", c.leashPull, v -> c.leashPull = v, rx, rw);
		ry += 22;
		toggle("Tylko wybrane osoby", c.leashPicked, v -> c.leashPicked = v, rx, rw);
		ry += 22;
		trust("Mogą mnie ciągnąć", Trust.What.LEASH);
		toggle("Widzę smycz na sobie", c.leashSeeOwn, v -> c.leashSeeOwn = v, rx, rw);
		ry += 22;
		UUID other = otherFocus();
		String name = other == null ? null : onlinePlayers().getOrDefault(other, focus.target.label());
		if (Leash.leading() != null) {
			addBtn(tr("Puść smycz (") + Leash.leadingName() + ")", rx, ry, rw, () -> {
				Leash.release();
				rebuild();
			});
		} else if (other != null) {
			addBtn(tr("Weź na smycz: ") + name, rx, ry, rw, () -> {
				Leash.take(other, name);
				rebuild();
			});
		} else hint("Smycz: wybierz gracza z listy");
		ry += 22;
		header("We dwoje");
		int bw = rw / 2 - 2;
		if (other != null) {
			addBtn("Boop w nosek", rx, ry, bw, () -> Boop.use(other));
			pairBtn("Za rękę", Bonds.HANDS, other, name, rx + bw + 4, bw);
			ry += 22;
			pairBtn("Na ręce", Bonds.CARRY, other, name, rx, bw);
			pairBtn("Na barana", Bonds.PIGGY, other, name, rx + bw + 4, bw);
		} else hint("Wybierz gracza z listy");
		ry += 22;
		if (Bonds.mineKind() != 0) {
			addBtn(tr("Puść: ") + Bonds.mineName(), rx, ry, rw, () -> {
				Bonds.stop();
				rebuild();
			});
			ry += 22;
		}
		if (Bonds.asking() != null) {
			UUID who = Bonds.asking();
			addBtn(Text.literal(tr("Zgódź się na noszenie: ") + onlinePlayers().getOrDefault(who, "?")).formatted(Formatting.YELLOW), rx, ry, rw, () -> {
				Bonds.consent(who);
				rebuild();
			});
			ry += 22;
		}
		trust("Mogą mnie nosić bez pytania", Trust.What.CARRY);
		header("Emotki nad głową");
		Emote[] emotes = Emote.values();
		for (int i = 0; i < emotes.length; i++) {
			Emote e = emotes[i];
			int col = i % 2;
			if (col == 0 && i > 0) ry += 22;
			addBtn(e.symbol + " " + tr(e.label), rx + col * (bw + 4), ry, bw, () -> Reactions.emote(e));
		}
		ry += 22;
	}

	/** Zaznaczony gracz inny niż ja (do smyczy, boopa, noszenia…) albo null. */
	private UUID otherFocus() {
		return focus.uuid != null && !focus.uuid.equals(selfUuid()) ? focus.uuid : null;
	}

	private void hint(String text) {
		addBtn(Text.literal(tr(text)).formatted(Formatting.DARK_GRAY), rx, ry, rw, () -> {
		});
	}

	/** Przycisk rozpoczęcia / zakończenia rzeczy we dwoje z zaznaczonym graczem. */
	private void pairBtn(String label, char kind, UUID other, String name, int x, int w) {
		boolean on = Bonds.mineKind() == kind;
		addBtn(on ? Text.literal(tr(label)).formatted(Formatting.LIGHT_PURPLE) : Text.literal(tr(label)), x, ry, w, () -> {
			if (on) Bonds.stop();
			else Bonds.start(kind, other, name);
			rebuild();
		});
	}

	/**
	 * Lista osób, na które reaguję: pole tekstowe, jeden nick w linijce. Gdy pole jest aktywne, kliknięcie gracza na liście
	 * obok dopisuje jego nick w nowej linijce (i nie zmienia zaznaczenia).
	 */
	private void trust(String label, Trust.What what) {
		hint(tr(label) + tr(" – nick pod nickiem:"));
		ry += 22;
		net.minecraft.client.gui.widget.EditBoxWidget box = net.minecraft.client.gui.widget.EditBoxWidget.builder()
			.x(rx).y(ry).placeholder(Text.literal(tr("wpisz nick albo kliknij gracza z listy")).formatted(Formatting.DARK_GRAY))
			.build(textRenderer, rw, 50, Text.literal(tr(label)));
		box.setMaxLength(Trust.MAX_NAMES * (Trust.MAX_NAME + 1));
		box.setText(Trust.text(what));
		box.setChangeListener(t -> Trust.setText(what, t));
		content.add(addDrawableChild(box));
		ry += 54;
	}

	/** Dopisuje nick do aktywnego pola z listą osób. false = żadne takie pole nie jest aktywne. */
	private boolean typeNick(Row r) {
		if (!(getFocused() instanceof net.minecraft.client.gui.widget.EditBoxWidget box) || !box.isFocused() || !content.contains(box)) return false;
		if (r.uuid == null || r.uuid.equals(selfUuid())) return true; // „Wszyscy” i ja – nic do wpisania, ale klik nie zmienia zaznaczenia
		String nick = onlinePlayers().get(r.uuid);
		if (nick == null || nick.isBlank()) return true;
		List<String> have = Trust.parse(box.getText());
		if (Trust.has(have, nick)) return true;
		have.add(nick);
		box.setText(String.join("\n", have)); // zapis robi się sam (nasłuch zmian pola)
		return true;
	}

	/** Animacje ciała: kocie pozy (to samo co klawisze) i te, które włączają się same. */
	private void buildAnimTab() {
		FemboyConfig c = FemboyMod.CONFIG;
		Pose[] poses = Pose.values();
		Pose mine = Poses.mine();
		int bw = rw / 2 - 2;
		for (int i = 0; i < poses.length; i++) {
			Pose p = poses[i];
			int col = i % 2;
			if (col == 0 && i > 0) ry += 22;
			String lbl = trim(textRenderer.getWidth(tr(p.label)) <= bw - 8 ? tr(p.label) : tr(p.brief), bw - 8);
			addBtn(p == mine ? Text.literal(lbl).formatted(Formatting.LIGHT_PURPLE) : Text.literal(lbl), rx + col * (bw + 4), ry, bw, () -> {
				Poses.play(p);
				rebuild();
			});
		}
		ry += 26;
		addBtn("■ Stop", rx, ry, rw, () -> {
			Poses.stopMine();
			rebuild();
		});
		ry += 22;
		header("Same z siebie");
		toggle("Bochenek i przeciąganie", c.autoPoses, v -> c.autoPoses = v, rx, rw);
		ry += 22;
		toggle("Mruganie", c.blink, v -> c.blink = v, rx, rw);
		ry += 22;
	}

	/** Zabawki (laser, kłębek) i przełączniki efektów. */
	private void buildToysTab() {
		FemboyConfig c = FemboyMod.CONFIG;
		header("Wskaźnik laserowy (trzymaj klawisz)");
		toggle("Idę za kropką", c.laserFollow, v -> c.laserFollow = v, rx, rw);
		ry += 22;
		toggle("Tylko wybrane osoby", c.laserPicked, v -> c.laserPicked = v, rx, rw);
		ry += 22;
		trust("Osoby, za których zabawkami chodzę", Trust.What.LASER);
		header("Kłębek włóczki");
		addBtn("Rzuć kłębek", rx, ry, rw, Toys::throwYarn);
		ry += 22;
		toggle("Gonię cudze kłębki", c.yarnFollow, v -> c.yarnFollow = v, rx, rw);
		ry += 22;
		toggle("Widzę cudze zabawki", c.toys, v -> c.toys = v, rx, rw);
		ry += 22;
		header("Efekty");
		toggle("Ślady łapek (śnieg, piasek)", c.pawPrints, v -> c.pawPrints = v, rx, rw);
		ry += 22;
		toggle("Dzwoneczki przy chodzeniu", c.bells, v -> c.bells = v, rx, rw);
		ry += 22;
		toggle("Mruczenie głaskanego kota", c.patPurr, v -> c.patPurr = v, rx, rw);
		ry += 22;
		toggle("Serduszka przy głaskaniu", c.patHearts, v -> c.patHearts = v, rx, rw);
		ry += 22;
		toggle("Reakcje uszu i ogona", c.react, v -> c.react = v, rx, rw);
		ry += 22;
	}

	/** Szafa: zapis stroju zaznaczonej osoby pod nazwą, a niżej lista – klik zakłada strój wszystkim zaznaczonym. */
	private void buildWardrobeTab() {
		List<FemboyConfig.Saved> all = Wardrobe.all();
		net.minecraft.client.gui.widget.TextFieldWidget field = new net.minecraft.client.gui.widget.TextFieldWidget(textRenderer, rx + 1, ry + 1, rw - 2, 18, Text.literal(tr("Nazwa stroju")));
		field.setMaxLength(24);
		field.setText(wardrobeName);
		field.setPlaceholder(Text.literal(Wardrobe.freeName()).formatted(Formatting.DARK_GRAY));
		field.setChangedListener(t -> wardrobeName = t);
		content.add(addDrawableChild(field));
		ry += 22;
		addBtn(tr("Zapisz strój: ") + trim(focus.target.label(), rw - 90), rx, ry, rw, () -> {
			EditTarget t = focus.target;
			if (Wardrobe.save(wardrobeName, t.outfit(), wardrobeSkin ? t.skinKey() : null) >= 0) wardrobeName = "";
			rebuild();
		});
		ry += 22;
		addBtn(tr("Razem ze skinem: ") + (wardrobeSkin ? tr("tak") : tr("nie")), rx, ry, rw, () -> {
			wardrobeSkin = !wardrobeSkin;
			rebuild();
		});
		ry += 22;
		header(tr("Stroje ") + all.size() + "/" + Wardrobe.MAX + tr(" – klawisz ") + Keys.name(wardrobeKey()));
		if (all.isEmpty()) hint("Szafa jest pusta");
		for (int i = 0; i < all.size(); i++) {
			FemboyConfig.Saved sv = all.get(i);
			int idx = i;
			boolean worn = Wardrobe.current() == i;
			String lbl = trim(sv.name + (sv.skin != null ? " +skin" : ""), rw - 34);
			addBtn(worn ? Text.literal(lbl).formatted(Formatting.LIGHT_PURPLE) : Text.literal(lbl), rx, ry, rw - 20, () -> {
				boolean onlyMe = true;
				for (Row r : rows) if (r.selected && (r.uuid == null || !r.uuid.equals(selfUuid()))) onlyMe = false;
				if (onlyMe) {
					Wardrobe.wear(idx); // pamięta, który strój mam na sobie – klawisz przełączy na następny
					rebuild();
				} else applyToSelected(t -> {
					t.setOutfit(sv.outfit.copy());
					if (sv.skin != null) t.setSkin(sv.skin);
				});
			});
			addBtn(Text.literal("✕"), rx + rw - 18, ry, 18, () -> {
				Wardrobe.remove(idx);
				rebuild();
			});
			ry += 22;
		}
	}

	private static KeyBinding wardrobeKey() {
		for (Keys.Entry e : Keys.all()) if (e.label().startsWith("Następny strój")) return e.key();
		return null;
	}

	/** Wszystkie klawisze moda: klik = czekam na nowy klawisz (Esc = bez klawisza), ✕ = usuń przypisanie. */
	private void buildKeysTab() {
		String group = null;
		for (Keys.Entry e : Keys.all()) {
			if (!e.group().equals(group)) header(group = e.group());
			boolean cap = capturing == e.key();
			Text txt = cap ? Text.literal(tr("> naciśnij klawisz <")).formatted(Formatting.YELLOW)
				: Text.literal(trim(tr(e.label()), rw - 70) + ": ").append(Text.literal(Keys.name(e.key())).formatted(Formatting.LIGHT_PURPLE));
			addBtn(txt, rx, ry, rw - 20, () -> {
				capturing = cap ? null : e.key();
				rebuild();
			});
			addBtn(Text.literal("✕"), rx + rw - 18, ry, 18, () -> {
				Keys.bind(e.key(), net.minecraft.client.util.InputUtil.UNKNOWN_KEY);
				capturing = null;
				rebuild();
			});
			ry += 22;
		}
	}

	/** Nagłówek sekcji w prawym panelu (rysowany razem z wartościami cyklerów). */
	private void header(String title) {
		cyclers.add(new Cycler(rx, ry - 4, rw, () -> "— " + tr(title) + " —"));
		ry += 13;
	}

	/** Przełącznik włączone/wyłączone; zapisuje config od razu. */
	private void toggle(String label, boolean on, Consumer<Boolean> set, int x, int w) {
		Text txt = Text.literal(tr(label) + ": ").append(on ? Text.literal(tr("wł.")).formatted(Formatting.GREEN) : Text.literal(tr("wył.")).formatted(Formatting.RED));
		addBtn(txt, x, ry, w, () -> {
			set.accept(!on);
			FemboyMod.CONFIG.save();
			rebuild();
		});
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
		if (capturing != null) { // Esc = bez klawisza
			Keys.bind(capturing, input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
				? net.minecraft.client.util.InputUtil.UNKNOWN_KEY : net.minecraft.client.util.InputUtil.fromKeyCode(input));
			capturing = null;
			rebuild();
			return true;
		}
		return super.keyPressed(input);
	}

	private void stepVolume(int d) {
		forSelectedSound(() -> {
			FemboyMod.CONFIG.voiceVolume = Math.max(0, Math.min(100, FemboyMod.CONFIG.voiceVolume + d));
			FemboyMod.CONFIG.voice = true;
		}, r -> {
			Voices.setPercent(r.uuid, onlinePlayers().get(r.uuid), Voices.percent(r.uuid) + d);
			if (Voices.muted(r.uuid)) Voices.setMuted(r.uuid, null, false);
		});
	}

	private void setMuted(boolean muted) {
		forSelectedSound(() -> FemboyMod.CONFIG.voice = !muted, r -> Voices.setMuted(r.uuid, onlinePlayers().get(r.uuid), muted));
	}

	/** Zmiana głośności u wszystkich zaznaczonych: „Wszyscy” → ustawienie ogólne, gracz → jego wpis. */
	private void forSelectedSound(Runnable everyone, Consumer<Row> player) {
		for (Row r : rows) {
			if (!r.selected) continue;
			if (r.uuid == null) everyone.run();
			else player.accept(r);
		}
		FemboyMod.CONFIG.save();
		rebuild();
	}

	/** Przyciski w rzędzie (styl); podświetla aktualny. */
	private void rowOf(String[] ids, Consumer<String> onPick, Supplier<String> current) {
		int bw = rw / ids.length - 1;
		for (int i = 0; i < ids.length; i++) {
			String id = ids[i];
			ButtonWidget w = ButtonWidget.builder(
				Text.literal(id).formatted(id.equals(current.get()) ? Formatting.LIGHT_PURPLE : Formatting.WHITE),
				btn -> onPick.accept(id)).dimensions(rx + i * (bw + 1), ry, bw, 20).build();
			content.add(addDrawableChild(w));
		}
		ry += 22;
	}

	/** Trójka ◀ wartość ▶; wartość rysuje się w render(). */
	private void cycler(Supplier<String> value, Runnable prev, Runnable next) {
		content.add(addDrawableChild(ButtonWidget.builder(Text.literal("◀"), b -> prev.run()).dimensions(rx, ry, 18, 20).build()));
		content.add(addDrawableChild(ButtonWidget.builder(Text.literal("▶"), b -> next.run()).dimensions(rx + rw - 18, ry, 18, 20).build()));
		cyclers.add(new Cycler(rx + 20, ry, rw - 40, value));
		ry += 22;
	}

	private void addBtn(String label, int x, int y, int w, Runnable onPress) {
		addBtn(Text.literal(tr(label)), x, y, w, onPress);
	}

	private void addBtn(Text label, int x, int y, int w, Runnable onPress) {
		content.add(addDrawableChild(ButtonWidget.builder(label, b -> onPress.run()).dimensions(x, y, w, 20).build()));
	}

	// ======================== zmiany ========================

	private void applyToSelected(Consumer<EditTarget> change) {
		for (Row r : rows)
			if (r.selected) {
				change.accept(r.target);
				r.target.invalidate();
			}
		rebuild();
	}

	private void stepSkin(int dir) {
		List<SkinManager.SkinOption> opts = SkinManager.options(browse);
		if (opts.isEmpty()) return;
		String cur = focus.target.skinKey();
		int idx = -1;
		for (int i = 0; i < opts.size(); i++) if (opts.get(i).key().equals(cur)) idx = i;
		idx = Math.floorMod(idx + dir, opts.size());
		String key = opts.get(idx).key();
		applyToSelected(t -> t.setSkin(key));
	}

	private void stepSlot(Item.Slot slot, int dir) {
		List<Item> items = new ArrayList<>();
		items.add(null); // brak
		items.addAll(Item.of(slot));
		Item cur = focus.target.outfit().item(slot);
		int idx = items.indexOf(cur);
		if (idx < 0) idx = 0;
		int ni = Math.floorMod(idx + dir, items.size());
		Item pick = items.get(ni);
		applyToSelected(t -> {
			Outfit o = t.outfit();
			o.set(slot, pick);
			t.setOutfit(o);
		});
	}

	private void stepColor(boolean fur, int dir) {
		List<String> opts = new ArrayList<>();
		opts.add("auto");
		opts.addAll(Palette.ids());
		String cur = fur ? focus.target.outfit().fur : focus.target.outfit().color;
		if (cur == null) cur = "auto";
		int idx = opts.indexOf(cur.toLowerCase(java.util.Locale.ROOT));
		if (idx < 0) idx = 0;
		int ni = Math.floorMod(idx + dir, opts.size());
		String pick = opts.get(ni);
		applyToSelected(t -> {
			Outfit o = t.outfit();
			if (fur) o.fur = pick;
			else o.color = pick;
			t.setOutfit(o);
		});
	}

	// ======================== render ========================

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawText(textRenderer, Text.literal("Femboy Skins").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), 8, 12, 0xFFFFFFFF, true);
		ctx.drawText(textRenderer, Text.literal(tr("klik = zaznacz; zmiany dotyczą wszystkich zaznaczonych")).formatted(Formatting.DARK_GRAY),
			130, 14, 0xFFFFFFFF, false);
		drawList(ctx, mouseX, mouseY);
		drawPreview(ctx, mouseX, mouseY);
		// wartości cyklerów
		for (Cycler cc : cyclers) {
			if (cc.y + 6 < contentTop() || cc.y + 15 > contentBottom()) continue; // liczy się sam napis (nagłówki są niższe niż przyciski)
			String v = trim(cc.value.get(), cc.w);
			int tw = textRenderer.getWidth(v);
			ctx.drawText(textRenderer, Text.literal(v), cc.x + (cc.w - tw) / 2, cc.y + 6, 0xFFFFFFFF, false);
		}
	}

	private void drawList(DrawContext ctx, int mouseX, int mouseY) {
		ctx.fill(listX - 2, listY - 2, listX + listW + 2, listBottom + 2, PANEL);
		ctx.enableScissor(listX - 2, listY, listX + listW + 2, listBottom);
		int y = listY - listScroll;
		for (Row r : rows) {
			if (y + rowH >= listY && y <= listBottom) {
				boolean hover = mouseX >= listX && mouseX <= listX + listW && mouseY >= y && mouseY < y + rowH;
				int bg = r.selected ? ROW_SEL : hover ? 0x60FFFFFF : ROW;
				ctx.fill(listX, y, listX + listW, y + rowH, bg);
				int col = r.selected ? 0xFF201820 : r == focus ? 0xFFFFE0F4 : 0xFFE8E8EE;
				String nm = trim(r.target.label(), listW - 6);
				ctx.drawText(textRenderer, Text.literal(nm), listX + 4, y + 3, col, false);
				if (r == focus) ctx.fill(listX, y, listX + 2, y + rowH, ACCENT);
			}
			y += rowH + 1;
		}
		ctx.disableScissor();
	}

	private void drawPreview(DrawContext ctx, int mouseX, int mouseY) {
		ctx.fill(pvX1, pvY1, pvX2, pvY2, PANEL);
		int cx = (pvX1 + pvX2) / 2, cy = (pvY1 + pvY2) / 2;
		AbstractClientPlayerEntity ent = previewEntity();
		if (ent != null) {
			// gracz to ~1,8 bloku, a z uszami/aureolką ~2,2 – rozmiar tak, żeby cała postać mieściła się w ramce
			int size = Math.max(20, Math.min((pvX2 - pvX1) * 2 / 3, (pvY2 - pvY1 - 34) * 10 / 23));
			try {
				InventoryScreen.drawEntity(ctx, pvX1, pvY1, pvX2, pvY2, size, 0.0625f,
					(float) mouseX, (float) mouseY, ent);
			} catch (Throwable t) {
				flat(ctx, cx, cy);
			}
		} else {
			flat(ctx, cx, cy);
		}
		// opis
		String d = SkinManager.describe(focus.target.uuid() == null ? selfUuid() : focus.target.uuid(),
			focus.target.label());
		String sk = focus.target.skinKey();
		String txt = sk != null ? ("skin: " + orDash(SkinManager.labelOf(sk))) : (d != null ? d : tr("oryginał"));
		int tw = textRenderer.getWidth(txt);
		ctx.drawText(textRenderer, Text.literal(trim(txt, pvX2 - pvX1 - 6)).formatted(Formatting.LIGHT_PURPLE),
			cx - Math.min(tw, pvX2 - pvX1 - 6) / 2, pvY2 - 12, 0xFFFFFFFF, false);
		ctx.drawText(textRenderer, Text.literal(trim(focus.target.label(), pvX2 - pvX1 - 6)).formatted(Formatting.WHITE),
			pvX1 + 4, pvY1 + 4, 0xFFFFFFFF, false);
		// licznik głaskań (swój zawsze, cudzy – gdy gracz ma moda i go przysłał)
		if (ent != null && pl.vsake.femboy.pat.PatPat.count(ent) > 0 && focus.uuid != null)
			ctx.drawText(textRenderer, Text.literal(tr("pogłaskany ") + pl.vsake.femboy.pat.PatPat.count(ent) + "×").formatted(Formatting.GRAY),
				pvX1 + 4, pvY1 + 15, 0xFFFFFFFF, false);
	}

	private void flat(DrawContext ctx, int cx, int cy) {
		String key = focus.target.skinKey();
		Identifier tex = key != null ? SkinManager.previewTexture(key) : null;
		if (tex != null) {
			int s = 64;
			// twarz 8x8 z (8,8)
			ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, tex, cx - s / 2, cy - s / 2, 8f, 8f, s, s, 8, 8, 64, 64);
		} else {
			String m = tr("wejdź bliżej, by zobaczyć podgląd");
			ctx.drawText(textRenderer, Text.literal(m).formatted(Formatting.DARK_GRAY),
				cx - textRenderer.getWidth(m) / 2, cy, 0xFFFFFFFF, false);
		}
	}

	private AbstractClientPlayerEntity previewEntity() {
		MinecraftClient mc = this.client;
		if (mc == null || mc.world == null) return null;
		UUID u = focus.target.uuid();
		if (u == null) return mc.player; // „wszyscy” → pokaż siebie jako przykład
		if (mc.player != null && u.equals(mc.player.getUuid())) return mc.player;
		for (AbstractClientPlayerEntity p : mc.world.getPlayers()) if (p.getUuid().equals(u)) return p;
		return null;
	}

	private UUID selfUuid() {
		MinecraftClient mc = this.client;
		return mc != null && mc.player != null ? mc.player.getUuid() : new UUID(0, 0);
	}

	// ======================== wejście ========================

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		double mx = click.x(), my = click.y();
		if (mx >= listX && mx <= listX + listW && my >= listY && my <= listBottom) {
			int y = listY - listScroll;
			for (Row r : rows) {
				if (my >= y && my < y + rowH) {
					if (typeNick(r)) return true;
					toggle(r);
					return true;
				}
				y += rowH + 1;
			}
		}
		return super.mouseClicked(click, doubled);
	}

	private void toggle(Row r) {
		r.selected = !r.selected;
		if (r.selected) focus = r;
		// zawsze coś zaznaczone, zawsze jest fokus
		boolean any = false;
		for (Row x : rows) any |= x.selected;
		if (!any) {
			r.selected = true;
			focus = r;
		}
		if (!focus.selected) {
			for (Row x : rows)
				if (x.selected) {
					focus = x;
					break;
				}
		}
		rebuild();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
		if (mouseX >= listX - 2 && mouseX <= listX + listW + 2 && mouseY >= listY && mouseY <= listBottom) {
			int total = rows.size() * (rowH + 1);
			int view = listBottom - listY;
			if (total > view) {
				listScroll = (int) Math.max(0, Math.min(total - view, listScroll - v * 12));
				return true;
			}
		}
		if (mouseX >= rx && mouseX <= rx + rw && mouseY >= contentTop() && mouseY <= contentBottom()) {
			int view = contentBottom() - contentTop();
			int max = Math.max(0, rightContentH - view);
			int ns = (int) Math.max(0, Math.min(max, rightScroll - v * 22));
			if (ns != rightScroll) {
				rightScroll = ns;
				rebuild();
			}
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, h, v);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	// ======================== pomocnicze ========================

	private static Map<UUID, String> onlinePlayers() {
		Map<UUID, String> out = new LinkedHashMap<>();
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.getNetworkHandler() != null)
			for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) out.put(e.getProfile().id(), e.getProfile().name());
		if (mc.world != null)
			for (AbstractClientPlayerEntity p : mc.world.getPlayers()) out.putIfAbsent(p.getUuid(), p.getGameProfile().name());
		return out;
	}

	private static String slotName(Item.Slot slot) {
		return switch (slot) {
			case EARS -> tr("Uszy");
			case TAIL -> tr("Ogon");
			case NECK -> tr("Szyja");
			case HEAD -> tr("Włosy");
			case FACE -> tr("Twarz");
			case BACK -> tr("Plecy");
			case ARMS -> tr("Ręce");
			case PAWS -> tr("Łapki");
			case SKIRT -> tr("Spódnica");
			case FX -> tr("Efekt");
			case HAND -> tr("W ręce");
		};
	}

	private static String colorLabel(String s, boolean fur) {
		if (s == null || "auto".equalsIgnoreCase(s)) return fur ? tr("jak włosy") : tr("losowy");
		int rgb = Palette.parse(s);
		return rgb < 0 ? s : tr(Palette.label(rgb));
	}

	private static String orDash(String s) {
		return s == null ? "—" : s;
	}

	private String trim(String s, int maxW) {
		if (textRenderer.getWidth(s) <= maxW) return s;
		while (s.length() > 1 && textRenderer.getWidth(s + "…") > maxW) s = s.substring(0, s.length() - 1);
		return s + "…";
	}
}
