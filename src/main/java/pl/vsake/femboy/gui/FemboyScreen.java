package pl.vsake.femboy.gui;

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
import pl.vsake.femboy.FemboyMod;
import pl.vsake.femboy.SkinManager;
import pl.vsake.femboy.acc.Item;
import pl.vsake.femboy.acc.Kit;
import pl.vsake.femboy.acc.Outfit;
import pl.vsake.femboy.acc.Palette;
import pl.vsake.femboy.gen.Style;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Ekran wyboru wyglądu: po lewej lista osób (zaznacz dowolną ilość albo „Wszyscy”),
 * w środku żywy podgląd 3D, po prawej zakładki Skin / Dodatki / Kolory z mnóstwem opcji.
 * Zmiany idą do wszystkich zaznaczonych naraz i od razu widać je w podglądzie.
 */
public final class FemboyScreen extends Screen {
	private static final int ACCENT = 0xFFFF8FBE;
	private static final int PANEL = 0xC0141018;
	private static final int ROW = 0xA02A2630;
	private static final int ROW_SEL = 0xC0FF8FBE;
	private static final int LINE = 0xFF3A3340;

	private enum Tab {SKIN, ACC, KITS, COLOR}

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
	private final List<ButtonWidget> content = new ArrayList<>();
	private int rightScroll, rightContentH;
	private Row focus;
	private Tab tab = Tab.SKIN;
	private Style browse = Style.FEMBOY;
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

	/** Otwórz od razu na zakładce (0 skin, 1 dodatki, 2 zestawy, 3 kolory) – np. do zrzutów do README. */
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
		rw = Math.min(176, Math.max(150, width / 4));
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
		// zakładki
		int tabW = rw / 4;
		addTab("Skin", rx, tabW, Tab.SKIN);
		addTab("Dodatki", rx + tabW, tabW, Tab.ACC);
		addTab("Zestawy", rx + tabW * 2, tabW, Tab.KITS);
		addTab("Kolory", rx + tabW * 3, rw - tabW * 3, Tab.COLOR);
		int top = contentTop();
		ry = top - rightScroll;
		switch (tab) {
			case SKIN -> buildSkinTab();
			case ACC -> buildAccTab();
			case KITS -> buildKitsTab();
			case COLOR -> buildColorTab();
		}
		rightContentH = ry + rightScroll - top;
		// to, co wyjechało poza panel, chowamy (przewijanie kółkiem nad prawym panelem)
		int bottom = contentBottom();
		for (ButtonWidget b : content) b.visible = b.getY() >= top && b.getY() + 20 <= bottom;
		// dół
		int by = height - 26;
		addDrawableChild(ButtonWidget.builder(Text.literal("Losuj od nowa"), b -> applyToSelected(t -> {
			t.rerollSkin();
			t.rerollAcc();
		})).dimensions(rx, by, rw / 2 - 2, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Wyczyść"), b -> applyToSelected(EditTarget::reset))
			.dimensions(rx + rw / 2 + 2, by, rw / 2 - 2, 20).build());
	}

	private int contentTop() {
		return 30 + 24;
	}

	private int contentBottom() {
		return height - 30;
	}

	private void addTab(String label, int x, int w, Tab t) {
		// aktywna zakładka: różowa i z kropką (wyszarzony przycisk wyglądał jak wyłączony)
		// podkreślenie zamiast kropki/pogrubienia – nie poszerza napisu, więc „Dodatki” się mieści
		Text txt = tab == t ? Text.literal(label).formatted(Formatting.LIGHT_PURPLE, Formatting.UNDERLINE)
			: Text.literal(label).formatted(Formatting.GRAY);
		addDrawableChild(ButtonWidget.builder(txt, btn -> {
			tab = t;
			rightScroll = 0;
			rebuild();
		}).dimensions(x, 30, w, 20).build());
	}

	private void buildSkinTab() {
		rowOf(new String[]{"auto", "femboy", "egirl", "catboy", "off"}, id -> {
			Style st = Style.byId(id);
			if (st != null) browse = st; // od razu przeglądaj pulę wybranego stylu
			applyToSelected(t -> t.setStyle(id));
		}, () -> focus.target.styleId());
		ry += 6;
		// przełącznik puli do przeglądania
		addBtn("Pula: " + browse.label + " (" + SkinManager.poolCount(browse) + ")", rx, ry, rw, () -> {
			browse = browse.next();
			rebuild();
		});
		ry += 22;
		// przeglądarka skinów
		cycler(() -> {
			String k = focus.target.skinKey();
			if (k == null) return "losowy skin";
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
				return slotName(slot) + ": " + (it == null ? "—" : it.label);
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
			addBtn(k.label, rx + col * (bw + 4), ry, bw, () -> applyToSelected(t -> t.setOutfit(k.outfit())));
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
		cycler(() -> "Futro: " + colorLabel(focus.target.outfit().fur, true),
			() -> stepColor(true, -1), () -> stepColor(true, 1));
		cycler(() -> "Dodatki: " + colorLabel(focus.target.outfit().color, false),
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
		content.add(addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> onPress.run()).dimensions(x, y, w, 20).build()));
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
		ctx.drawText(textRenderer, Text.literal("klik = zaznacz; zmiany dotyczą wszystkich zaznaczonych").formatted(Formatting.DARK_GRAY),
			130, 14, 0xFFFFFFFF, false);
		drawList(ctx, mouseX, mouseY);
		drawPreview(ctx, mouseX, mouseY);
		// wartości cyklerów
		for (Cycler cc : cyclers) {
			if (cc.y < contentTop() || cc.y + 20 > contentBottom()) continue;
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
		String txt = sk != null ? ("skin: " + orDash(SkinManager.labelOf(sk))) : (d != null ? d : "oryginał");
		int tw = textRenderer.getWidth(txt);
		ctx.drawText(textRenderer, Text.literal(trim(txt, pvX2 - pvX1 - 6)).formatted(Formatting.LIGHT_PURPLE),
			cx - Math.min(tw, pvX2 - pvX1 - 6) / 2, pvY2 - 12, 0xFFFFFFFF, false);
		ctx.drawText(textRenderer, Text.literal(trim(focus.target.label(), pvX2 - pvX1 - 6)).formatted(Formatting.WHITE),
			pvX1 + 4, pvY1 + 4, 0xFFFFFFFF, false);
	}

	private void flat(DrawContext ctx, int cx, int cy) {
		String key = focus.target.skinKey();
		Identifier tex = key != null ? SkinManager.previewTexture(key) : null;
		if (tex != null) {
			int s = 64;
			// twarz 8x8 z (8,8)
			ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, tex, cx - s / 2, cy - s / 2, 8f, 8f, s, s, 8, 8, 64, 64);
		} else {
			String m = "wejdź bliżej, by zobaczyć podgląd";
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
			case EARS -> "Uszy";
			case TAIL -> "Ogon";
			case NECK -> "Szyja";
			case HEAD -> "Włosy";
			case FACE -> "Twarz";
			case BACK -> "Plecy";
			case ARMS -> "Ręce";
			case LEGS -> "Nogi";
			case PAWS -> "Łapki";
			case SKIRT -> "Spódnica";
		};
	}

	private static String colorLabel(String s, boolean fur) {
		if (s == null || "auto".equalsIgnoreCase(s)) return fur ? "jak włosy" : "losowy";
		int rgb = Palette.parse(s);
		return rgb < 0 ? s : Palette.label(rgb);
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
