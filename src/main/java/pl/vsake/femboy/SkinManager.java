package pl.vsake.femboy;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import pl.vsake.femboy.gen.SkinGenerator;
import pl.vsake.femboy.gen.SkinPixels;
import pl.vsake.femboy.gen.Style;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Serce moda: dla każdego UUID wybiera (i trzyma w pamięci) skin zastępczy.
 * Źródła: skiny PNG wbudowane w jar (assets/femboy/skins/&lt;styl&gt;/, pobrane z laby.net), własne PNG
 * z config/femboy/skins/&lt;styl&gt;/ oraz generator proceduralny. Peleryna i elytra zostają oryginalne – podmieniamy ciało i model.
 */
public final class SkinManager {
	private SkinManager() {
	}

	/** Gotowa decyzja dla jednego gracza. asset == null → zostaw oryginalny skin. */
	private static final class Entry {
		final Style style;
		final PoolSkin pool;      // skin z pliku (współdzielony) albo null
		final Identifier texture; // tylko wygenerowane – nasza tekstura do zwolnienia
		final AssetInfo.TextureAsset asset;
		final PlayerSkinType model;
		final boolean self;
		volatile Memo memo;

		Entry(Style style, PoolSkin pool, Identifier texture, AssetInfo.TextureAsset asset, PlayerSkinType model, boolean self) {
			this.style = style;
			this.pool = pool;
			this.texture = texture;
			this.asset = asset;
			this.model = model;
			this.self = self;
		}

		static Entry original(boolean self) {
			return new Entry(null, null, null, null, null, self);
		}
	}

	/** Ostatni oryginał → ostatni wynik (żeby nie tworzyć obiektu co klatkę). */
	private record Memo(SkinTextures in, SkinTextures out) {
	}

	/** Skin z pliku. Tekstura wczytuje się dopiero, gdy ktoś go dostanie. */
	private static final class PoolSkin {
		final Style style;
		final String name;     // ścieżka względem folderu stylu (z "/"), np. 5dad…_slim.png
		final Path file;       // plik na dysku albo wewnątrz jara moda
		final boolean bundled; // wbudowany w jar
		final Identifier id;
		AssetInfo.TextureAsset asset;
		PlayerSkinType model;
		boolean failed;
		int users;

		PoolSkin(Style style, String name, Path file, boolean bundled, Identifier id) {
			this.style = style;
			this.name = name;
			this.file = file;
			this.bundled = bundled;
			this.id = id;
		}

		/** Klucz do zapisu w configu / wyboru w GUI: "styl/plik", np. femboy/5dad…_slim.png. */
		String key() {
			return style.id + "/" + name;
		}

		/** Do /femboy list: skiny z laby.net mają w nazwie hash strony (laby.net/skins/&lt;hash&gt;). */
		String label() {
			String base = name.substring(name.lastIndexOf('/') + 1).replaceAll("(?i)\\.png$", "");
			return base.matches("[0-9a-f]{32}_(slim|wide)") ? "laby.net " + base.substring(0, 8) : base;
		}
	}

	private static final Map<UUID, Entry> CACHE = new ConcurrentHashMap<>();
	private static final Map<Style, List<PoolSkin>> POOL = new EnumMap<>(Style.class);
	private static boolean poolScanned;
	private static long counter;
	private static Path dir;
	private static final int MAX_PER_WINDOW = 6;
	private static final long WINDOW_NS = 20_000_000L;
	private static long windowStart;
	private static int createdInWindow;

	private static final String SKINS_README = """
		Skiny dla Femboy Skins
		======================
		Skiny z laby.net są wbudowane w mod (w pliku .jar) – tutaj nic nie trzeba kopiować.
		Tu możesz dorzucić własne PNG (64x64 albo 64x32):
		  femboy/  – skiny dla femboyów
		  egirl/   – skiny dla e-girls
		  catboy/  – skiny dla catboyów
		Potem wpisz w grze: /femboy reload. Twoje skiny dołączają do puli razem z wbudowanymi.
		Model (chudy/szeroki) rozpoznaje się sam; można go wymusić słowem "slim"
		albo "wide" w nazwie pliku.
		""";

	public static void init(Path folder) {
		dir = folder;
		try {
			for (Style st : Style.values()) Files.createDirectories(skinsDir().resolve(st.id));
			// plik moda – zawsze aktualny opis (starsza wersja zostawiała tu nieaktualny tekst)
			Path readme = skinsDir().resolve("README.txt");
			String old = null;
			try {
				old = Files.readString(readme);
			} catch (IOException ignored) { // brak pliku albo dziwne kodowanie – i tak piszemy od nowa
			}
			if (!SKINS_README.equals(old)) Files.writeString(readme, SKINS_README);
			Files.createDirectories(exportDir());
		} catch (IOException e) {
			FemboyMod.LOG.warn("Nie mogę przygotować folderu {}: {}", folder, e.toString());
		}
		removeOldLabyCopies();
	}

