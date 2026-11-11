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
	/** femboy | egirl | catboy | mix */
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
	/** Odgłosy (miau, hau…) pod klawiszami: false = nie słyszę żadnych (cudzych ani swoich). */
	public boolean voice = true;
	/** Ogólna głośność odgłosów w procentach (0–100), mnożona przez głośność pojedynczego gracza. */
	public int voiceVolume = 100;
	/** Które warianty odgłosu losuję, gdy go wydaję (klucz = id odgłosu, np. "hiss"); brak wpisu = wszystkie. */
	public Map<String, List<Integer>> voiceVariants = new LinkedHashMap<>();
	/** Clicker: false (domyślnie) = nie słyszę clickerów i nie mogę klikać; true = słyszę i sam obracam się do klikającego. */
	public boolean clicker = false;
	/** Smycz: true = pozwalam się naprawdę ciągnąć prowadzącemu (linkę widać zawsze). */
	public boolean leashPull = false;
	/** Clicker: true = reaguję tylko na osoby zaznaczone w oknie moda (PlayerSetting.okClicker). */
	public boolean clickerPicked = false;
	/** Smycz: true = ciągnąć mogą mnie tylko osoby zaznaczone w oknie moda (PlayerSetting.okLeash). */
	public boolean leashPicked = false;
	/** Smycz, na której sam jestem: false (domyślnie) = nie widzę linki przy sobie. */
	public boolean leashSeeOwn = false;
	/** Zabawki innych graczy (kropka lasera, kłębek włóczki) są u mnie widoczne. */
	public boolean toys = true;
	/** Wskaźnik laserowy: true = sam idę za kropką (domyślnie wyłączone, jak clicker). */
	public boolean laserFollow = false;
	/** Wskaźnik: true = idę tylko za kropką osób zaznaczonych w oknie moda (PlayerSetting.okLaser). */
	public boolean laserPicked = false;
	/** Włóczka: true = sam gonię cudze kłębki (domyślnie wyłączone, jak laser). Lista osób ta sama co przy laserze. */
	public boolean yarnFollow = false;
	/** Na kogo reaguję: "clicker" / "leash" / "laser" / "carry" → nicki (wpisywane w oknie moda, jeden w linijce). */
	public Map<String, List<String>> trustNames = new LinkedHashMap<>();
	/** Ślady łapek na śniegu i piasku. */
	public boolean pawPrints = true;
	/** Mruganie (co kilka sekund i przy boopie). */
	public boolean blink = true;
	/** Animacje, które włączają się same: bochenek przy długim kucaniu, przeciąganie po spaniu / bezczynności. */
	public boolean autoPoses = true;
	/** Ile razy mnie pogłaskano (pokazywane na identyfikatorze przy obroży, wysyłane innym z modem). */
	public long pats = 0;
	/** Szafa: zapisane stroje do szybkiego przełączania. */
	public List<Saved> wardrobe = new ArrayList<>();
	/** Dzwoneczki przy dodatkach dzwonią przy chodzeniu. */
	public boolean bells = true;
	/** Głaskany kot mruczy. */
	public boolean patPurr = true;
	/** Serduszka nad głaskanym. */
	public boolean patHearts = true;
	/** Uszy i ogon reagują na odgłosy i obrażenia. */
	public boolean react = true;
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
	/** Wymuszony konkretny skin dla wszystkich (klucz puli "styl/plik"); null = losowanie według trybu. Ustawiane z GUI „wszyscy”. */
	public String skinAll;
	/** Zestawy, z których losuje tryb random. */
	public List<String> accKits = new ArrayList<>(Kit.ids());
	/** Ziarno losowania dodatków – /femboy acc reroll je zmienia. */
	public long accSeed = 0;
	/** Ustawienia pojedynczych graczy, klucz = UUID. */
	public Map<String, PlayerSetting> players = new LinkedHashMap<>();

	/** Strój w szafie: dodatki z kolorami i (opcjonalnie) konkretny skin. */
	public static final class Saved {
		public String name;
		public Outfit outfit;
		/** Klucz skina z puli albo null (skin zostaje, jaki jest). */
		public String skin;
	}

	public static final class PlayerSetting {
		/** Ostatnio widziany nick – tylko dla czytelności pliku. */
		public String name;
		/** femboy | egirl | catboy | off | null (= według trybu). */
		public String style;
		/** Wymuszony konkretny skin (klucz puli "styl/plik"); null = losowanie z puli według ziarna. */
		public String skin;
		/** Zwiększane przez /femboy reroll gracz. */
		public int salt;
		/** Własny strój z dodatkami; null = według trybu. */
		public Outfit outfit;
		/** Zwiększane przez /femboy acc reroll gracz. */
		public int accSalt;
		/** Głośność odgłosów tego gracza w procentach (0–100); null = 100. */
		public Integer voiceVolume;
		/** Wyciszony – nie słyszę jego odgłosów (głośność zostaje zapamiętana na po odciszeniu). */
		public boolean voiceMuted;
		/** Zaufanie: reaguję na clicker tej osoby / daję się jej ciągnąć / idę za jej laserem / od razu zgadzam się na noszenie. */
		public boolean okClicker, okLeash, okLaser, okCarry;

		public boolean anyTrust() {
			return okClicker || okLeash || okLaser || okCarry;
		}
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
		if (!"femboy".equals(cfg.mode) && !"egirl".equals(cfg.mode) && !"catboy".equals(cfg.mode)) cfg.mode = "mix";
		if (!"generated".equals(cfg.source) && !"mix".equals(cfg.source)) cfg.source = "skins";
		if (!"same".equals(cfg.accMode) && !"me".equals(cfg.accMode)) cfg.accMode = "random";
		cfg.voiceVolume = Math.max(0, Math.min(100, cfg.voiceVolume));
		// tylko znane odgłosy i istniejące warianty, bez powtórzeń; pusta lista = wszystkie (wpis znika)
		Map<String, List<Integer>> vv = new LinkedHashMap<>();
		if (cfg.voiceVariants != null)
			cfg.voiceVariants.forEach((id, list) -> {
				pl.vsake.femboy.voice.Voice v = pl.vsake.femboy.voice.Voice.byId(id);
				if (v == null || list == null) return;
				List<Integer> ok = new ArrayList<>();
				for (Integer i : list) if (i != null && i >= 0 && i < v.variants() && !ok.contains(i)) ok.add(i);
				if (!ok.isEmpty() && ok.size() < v.variants()) vv.put(v.id, ok);
			});
		cfg.voiceVariants = vv;
		for (PlayerSetting ps : cfg.players.values())
			if (ps != null && ps.voiceVolume != null) ps.voiceVolume = Math.max(0, Math.min(100, ps.voiceVolume));
		if (cfg.accAll == null) cfg.accAll = Kit.CATBOY.outfit();
		// listy nicków: bez śmieci; stare zaznaczenia „na gracza” (1.7.0 przed listami) przechodzą na nicki
		Map<String, List<String>> tn = new LinkedHashMap<>();
		if (cfg.trustNames != null)
			cfg.trustNames.forEach((k, list) -> {
				if (k == null || list == null) return;
				List<String> ok = pl.vsake.femboy.fx.Trust.parse(String.join("\n", list.stream().filter(java.util.Objects::nonNull).toList()));
				if (!ok.isEmpty()) tn.put(k, ok);
			});
		for (PlayerSetting ps : cfg.players.values()) {
			if (ps == null || !ps.anyTrust()) continue;
			if (ps.name != null && !ps.name.isBlank()) {
				boolean[] on = {ps.okClicker, ps.okLeash, ps.okLaser, ps.okCarry};
				String[] keys = {"clicker", "leash", "laser", "carry"};
				for (int i = 0; i < 4; i++) {
					if (!on[i]) continue;
					List<String> l = tn.computeIfAbsent(keys[i], x -> new ArrayList<>());
					if (!pl.vsake.femboy.fx.Trust.has(l, ps.name)) l.add(ps.name);
				}
			}
			ps.okClicker = ps.okLeash = ps.okLaser = ps.okCarry = false;
		}
		cfg.trustNames = tn;
		cfg.pats = Math.max(0, cfg.pats);
		List<Saved> saved = new ArrayList<>();
		if (cfg.wardrobe != null)
			for (Saved sv : cfg.wardrobe)
				if (sv != null && sv.outfit != null && saved.size() < 24) {
					if (sv.name == null || sv.name.isBlank()) sv.name = "Strój " + (saved.size() + 1);
					if (sv.name.length() > 24) sv.name = sv.name.substring(0, 24);
					saved.add(sv);
				}
		cfg.wardrobe = saved;
		// tylko znane zestawy, bez powtórzeń; pusta lista = wszystkie
		List<String> kits = new ArrayList<>();
		if (cfg.accKits != null)
			for (String id : cfg.accKits) {
				Kit k = Kit.byId(id);
				if (k != null && !kits.contains(k.id)) kits.add(k.id);
			}
		// config sprzed 1.5 miał wszystkie 6 starych zestawów włączonych → dołóż nowe (miś, diabełek, aniołek, wróżka)
		if (kits.size() == 6 && kits.containsAll(List.of("catboy", "puppy", "fox", "wolf", "bunny", "femboy"))) kits = new ArrayList<>(Kit.ids());
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
		if (ps != null && ps.style == null && ps.skin == null && ps.salt == 0 && ps.outfit == null && ps.accSalt == 0
			&& (ps.voiceVolume == null || ps.voiceVolume == 100) && !ps.voiceMuted && !ps.anyTrust()) players.remove(id.toString());
	}
}
