package pl.vsake.femboy.gui;

import net.minecraft.client.MinecraftClient;
import pl.vsake.femboy.FemboyConfig;
import pl.vsake.femboy.FemboyMod;
import pl.vsake.femboy.SkinManager;
import pl.vsake.femboy.acc.Accessories;
import pl.vsake.femboy.acc.Kit;
import pl.vsake.femboy.acc.Outfit;

import java.util.Random;
import java.util.UUID;

/**
 * Co zmienia GUI: „wszyscy”, „ja” albo konkretny gracz. Każdy wariant pisze w inne miejsce
 * configu (globalne pola vs. wpis gracza vs. mój strój), ale ekran steruje nimi tak samo.
 */
public interface EditTarget {
	String label();

	/** null = „wszyscy” (nie jedna osoba). */
	UUID uuid();

	default boolean everyone() {
		return uuid() == null;
	}

	// ---- styl skina: "auto" | "femboy" | "egirl" | "catboy" | "off" ----
	String styleId();

	void setStyle(String id);

	// ---- wymuszony konkretny skin (klucz puli) albo null = losowy ----
	String skinKey();

	void setSkin(String key);

	// ---- dodatki ----
	/** Strój do edycji jednego dodatku – nigdy null (bierze to, co osoba ma teraz). */
	Outfit outfit();

	void setOutfit(Outfit o);

	/** Kolor włosów ze skina albo -1. */
	int hair();

	// ---- akcje ----
	void rerollSkin();

	void rerollAcc();

	void reset();

	/** Odśwież render po zmianie (podgląd aktualizuje się od następnej klatki). */
	void invalidate();

	static FemboyConfig cfg() {
		return FemboyMod.CONFIG;
	}

	// ======================== wszyscy ========================

	static EditTarget everyoneTarget() {
		return new EditTarget() {
			public String label() {
				return "Wszyscy";
			}

			public UUID uuid() {
				return null;
			}

			public String styleId() {
				FemboyConfig c = cfg();
				if (!c.enabled) return "off";
				return switch (c.mode) {
					case "femboy", "egirl", "catboy" -> c.mode;
					default -> "auto";
				};
			}

			public void setStyle(String id) {
				FemboyConfig c = cfg();
				if ("off".equals(id)) {
					c.enabled = false;
				} else {
					c.enabled = true;
					c.mode = switch (id) {
						case "femboy", "egirl", "catboy" -> id;
						default -> "mix";
					};
				}
			}

			public String skinKey() {
				return cfg().skinAll;
			}

			public void setSkin(String key) {
				cfg().skinAll = key;
			}

			public Outfit outfit() {
				return cfg().accAll.copy();
			}

			public void setOutfit(Outfit o) {
				FemboyConfig c = cfg();
				c.acc = true;
				c.accMode = "same";
				c.accAll = o == null ? Kit.CATBOY.outfit() : o;
			}

			public int hair() {
				return -1;
			}

			public void rerollSkin() {
				FemboyConfig c = cfg();
				long s;
				do s = new Random().nextLong(); while (s == c.seed);
				c.seed = s;
				c.skinAll = null;
			}

			public void rerollAcc() {
				FemboyConfig c = cfg();
				long s;
				do s = new Random().nextLong(); while (s == c.accSeed);
				c.accSeed = s;
			}

			public void reset() {
				FemboyConfig c = cfg();
				c.skinAll = null;
				c.mode = "mix";
				c.enabled = true;
			}

			public void invalidate() {
				cfg().save();
				SkinManager.invalidateAll();
				Accessories.changed();
			}
		};
	}

	// ======================== jeden gracz (albo ja) ========================

	static EditTarget playerTarget(UUID id, String name) {
		boolean self = SkinManager.isSelf(id, name);
		return new EditTarget() {
			public String label() {
				return self ? "Ja (" + name + ")" : name;
			}

			public UUID uuid() {
				return id;
			}

			public String styleId() {
				FemboyConfig.PlayerSetting ps = cfg().player(id);
				if (ps == null || ps.style == null) return "auto";
				return ps.style; // "femboy" | "egirl" | "catboy" | "off"
			}

			public void setStyle(String sid) {
				FemboyConfig.PlayerSetting ps = cfg().playerOrCreate(id, name);
				ps.style = "auto".equals(sid) ? null : sid;
				cfg().tidy(id);
			}

			public String skinKey() {
				FemboyConfig.PlayerSetting ps = cfg().player(id);
				return ps == null ? null : ps.skin;
			}

			public void setSkin(String key) {
				if (key == null) {
					FemboyConfig.PlayerSetting ps = cfg().player(id);
					if (ps != null) ps.skin = null;
					cfg().tidy(id);
				} else {
					cfg().playerOrCreate(id, name).skin = key;
				}
			}

			public Outfit outfit() {
				if (self) return cfg().accMe != null ? cfg().accMe.copy() : Accessories.editable(id, name);
				FemboyConfig.PlayerSetting ps = cfg().player(id);
				return ps != null && ps.outfit != null ? ps.outfit.copy() : Accessories.editable(id, name);
			}

			public void setOutfit(Outfit o) {
				cfg().acc = true;
				if (self) {
					cfg().accMe = o;
					return;
				}
				if (o != null) {
					cfg().playerOrCreate(id, name).outfit = o;
				} else {
					FemboyConfig.PlayerSetting ps = cfg().player(id);
					if (ps != null) ps.outfit = null;
					cfg().tidy(id);
				}
			}

			public int hair() {
				return Accessories.knownHair(id);
			}

			public void rerollSkin() {
				FemboyConfig.PlayerSetting ps = cfg().playerOrCreate(id, name);
				ps.skin = null;
				ps.salt++;
			}

			public void rerollAcc() {
				if (self) cfg().accMe = null;
				cfg().playerOrCreate(id, name).accSalt++;
				FemboyConfig.PlayerSetting ps = cfg().player(id);
				if (!self && ps != null) ps.outfit = null;
			}

			public void reset() {
				if (self) cfg().accMe = null;
				cfg().players.remove(id.toString());
			}

			public void invalidate() {
				cfg().save();
				SkinManager.invalidate(id);
				Accessories.changed();
			}
		};
	}

	static MinecraftClient mc() {
		return MinecraftClient.getInstance();
	}
}
