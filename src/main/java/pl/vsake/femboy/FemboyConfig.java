package pl.vsake.femboy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import pl.vsake.femboy.acc.Kit;
import pl.vsake.femboy.acc.Outfit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Ustawienia w config/femboy.json – zapisywane po każdej komendzie. */
public final class FemboyConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public boolean enabled = true;
	/** femboy | egirl | mix */
	public String mode = "mix";
	/** skins = pliki PNG (pobrane z laby.net + własne), generated = generator, mix = pół na pół. */
	public String source = "skins";
	/** Czy zmieniać też twój własny skin. */
	public boolean self = true;
	/** Czy podmieniać główki na liście graczy (TAB). */
	public boolean tab = true;
	/** Wymuszaj warstwy skina (włosy 3D, kaptur, spódniczka), nawet gdy gracz je wyłączył. */
	public boolean layers = true;
	/** Głaskanie (pat pat): trzymasz klawisz (domyślnie R, zmiana w Sterowaniu) celując w kogoś. */
	public boolean pat = true;
	/** Piszczenie przy każdym klepnięciu. */
	public boolean patSound = true;
	/** Ziarno losowania wyglądu – /femboy reroll je zmienia. */
	public long seed = 0;
	/** Dodatki 3D (uszy, ogony, obroże…) – niezależne od podmiany skinów (/femboy on|off). */
	public boolean acc = true;
	/** random = każdy coś losowego, same = każdy ma strój accAll, me = tylko ja (i gracze z własnym strojem). */
	public String accMode = "random";
	/** Mój strój; null = według trybu. */
	public Outfit accMe;
	/** Strój dla wszystkich w trybie same. */
	public Outfit accAll = Kit.CATBOY.outfit();
	/** Zestawy, z których losuje tryb random. */
	public List<String> accKits = new ArrayList<>(Kit.ids());
	/** Ziarno losowania dodatków – /femboy acc reroll je zmienia. */
	public long accSeed = 0;
	/** Ustawienia pojedynczych graczy, klucz = UUID. */
	public Map<String, PlayerSetting> players = new LinkedHashMap<>();

	public static final class PlayerSetting {
		/** Ostatnio widziany nick – tylko dla czytelności pliku. */
		public String name;
		/** femboy | egirl | off | null (= według trybu). */
		public String style;
		/** Zwiększane przez /femboy reroll gracz. */
		public int salt;
		/** Własny strój z dodatkami; null = według trybu. */
		public Outfit outfit;
		/** Zwiększane przez /femboy acc reroll gracz. */
		public int accSalt;
	}

	private transient Path path;

	public static FemboyConfig load(Path path) {
		FemboyConfig cfg = null;
		if (Files.isRegularFile(path)) {
			try {
				cfg = GSON.fromJson(Files.readString(path), FemboyConfig.class);
			} catch (Exception e) {
				FemboyMod.LOG.warn("Uszkodzony plik {}, biorę domyślne ustawienia: {}", path, e.toString());
			}
		}
		if (cfg == null) cfg = new FemboyConfig();
		if (cfg.players == null) cfg.players = new LinkedHashMap<>();
		if (!"femboy".equals(cfg.mode) && !"egirl".equals(cfg.mode)) cfg.mode = "mix";
		if (!"generated".equals(cfg.source) && !"mix".equals(cfg.source)) cfg.source = "skins";
		if (!"same".equals(cfg.accMode) && !"me".equals(cfg.accMode)) cfg.accMode = "random";
		if (cfg.accAll == null) cfg.accAll = Kit.CATBOY.outfit();
		// tylko znane zestawy, bez powtórzeń; pusta lista = wszystkie
		List<String> kits = new ArrayList<>();
		if (cfg.accKits != null)
			for (String id : cfg.accKits) {
				Kit k = Kit.byId(id);
				if (k != null && !kits.contains(k.id)) kits.add(k.id);
			}
		cfg.accKits = kits.isEmpty() ? new ArrayList<>(Kit.ids()) : kits;
		cfg.path = path;
		cfg.save();
		return cfg;
	}

	public void save() {
		if (path == null) return;
		try {
			Files.createDirectories(path.getParent());
			Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(this));
			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (Exception e) {
			FemboyMod.LOG.warn("Nie mogę zapisać {}: {}", path, e.toString());
		}
	}

	public PlayerSetting player(UUID id) {
		return players.get(id.toString());
	}

	public PlayerSetting playerOrCreate(UUID id, String name) {
		PlayerSetting ps = players.computeIfAbsent(id.toString(), k -> new PlayerSetting());
		if (name != null) ps.name = name;
		return ps;
	}

	/** Usuwa wpis gracza, jeśli nic już w nim nie zostało. */
	public void tidy(UUID id) {
		PlayerSetting ps = players.get(id.toString());
		if (ps != null && ps.style == null && ps.salt == 0 && ps.outfit == null && ps.accSalt == 0) players.remove(id.toString());
	}
}