	/**
	 * Wersja 1.1.0 kopiowała skiny z laby.net do config/femboy/skins/&lt;styl&gt;/laby/. Teraz są w jarze,
	 * więc te kopie byłyby duplikatami. Usuwamy tylko nasze pliki (nazwa jak z laby.net) – przy starcie gry,
	 * zanim cokolwiek je wczyta. Cudze pliki w tym folderze zostają i działają jak własne skiny.
	 */
	private static void removeOldLabyCopies() {
		for (Style st : Style.values()) {
			Path old = skinsDir().resolve(st.id).resolve("laby");
			if (!Files.isDirectory(old)) continue;
			int removed = 0;
			try {
				List<Path> files;
				try (Stream<Path> s = Files.list(old)) {
					files = s.toList();
				}
				for (Path f : files) {
					String n = f.getFileName().toString();
					if (Files.isRegularFile(f) && (n.matches("[0-9a-f]{32}_(slim|wide)\\.png") || n.equals("zrodla.json"))) {
						Files.delete(f);
						removed++;
					}
				}
				boolean empty;
				try (Stream<Path> s = Files.list(old)) {
					empty = s.findAny().isEmpty();
				}
				if (empty) Files.delete(old);
			} catch (IOException | UncheckedIOException e) {
				FemboyMod.LOG.warn("Nie mogę posprzątać {}: {}", old, e.toString());
			}
			if (removed > 0) FemboyMod.LOG.info("Usunięto {} starych kopii skinów z {} (są teraz wbudowane w mod)", removed, old);
		}
	}

	public static Path skinsDir() {
		return dir.resolve("skins");
	}

	public static Path exportDir() {
		return dir.resolve("export");
	}

	public static boolean isOurs(SkinTextures s) {
		return s != null && s.body() != null && FemboyMod.ID.equals(s.body().texturePath().getNamespace());
	}

	// ======================== wejście z mixinów ========================

	public static SkinTextures apply(UUID uuid, String name, SkinTextures original) {
		FemboyConfig cfg = FemboyMod.CONFIG;
		if (!cfg.enabled || uuid == null || original == null || isOurs(original)) return original;
		Entry e = CACHE.get(uuid);
		if (e == null) {
			if (!RenderSystem.isOnRenderThread()) return original; // tekstury tworzymy tylko na wątku renderowania
			// limit nowych skinów na klatkę – pełny serwer po /femboy reroll nie daje przycięcia (słaby PC)
			long now = System.nanoTime();
			if (now - windowStart > WINDOW_NS) {
				windowStart = now;
				createdInWindow = 0;
			}
			if (createdInWindow >= MAX_PER_WINDOW) return original;
			createdInWindow++;
			e = create(uuid, name);
			CACHE.put(uuid, e);
		}
		if (e.asset == null || (e.self && !cfg.self)) return original;
		Memo m = e.memo;
		if (m == null || m.in != original) {
			m = new Memo(original, new SkinTextures(e.asset, original.cape(), original.elytra(), e.model, original.secure()));
			e.memo = m;
		}
		return m.out;
	}

