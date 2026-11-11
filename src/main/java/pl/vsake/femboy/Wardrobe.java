package pl.vsake.femboy;

import static pl.vsake.femboy.Lang.tr;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import pl.vsake.femboy.acc.Accessories;
import pl.vsake.femboy.acc.Outfit;

import java.util.List;
import java.util.UUID;

/**
 * Szafa: kilka zapisanych strojów (dodatki z kolorami + opcjonalnie konkretny skin) w config/femboy.json.
 * Zapisuje się je i zakłada w oknie moda (zakładka Szafa), a klawiszem przełącza na sobie kolejne.
 */
public final class Wardrobe {
	private Wardrobe() {
	}

	public static final int MAX = 24;
	private static KeyBinding key;
	/** Który strój mam teraz na sobie z szafy (-1 = żaden / zmieniony ręcznie). */
	private static int current = -1;

	public static void init() {
		key = Keys.register("Ogólne", "Następny strój z szafy", "wardrobe", GLFW.GLFW_KEY_O);
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (key.wasPressed()) if (mc.player != null && mc.currentScreen == null) next();
		});
	}

	public static List<FemboyConfig.Saved> all() {
		return FemboyMod.CONFIG.wardrobe;
	}

	public static int current() {
		return current;
	}

	/** Zapisuje strój pod nazwą (taka sama nazwa = nadpisanie). @return indeks albo -1, gdy szafa pełna */
	public static int save(String name, Outfit outfit, String skin) {
		FemboyConfig cfg = FemboyMod.CONFIG;
		String n = name == null ? "" : name.trim();
		if (n.length() > 24) n = n.substring(0, 24);
		if (n.isEmpty()) n = freeName();
		FemboyConfig.Saved s = null;
		int idx = -1;
		for (int i = 0; i < cfg.wardrobe.size(); i++)
			if (cfg.wardrobe.get(i).name.equalsIgnoreCase(n)) {
				s = cfg.wardrobe.get(i);
				idx = i;
			}
		if (s == null) {
			if (cfg.wardrobe.size() >= MAX) return -1;
			s = new FemboyConfig.Saved();
			cfg.wardrobe.add(s);
			idx = cfg.wardrobe.size() - 1;
		}
		s.name = n;
		s.outfit = outfit == null ? new Outfit() : outfit.copy();
		s.skin = skin;
		cfg.save();
		return idx;
	}

	/** Pierwsza wolna nazwa „Strój N”. */
	public static String freeName() {
		for (int i = 1; ; i++) {
			String n = tr("Strój ") + i;
			boolean taken = false;
			for (FemboyConfig.Saved s : all()) taken |= s.name.equalsIgnoreCase(n);
			if (!taken) return n;
		}
	}

	public static void remove(int index) {
		List<FemboyConfig.Saved> w = all();
		if (index < 0 || index >= w.size()) return;
		w.remove(index);
		if (current == index) current = -1;
		else if (current > index) current--;
		FemboyMod.CONFIG.save();
	}

	public static FemboyConfig.Saved byName(String name) {
		for (FemboyConfig.Saved s : all()) if (s.name.equalsIgnoreCase(name)) return s;
		return null;
	}

	/** Zakładam strój z szafy na siebie. */
	public static boolean wear(int index) {
		MinecraftClient mc = MinecraftClient.getInstance();
		List<FemboyConfig.Saved> w = all();
		if (mc.player == null || index < 0 || index >= w.size()) return false;
		FemboyConfig cfg = FemboyMod.CONFIG;
		FemboyConfig.Saved s = w.get(index);
		UUID me = mc.player.getUuid();
		cfg.acc = true;
		cfg.accMe = s.outfit.copy();
		if (s.skin != null) cfg.playerOrCreate(me, mc.player.getGameProfile().name()).skin = s.skin;
		cfg.save();
		SkinManager.invalidate(me);
		Accessories.changed();
		current = index;
		mc.player.sendMessage(Text.literal(tr("Strój: ")).formatted(Formatting.WHITE).append(Text.literal(s.name).formatted(Formatting.LIGHT_PURPLE))
			.append(Text.literal(" (" + (index + 1) + "/" + w.size() + ")").formatted(Formatting.GRAY)), true);
		return true;
	}

	/** Klawisz: kolejny strój z szafy. */
	public static void next() {
		MinecraftClient mc = MinecraftClient.getInstance();
		int n = all().size();
		if (n == 0) {
			if (mc.player != null) mc.player.sendMessage(Text.literal(tr("Szafa jest pusta – zapisz strój w oknie moda (zakładka Szafa)")).formatted(Formatting.GRAY), true);
			return;
		}
		wear(Math.floorMod(current + 1, n));
	}
}
