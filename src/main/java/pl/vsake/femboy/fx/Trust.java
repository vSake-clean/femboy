package pl.vsake.femboy.fx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import pl.vsake.femboy.FemboyConfig;
import pl.vsake.femboy.FemboyMod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Na kogo reaguję: clicker, smycz, zabawki (laser, włóczka) i noszenie. Każdą z tych rzeczy włączam u siebie sam,
 * a tu dodatkowo mogę zawęzić ją do wybranych osób. Lista to zwykłe nicki – w oknie moda wpisuje się je w polu tekstowym,
 * jeden nick w linijce (wielkość liter bez znaczenia).
 */
public final class Trust {
	private Trust() {
	}

	public enum What {CLICKER, LEASH, LASER, CARRY}

	public static final int MAX_NAMES = 100, MAX_NAME = 32;

	private static String key(What w) {
		return w.name().toLowerCase(Locale.ROOT);
	}

	/** Tekst z pola → lista nicków: jeden w linijce (przecinek i średnik też dzielą), bez pustych i powtórzeń. Czysta Java. */
	public static List<String> parse(String text) {
		List<String> out = new ArrayList<>();
		if (text == null) return out;
		for (String line : text.split("[\\r\\n,;]+")) {
			String n = line.trim();
			if (n.isEmpty()) continue;
			if (n.length() > MAX_NAME) n = n.substring(0, MAX_NAME);
			if (!has(out, n) && out.size() < MAX_NAMES) out.add(n);
		}
		return out;
	}

	public static boolean has(List<String> names, String name) {
		if (names == null || name == null) return false;
		for (String n : names) if (n.equalsIgnoreCase(name)) return true;
		return false;
	}

	public static List<String> names(What w) {
		List<String> l = FemboyMod.CONFIG.trustNames.get(key(w));
		return l == null ? List.of() : l;
	}

	/** Lista do pola tekstowego: nick pod nickiem. */
	public static String text(What w) {
		return String.join("\n", names(w));
	}

	/** Zapis tego, co wpisano w polu. */
	public static void setText(What w, String text) {
		List<String> l = parse(text);
		FemboyConfig cfg = FemboyMod.CONFIG;
		if (l.isEmpty()) cfg.trustNames.remove(key(w));
		else cfg.trustNames.put(key(w), l);
		cfg.save();
	}

	private static String nameOf(UUID who) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (who == null || mc == null) return null;
		if (mc.world != null) {
			PlayerEntity p = mc.world.getPlayerByUuid(who);
			if (p != null) return p.getGameProfile().name();
		}
		if (mc.getNetworkHandler() != null) {
			PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(who);
			if (e != null) return e.getProfile().name();
		}
		return null;
	}

	/** Czy ta osoba jest na liście. */
	public static boolean picked(What w, UUID who) {
		List<String> l = names(w);
		return !l.isEmpty() && has(l, nameOf(who));
	}

	public static void setPicked(What w, UUID who, String name, boolean on) {
		if (name == null) name = nameOf(who);
		if (name == null) return;
		List<String> l = new ArrayList<>(names(w));
		String nick = name;
		l.removeIf(n -> n.equalsIgnoreCase(nick));
		if (on) l.add(name);
		setText(w, String.join("\n", l));
	}

	/** Czy reaguję na tę osobę (funkcja musi być i tak włączona osobno). Noszenie: osoby z listy = zgoda od razu, bez pytania. */
	public static boolean allows(What w, UUID who) {
		FemboyConfig cfg = FemboyMod.CONFIG;
		boolean onlyPicked = switch (w) {
			case CLICKER -> cfg.clickerPicked;
			case LEASH -> cfg.leashPicked;
			case LASER -> cfg.laserPicked;
			case CARRY -> true;
		};
		return !onlyPicked || picked(w, who);
	}
}
