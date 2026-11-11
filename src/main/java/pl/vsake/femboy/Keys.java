package pl.vsake.femboy;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wszystkie klawisze moda w jednym miejscu: te same są w Sterowaniu → Femboy Skins i w oknie moda (zakładka „Klawisze”).
 * Zmiana z okna zapisuje się w options.txt dokładnie tak, jak zmiana w Sterowaniu.
 */
public final class Keys {
	private Keys() {
	}

	/** @param group nagłówek w oknie moda („Odgłosy”, „Emotki”…), @param label krótka nazwa */
	public record Entry(String group, String label, KeyBinding key) {
	}

	private static final List<Entry> ALL = new ArrayList<>();
	/** Wspólna kategoria klawiszy – rejestrować ją można tylko raz (drugi raz = crash przy starcie). */
	private static KeyBinding.Category category;

	public static KeyBinding.Category category() {
		if (category == null) category = KeyBinding.Category.create(Identifier.of(FemboyMod.ID, "main"));
		return category;
	}

	/**
	 * @param id  końcówka klucza tłumaczenia: key.femboy.ID
	 * @param def GLFW_KEY_… albo GLFW_KEY_UNKNOWN (bez klawisza)
	 */
	public static KeyBinding register(String group, String label, String id, int def) {
		KeyBinding k = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.femboy." + id, InputUtil.Type.KEYSYM, def, category()));
		ALL.add(new Entry(group, label, k));
		return k;
	}

	public static List<Entry> all() {
		return Collections.unmodifiableList(ALL);
	}

	/** Przypisz klawisz (InputUtil.UNKNOWN_KEY = bez klawisza). */
	public static void bind(KeyBinding k, InputUtil.Key key) {
		if (k == null) return;
		k.setBoundKey(key);
		KeyBinding.updateKeysByCode();
		MinecraftClient.getInstance().options.write();
	}

	/** Nazwa klawisza do komunikatów ("—" = bez klawisza). */
	public static String name(KeyBinding k) {
		if (k == null || k.isUnbound()) return "—";
		return k.getBoundKeyLocalizedText().getString();
	}
}
