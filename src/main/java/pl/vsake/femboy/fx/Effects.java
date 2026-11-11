package pl.vsake.femboy.fx;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import pl.vsake.femboy.FemboyConfig;
import pl.vsake.femboy.FemboyMod;
import pl.vsake.femboy.acc.Accessories;
import pl.vsake.femboy.acc.Item;
import pl.vsake.femboy.acc.Look;
import pl.vsake.femboy.pat.PatPat;
import pl.vsake.femboy.voice.Voices;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Efekty wokół postaci, liczone u każdego osobno z tego, co i tak wszyscy wiedzą (stroje i głaskanie są przesyłane):
 * dzwoneczki dzwonią przy chodzeniu, serduszka/iskierki unoszą się nad głową (stały efekt z dodatków albo przy głaskaniu),
 * a głaskany kot mruczy, dopóki się go głaszcze.
 */
public final class Effects {
	private Effects() {
	}

	private static final SoundEvent BELL = SoundEvent.of(Identifier.of(FemboyMod.ID, "bell"));
	private static final SoundEvent PURR = SoundEvent.of(Identifier.of(FemboyMod.ID, "purr_loop"));
	private static final Random RANDOM = Random.create();
	/** Co ile bloków drogi dzwoni dzwoneczek. */
	private static final double BELL_EVERY = 1.4;

	private static final class Walk {
		double x, y, z, dist;
		int quiet;
	}

