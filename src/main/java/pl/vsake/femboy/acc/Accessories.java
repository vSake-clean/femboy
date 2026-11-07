package pl.vsake.femboy.acc;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.Identifier;
import pl.vsake.femboy.FemboyConfig;
import pl.vsake.femboy.FemboyMod;
import pl.vsake.femboy.SkinManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Kto jakie dodatki nosi i tekstury do nich.
 * Wygląd liczy się raz na gracza i trzyma do zmiany ustawień albo skina (kolor "auto" bierze się z włosów na skinie).
 * Tekstura jest jedna na każdy inny wygląd: malowana w locie, zwalniana, kiedy nikt jej nie nosi od pół minuty.
 * Wszystko dzieje się na wątku gry/renderowania.
 */
public final class Accessories {
	private Accessories() {
	}

	private record Seen(int version, Identifier skin, int hair, Look look) {
	}

	private static final class Tex {
		/** null = nie udało się zrobić (nie próbujemy w kółko). */
		final Identifier id;
		long lastUsed;

		Tex(Identifier id, long now) {
			this.id = id;
			this.lastUsed = now;
		}
	}

	private static final Map<UUID, Seen> SEEN = new HashMap<>();
	private static final Map<Identifier, Integer> HAIR = new HashMap<>();
	private static final Map<String, Tex> TEXTURES = new HashMap<>();
	/** Zmienia się przy każdej zmianie ustawień – wtedy wszystkie wyglądy liczą się od nowa. */
	private static int version;
	private static long counter;
	private static int ticks;
	private static List<Kit> kits;
	private static int kitsVersion = -1;
	private static boolean warned;
	// najwyżej kilka nowych tekstur na klatkę (słaby PC, pełny serwer po zmianie ustawień)
	private static final int MAX_PER_WINDOW = 4;
	private static final long WINDOW_NS = 20_000_000L;
	private static final long UNUSED_NS = 30_000_000_000L;
	private static long windowStart;
	private static int createdInWindow;

	/** Po każdej zmianie ustawień dodatków. */
	public static void changed() {
		version++;
	}

	/** Wywoływane przy wypełnianiu stanu gracza do rysowania (co klatkę). */
	public static Look lookFor(AbstractClientPlayerEntity player, SkinTextures skin) {
		if (!FemboyMod.CONFIG.acc) return null;
		UUID uuid = player.getUuid();
		Identifier skinId = skin != null && skin.body() != null ? skin.body().texturePath() : null;
		Seen s = SEEN.get(uuid);
		if (s != null && s.version == version && Objects.equals(s.skin, skinId)) return s.look;
		int hair = hairOf(skinId);
		Look look = compute(uuid, player.getGameProfile().name(), hair);
		SEEN.put(uuid, new Seen(version, skinId, hair, look));
		return look;
	}

	/** Co gracz nosi według ustawień (null = nic). */
	public static Look compute(UUID uuid, String name, int hair) {
		if (!FemboyMod.CONFIG.acc) return null;
		Look look = choose(uuid, name, hair);
		return look == null || look.isEmpty() ? null : look;
	}

	private static Look choose(UUID uuid, String name, int hair) {
		FemboyConfig cfg = FemboyMod.CONFIG;
		FemboyConfig.PlayerSetting ps = cfg.player(uuid);
		long seed = seed(uuid, ps);
		boolean self = SkinManager.isSelf(uuid, name);
		if (self && cfg.accMe != null) return cfg.accMe.resolve(hair, seed);
		if (!self && ps != null && ps.outfit != null) return ps.outfit.resolve(hair, seed);
		return switch (cfg.accMode) {
			case "same" -> cfg.accAll.resolve(hair, seed);
			case "me" -> self ? Kit.random(seed, kits(), hair) : null;
			default -> Kit.random(seed, kits(), hair);
		};
	}

	/** Ziarno dodatków gracza – inne niż ziarno skina, zmienia je /femboy acc reroll. */
	public static long seed(UUID uuid, FemboyConfig.PlayerSetting ps) {
		long salt = ps == null ? 0 : ps.accSalt;
		return Rng.mix(uuid.getMostSignificantBits() ^ Rng.mix(uuid.getLeastSignificantBits()
			^ FemboyMod.CONFIG.accSeed * 0x9E3779B97F4A7C15L ^ salt * 0xC2B2AE3D27D4EB4FL ^ 0xACCE55L));
	}

	/** Zestawy, z których losuje tryb random. */
	public static List<Kit> kits() {
		if (kits == null || kitsVersion != version) {
			List<Kit> l = new ArrayList<>();
			for (String id : FemboyMod.CONFIG.accKits) {
				Kit k = Kit.byId(id);
				if (k != null && !l.contains(k)) l.add(k);
			}
			kits = l.isEmpty() ? List.of(Kit.values()) : l;
			kitsVersion = version;
		}
		return kits;
	}