	private static Entry create(UUID uuid, String name) {
		FemboyConfig cfg = FemboyMod.CONFIG;
		FemboyConfig.PlayerSetting ps = cfg.player(uuid);
		boolean self = isSelf(uuid, name);
		if (ps != null && "off".equals(ps.style)) return Entry.original(self);
		try {
			Style style = styleFor(uuid, ps);
			// wymuszony konkretny skin: najpierw gracza, potem globalny (GUI „wszyscy”)
			String forced = ps != null && ps.skin != null ? ps.skin : cfg.skinAll;
			if (forced != null) {
				PoolSkin p = findByKey(forced);
				if (p != null && load(p)) {
					p.users++;
					return new Entry(p.style, p, null, p.asset, p.model, self);
				}
			}
			long seed = lookSeed(uuid, ps);
			if (usesPool(seed)) {
				PoolSkin p = pick(pool(style), seed);
				if (p != null) {
					p.users++;
					return new Entry(style, p, null, p.asset, p.model, self);
				}
			}
			Identifier id = Identifier.of(FemboyMod.ID, "gen/" + (counter++) + "_" + uuid.toString().replace("-", ""));
			NativeImage img = toImage(SkinGenerator.generate(style, seed));
			String label = "femboy " + style.id + " " + name;
			MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(() -> label, img));
			return new Entry(style, null, id, new AssetInfo.TextureAssetInfo(id, id), PlayerSkinType.SLIM, self);
		} catch (Throwable t) {
			FemboyMod.LOG.error("Nie udało się zrobić skina dla {}", name, t);
			return Entry.original(self);
		}
	}

	// ======================== losowanie ========================

	static long mix64(long z) {
		z = (z ^ (z >>> 33)) * 0xFF51AFD7ED558CCDL;
		z = (z ^ (z >>> 33)) * 0xC4CEB9FE1A85EC53L;
		return z ^ (z >>> 33);
	}

	private static int salt(FemboyConfig.PlayerSetting ps) {
		return ps == null ? 0 : ps.salt;
	}

	public static Style styleFor(UUID uuid, FemboyConfig.PlayerSetting ps) {
		if (ps != null) {
			Style forced = Style.byId(ps.style);
			if (forced != null) return forced;
		}
		FemboyConfig cfg = FemboyMod.CONFIG;
		if ("femboy".equals(cfg.mode)) return Style.FEMBOY;
		if ("egirl".equals(cfg.mode)) return Style.EGIRL;
		if ("catboy".equals(cfg.mode)) return Style.CATBOY;
		long h = mix64(uuid.getMostSignificantBits() ^ mix64(uuid.getLeastSignificantBits() ^ cfg.seed ^ salt(ps) * 0x94D049BB133111EBL));
		// mix: po równo ze wszystkich stylów (femboy / e-girl / catboy)
		return Style.values()[(int) Math.floorMod(h, (long) Style.values().length)];
	}

	public static long lookSeed(UUID uuid, FemboyConfig.PlayerSetting ps) {
		FemboyConfig cfg = FemboyMod.CONFIG;
		return mix64(uuid.getMostSignificantBits() * 0x9E3779B97F4A7C15L ^ uuid.getLeastSignificantBits()
			^ cfg.seed * 0xD1B54A32D192ED03L ^ salt(ps) * 0x2545F4914F6CDD1DL ^ 0x5F656D626F79L);
	}

	/** skins = pliki (jak brak – generator), generated = tylko generator, mix = pół na pół. */
	private static boolean usesPool(long seed) {
		return switch (FemboyMod.CONFIG.source) {
			case "generated" -> false;
			case "mix" -> (mix64(seed ^ 0x6D69785F736B696EL) & 1) == 0;
			default -> true;
		};
	}

	/** Wybór z puli: najpierw "swój" skin z ziarna, a jeśli już ktoś go nosi – następny wolny. */
	private static PoolSkin pick(List<PoolSkin> pool, long seed) {
		int n = pool.size();
		if (n == 0) return null;
		int base = Math.floorMod(seed, n);
		for (int pass = 0; pass < 2; pass++)
			for (int k = 0; k < n; k++) {
				PoolSkin p = pool.get((base + k) % n);
				if (p.failed || (pass == 0 && p.users > 0)) continue;
				if (load(p)) return p;
			}
		return null;
	}

	public static boolean isSelf(UUID uuid, String name) {
		MinecraftClient c = MinecraftClient.getInstance();
		if (c.player != null && uuid.equals(c.player.getUuid())) return true;
		if (c.uuidEquals(uuid)) return true;
		return name != null && name.equals(c.getSession().getUsername());
	}

	// ======================== pamięć ========================

	/** Wszystko od nowa (po zmianie trybu, reroll itp.). */
	public static void invalidateAll() {
		TextureManager tm = MinecraftClient.getInstance().getTextureManager();
		for (Entry e : CACHE.values()) if (e.texture != null) tm.destroyTexture(e.texture);
		CACHE.clear();
		for (List<PoolSkin> l : POOL.values()) for (PoolSkin p : l) p.users = 0;
	}

	public static void invalidate(UUID uuid) {
		Entry e = CACHE.remove(uuid);
		if (e == null) return;
		if (e.texture != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(e.texture);
		if (e.pool != null && e.pool.users > 0) e.pool.users--;
	}

	public static void clearGenerated() {
		invalidateAll();
	}

	// ======================== skiny z plików ========================

	private static List<PoolSkin> pool(Style st) {
		if (!poolScanned) scanPool();
		return POOL.getOrDefault(st, List.of());
	}

	private static void scanPool() {
		poolScanned = true;
		for (Style st : Style.values()) {
			List<PoolSkin> list = new ArrayList<>();
			Path inJar = bundledDir(st);
			if (inJar != null) addPngs(list, st, inJar, true);
			int bundled = list.size();
			addPngs(list, st, skinsDir().resolve(st.id), false);
			POOL.put(st, list);
			FemboyMod.LOG.info("Skiny {}: {} wbudowanych + {} własnych", st.id, bundled, list.size() - bundled);
		}
	}

	/** Folder ze skinami wbudowanymi w jar moda (assets/femboy/skins/&lt;styl&gt;/); null = brak. */
	private static Path bundledDir(Style st) {
		try {
			return FabricLoader.getInstance().getModContainer(FemboyMod.ID)
				.flatMap(m -> m.findPath("assets/" + FemboyMod.ID + "/skins/" + st.id))
				.filter(Files::isDirectory)
				.orElse(null);
		} catch (RuntimeException e) {
			FemboyMod.LOG.warn("Nie mogę otworzyć wbudowanych skinów {}: {}", st.id, e.toString());
			return null;
		}
	}

	private static void addPngs(List<PoolSkin> list, Style st, Path folder, boolean bundled) {
		List<Path> files;
		try (Stream<Path> s = Files.walk(folder, 4)) {
			files = s.filter(Files::isRegularFile)
				.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
				.sorted(Comparator.comparing(Path::toString))
				.toList();
		} catch (NoSuchFileException e) {
			return;
		} catch (IOException | UncheckedIOException e) {
			FemboyMod.LOG.warn("Nie mogę przeczytać folderu {}: {}", folder, e.toString());
			return;
		}
		for (Path f : files) {
			String rel = folder.relativize(f).toString().replace('\\', '/');
			Identifier id = Identifier.of(FemboyMod.ID, "pool/" + st.id + "/" + list.size() + "_" + sanitize(f.getFileName().toString()));
			list.add(new PoolSkin(st, rel, f, bundled, id));
		}
	}

	/** Wczytuje teksturę skina z pliku (obróbka jak w vanilla: stare 64x32, krycie bazowej warstwy). */
	private static boolean load(PoolSkin p) {
		if (p.asset != null) return true;
		if (p.failed) return false;
		try {
			int w, h;
			int[] px;
			try (InputStream in = Files.newInputStream(p.file); NativeImage raw = NativeImage.read(in)) {
				w = raw.getWidth();
				h = raw.getHeight();
				if (!SkinPixels.validSize(w, h)) {
					p.failed = true;
					FemboyMod.LOG.warn("Pomijam {} – skin musi mieć 64x64 albo 64x32 pikseli (ma {}x{})", p.file, w, h);
					return false;
				}
				px = new int[w * h];
				for (int y = 0; y < h; y++)
					for (int x = 0; x < w; x++) px[y * w + x] = raw.getColorArgb(x, y);
			}
			PlayerSkinType model = modelFromName(p.name);
			if (model == null) model = SkinPixels.looksSlim(px, w, h) ? PlayerSkinType.SLIM : PlayerSkinType.WIDE;
			NativeImage img = toImage(SkinPixels.remap(px, w, h));
			MinecraftClient.getInstance().getTextureManager().registerTexture(p.id, new NativeImageBackedTexture(() -> "femboy " + p.name, img));
			p.model = model;
			p.asset = new AssetInfo.TextureAssetInfo(p.id, p.id);
			return true;
		} catch (Exception e) {
			p.failed = true;
			FemboyMod.LOG.warn("Nie mogę wczytać skina {}: {}", p.file, e.toString());
			return false;
		}
	}

	/** Skanuje pliki od nowa. Zwraca łączną liczbę skinów we wszystkich pulach. */
	public static int reloadPool() {
		invalidateAll();
		TextureManager tm = MinecraftClient.getInstance().getTextureManager();
		for (List<PoolSkin> l : POOL.values()) for (PoolSkin p : l) if (p.asset != null) tm.destroyTexture(p.id);
		POOL.clear();
		poolScanned = false;
		int n = 0;
		for (Style st : Style.values()) n += poolCount(st);
		return n;
	}

	public static int poolCount(Style st) {
		return pool(st).size();
	}

	// ======================== wybór skina (GUI) ========================

	/** Jeden skin z puli dla GUI: klucz do zapisu, czytelna etykieta i tekstura do podglądu. */
	public record SkinOption(String key, String label, Style style) {
	}

	private static PoolSkin findByKey(String key) {
		if (key == null) return null;
		int slash = key.indexOf('/');
		if (slash < 0) return null;
		Style st = Style.byId(key.substring(0, slash));
		if (st == null) return null;
		String name = key.substring(slash + 1);
		for (PoolSkin p : pool(st)) if (p.name.equals(name)) return p;
		return null;
	}

	/** Lista skinów danego stylu do przeglądania w GUI (kolejność jak w puli). */
	public static List<SkinOption> options(Style st) {
		List<SkinOption> out = new ArrayList<>();
		for (PoolSkin p : pool(st)) out.add(new SkinOption(p.key(), p.label(), p.style));
		return out;
	}

	/** Czytelna etykieta skina spod klucza (do napisów w GUI); null, jeśli nie ma takiego. */
	public static String labelOf(String key) {
		PoolSkin p = findByKey(key);
		return p == null ? null : p.label();
	}

	/**
	 * Tekstura skina do płaskiego podglądu w GUI; null, jeśli w tej klatce nie da się jej wczytać
	 * (nie na wątku renderowania albo plik się nie wczytał). Wołane z ekranu, na wątku gry.
	 */
	public static Identifier previewTexture(String key) {
		PoolSkin p = findByKey(key);
		if (p == null) return null;
		if (p.asset != null) return p.id;
		if (!RenderSystem.isOnRenderThread()) return null;
		return load(p) ? p.id : null;
	}

	/** Ile z puli jest wbudowanych w jar (reszta to własne pliki z config). */
	public static int bundledCount(Style st) {
		int n = 0;
		for (PoolSkin p : pool(st)) if (p.bundled) n++;
		return n;
	}

	/** Model wymuszony nazwą pliku ("slim"/"alex" albo "wide"/"steve"/"classic"); null = rozpoznaj z pikseli. */
	private static PlayerSkinType modelFromName(String file) {
		String f = file.toLowerCase(Locale.ROOT);
		if (f.contains("slim") || f.contains("alex")) return PlayerSkinType.SLIM;
		if (f.contains("wide") || f.contains("steve") || f.contains("classic")) return PlayerSkinType.WIDE;
		return null;
	}

	private static String sanitize(String s) {
		String out = s.toLowerCase(Locale.ROOT).replaceAll("\\.png$", "").replaceAll("[^a-z0-9._-]", "_");
		return out.isEmpty() ? "skin" : out;
	}

	// ======================== informacje / eksport ========================

	/** Co gracz ma teraz (do /femboy list). null = oryginalny skin. */
	public static String describe(UUID uuid, String name) {
		FemboyConfig cfg = FemboyMod.CONFIG;
		FemboyConfig.PlayerSetting ps = cfg.player(uuid);
		if (!cfg.enabled) return null;
		if (ps != null && "off".equals(ps.style)) return null;
		if (!cfg.self && isSelf(uuid, name)) return null;
		Entry e = CACHE.get(uuid);
		Style st = e != null && e.style != null ? e.style : styleFor(uuid, ps);
		String what = st.label;
		if (e == null) return what + " (jeszcze nie widziany)";
		if (e.asset == null) return null;
		return what + (e.pool != null ? " (" + e.pool.label() + ")" : " (generowany)");
	}

	private static NativeImage toImage(int[] argb) {
		NativeImage img = new NativeImage(NativeImage.Format.RGBA, SkinGenerator.SIZE, SkinGenerator.SIZE, true);
		for (int y = 0; y < SkinGenerator.SIZE; y++)
			for (int x = 0; x < SkinGenerator.SIZE; x++) img.setColorArgb(x, y, argb[y * SkinGenerator.SIZE + x]);
		return img;
	}

	/** Zapisuje skin, który gracz ma, do PNG – można go potem ustawić jako swój prawdziwy skin. */
	public static Path export(UUID uuid, String name) throws IOException {
		FemboyConfig.PlayerSetting ps = FemboyMod.CONFIG.player(uuid);
		Entry e = CACHE.get(uuid);
		Style st = e != null && e.style != null ? e.style : styleFor(uuid, ps);
		Files.createDirectories(exportDir());
		Path out = exportDir().resolve(sanitize(name) + "_" + st.id + ".png");
		long seed = lookSeed(uuid, ps);
		PoolSkin p = e != null ? e.pool : (usesPool(seed) ? pick(pool(st), seed) : null);
		if (p != null) {
			Files.copy(p.file, out, StandardCopyOption.REPLACE_EXISTING);
		} else {
			try (NativeImage img = toImage(SkinGenerator.generate(st, lookSeed(uuid, ps)))) {
				img.writeTo(out);
			}
		}
		return out;
	}
}