	private static final Map<UUID, Walk> WALK = new HashMap<>();
	private static final Map<Integer, Purr> PURRS = new HashMap<>();
	private static int ticks;

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(Effects::tick);
	}

	private static void tick(MinecraftClient mc) {
		if (mc.world == null || mc.player == null) {
			if (!WALK.isEmpty()) clear();
			return;
		}
		if (mc.isPaused()) return;
		ticks++;
		FemboyConfig cfg = FemboyMod.CONFIG;
		boolean firstPerson = mc.options.getPerspective().isFirstPerson();
		for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
			if (p.isSpectator() || p.isInvisible()) continue;
			Look look = Accessories.current(p.getUuid(), p.getGameProfile().name());
			bells(mc, p, look, cfg);
			if (look != null && !(firstPerson && p == mc.player)) aura(mc, p, look);
		}
		PatPat.forEachPatted(mc, e -> {
			// serduszka nad głaskanym (także nad mobem)
			if (cfg.patHearts && ticks % 6 == 0 && !(firstPerson && e == mc.player))
				mc.world.addParticleClient(ParticleTypes.HEART, e.getX() + spread(0.35), e.getY() + e.getHeight() + 0.35 + spread(0.1),
					e.getZ() + spread(0.35), 0, 0.02, 0);
			if (cfg.patPurr && e instanceof PlayerEntity pl && !PURRS.containsKey(e.getId())) {
				Look look = Accessories.current(pl.getUuid(), pl.getGameProfile().name());
				float vol = 0.75f * Voices.volume(pl.getUuid());
				if (look != null && look.isCat() && vol > 0f) {
					Purr purr = new Purr(e, vol);
					PURRS.put(e.getId(), purr);
					mc.getSoundManager().play(purr);
				}
			}
		});
		// mruczenie, które nie wystartowało albo zostało ucięte przez silnik dźwięku – żeby mogło zacząć się od nowa
		if (ticks % 40 == 0 && !PURRS.isEmpty())
			PURRS.values().removeIf(p -> ticks - p.born >= 40 && !mc.getSoundManager().isPlaying(p));
		if (ticks % 200 == 0) { // gracze, którzy wyszli z zasięgu
			Set<UUID> here = new HashSet<>();
			for (AbstractClientPlayerEntity p : mc.world.getPlayers()) here.add(p.getUuid());
			WALK.keySet().retainAll(here);
		}
	}

	private static void bells(MinecraftClient mc, AbstractClientPlayerEntity p, Look look, FemboyConfig cfg) {
		Walk w = WALK.get(p.getUuid());
		if (w == null) {
			w = new Walk();
			w.x = p.getX();
			w.y = p.getY();
			w.z = p.getZ();
			WALK.put(p.getUuid(), w);
		}
		double dx = p.getX() - w.x, dy = p.getY() - w.y, dz = p.getZ() - w.z;
		w.x = p.getX();
		w.y = p.getY();
		w.z = p.getZ();
		if (w.quiet > 0) w.quiet--;
		if (!cfg.bells || look == null || !look.hasBell() || p.hasVehicle()) {
			w.dist = 0;
			return;
		}
		double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (d > 6) return; // teleport, nie spacer
		w.dist += d;
		if (w.dist < BELL_EVERY || w.quiet > 0) return;
		w.dist = 0;
		w.quiet = 3;
		if (Voices.muted(p.getUuid())) return;
		// skradanie = ciszej (dzwoneczek przytrzymany)
		float vol = (p.isSneaking() ? 0.12f : 0.3f) * (0.85f + RANDOM.nextFloat() * 0.3f);
		mc.getSoundManager().play(new PositionedSoundInstance(BELL, SoundCategory.PLAYERS, vol, 1.55f + RANDOM.nextFloat() * 0.45f,
			RANDOM, p.getX(), p.getY() + p.getHeight() * 0.8, p.getZ()));
	}

	/** Stały efekt z dodatków (miejsce „efekt”): serduszka albo iskierki. */
	private static void aura(MinecraftClient mc, AbstractClientPlayerEntity p, Look look) {
		Item fx = look.get(Item.Slot.FX);
		if (fx == null) return;
		int phase = p.getId() * 7;
		if (fx == Item.FX_HEARTS) {
			if ((ticks + phase) % 18 == 0)
				mc.world.addParticleClient(ParticleTypes.HEART, p.getX() + spread(0.3), p.getY() + p.getHeight() + 0.3 + spread(0.1),
					p.getZ() + spread(0.3), 0, 0.02, 0);
		} else if (fx == Item.FX_SPARKLES) {
			if ((ticks + phase) % 3 == 0)
				mc.world.addParticleClient(ParticleTypes.WAX_OFF, p.getX() + spread(0.45), p.getY() + 0.2 + RANDOM.nextDouble() * (p.getHeight() + 0.3),
					p.getZ() + spread(0.45), spread(0.4), 0.3 + RANDOM.nextDouble() * 0.4, spread(0.4));
		}
	}

	private static double spread(double r) {
		return (RANDOM.nextDouble() * 2 - 1) * r;
	}

	/** Mruczenie zapętlone przy głaskanym kocie: jedzie za nim, po głaskaniu cichnie i się kończy. */
	private static final class Purr extends MovingSoundInstance {
		private final LivingEntity cat;
		private final float full;
		private final int born = ticks;

		Purr(LivingEntity cat, float full) {
			super(PURR, SoundCategory.PLAYERS, Random.create());
			this.cat = cat;
			this.full = full;
			this.repeat = true;
			this.repeatDelay = 0;
			this.volume = full * 0.15f;
			this.pitch = 0.95f + RANDOM.nextFloat() * 0.15f;
			follow();
		}

		private void follow() {
			x = cat.getX();
			y = cat.getY() + cat.getHeight() * 0.8;
			z = cat.getZ();
		}

		@Override
		public void tick() {
			boolean on = !cat.isRemoved() && FemboyMod.CONFIG.patPurr && PatPat.patted(cat);
			if (on) volume = Math.min(full, volume + full * 0.12f);
			else {
				volume -= full * 0.1f;
				if (volume <= 0.001f) {
					volume = 0f;
					PURRS.remove(cat.getId(), this);
					setDone();
					return;
				}
			}
			follow();
		}
	}

	/** Po rozłączeniu. */
	public static void clear() {
		WALK.clear();
		PURRS.clear(); // same się skończą – głaskany zniknął
	}
}