	/** Kolor włosów z ostatnio widzianego skina gracza albo -1. */
	public static int knownHair(UUID uuid) {
		Seen s = SEEN.get(uuid);
		return s == null ? -1 : s.hair;
	}

	/** Co gracz ma teraz, jako strój do dalszej zmiany (np. /femboy acc me tail fox, gdy był losowy). */
	public static Outfit editable(UUID uuid, String name) {
		int hair = knownHair(uuid);
		Look now = choose(uuid, name, hair);
		if (now == null || now.isEmpty()) return new Outfit();
		return Outfit.of(now, hair >= 0 && now.fur == hair);
	}

	/** Do /femboy list: "kocie uszy, koci ogon, …" albo null. */
	public static String describe(UUID uuid, String name) {
		Look l = compute(uuid, name, knownHair(uuid));
		return l == null ? null : l.describe();
	}

	// ======================== tekstury ========================

	/** Tekstura dla wyglądu; null, jeśli w tej klatce nie da się jej już zrobić. */
	public static Identifier texture(Look look) {
		long now = System.nanoTime();
		Tex t = TEXTURES.get(look.textureKey());
		if (t != null) {
			t.lastUsed = now;
			return t.id;
		}
		if (!RenderSystem.isOnRenderThread()) return null;
		if (now - windowStart > WINDOW_NS) {
			windowStart = now;
			createdInWindow = 0;
		}
		if (createdInWindow >= MAX_PER_WINDOW) return null;
		createdInWindow++;
		try {
			int[] px = Painter.paint(look);
			NativeImage img = new NativeImage(NativeImage.Format.RGBA, Parts.TEX_W, Parts.TEX_H, true);
			for (int y = 0; y < Parts.TEX_H; y++)
				for (int x = 0; x < Parts.TEX_W; x++) img.setColorArgb(x, y, px[y * Parts.TEX_W + x]);
			Identifier id = Identifier.of(FemboyMod.ID, "acc/" + (counter++));
			String label = "femboy accessories " + look.describe();
			MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(() -> label, img));
			TEXTURES.put(look.textureKey(), new Tex(id, now));
			return id;
		} catch (Throwable e) {
			failed(e);
			TEXTURES.put(look.textureKey(), new Tex(null, now));
			return null;
		}
	}

	/** Kolor włosów ze skina (czytany raz na teksturę). Domyślne skiny Steve/Alex itp. → -1. */
	private static int hairOf(Identifier skin) {
		if (skin == null) return -1;
		Integer known = HAIR.get(skin);
		if (known != null) return known;
		int hair = -1;
		try {
			AbstractTexture tex = MinecraftClient.getInstance().getTextureManager().getTexture(skin);
			if (tex instanceof NativeImageBackedTexture n) {
				NativeImage img = n.getImage();
				if (img != null && img.getWidth() == 64 && img.getHeight() == 64) {
					int[] px = new int[64 * 64];
					for (int y = 0; y < 64; y++)
						for (int x = 0; x < 64; x++) px[y * 64 + x] = img.getColorArgb(x, y);
					hair = Hair.detect(px, 64, 64);
				}
			}
		} catch (Throwable ignored) { // obrazek już zwolniony albo w innym formacie – futro dostanie kolor z palety
		}
		HAIR.put(skin, hair);
		return hair;
	}

	/** Co 5 s: zwalnia tekstury, których nikt nie nosi od pół minuty. */
	public static void tick() {
		if (++ticks % 100 != 0 || TEXTURES.isEmpty()) return;
		long now = System.nanoTime();
		TextureManager tm = MinecraftClient.getInstance().getTextureManager();
		TEXTURES.values().removeIf(t -> {
			if (now - t.lastUsed < UNUSED_NS) return false;
			if (t.id != null) tm.destroyTexture(t.id);
			return true;
		});
	}

	/** Po wyjściu ze świata/serwera. */
	public static void clear() {
		TextureManager tm = MinecraftClient.getInstance().getTextureManager();
		for (Tex t : TEXTURES.values()) if (t.id != null) tm.destroyTexture(t.id);
		TEXTURES.clear();
		SEEN.clear();
		HAIR.clear();
	}

	/** Po /femboy reload – skiny z plików mogły się zmienić, więc i kolory włosów. */
	public static void forgetSkins() {
		HAIR.clear();
		changed();
	}

	public static void failed(Throwable t) {
		if (warned) return;
		warned = true;
		FemboyMod.LOG.error("Dodatki (uszy, ogony…) nie działają – pomijam je", t);
	}
}
