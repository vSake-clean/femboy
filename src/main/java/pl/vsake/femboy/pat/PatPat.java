package pl.vsake.femboy.pat;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.lwjgl.glfw.GLFW;
import pl.vsake.femboy.FemboyMod;
import pl.vsake.femboy.Keys;
import pl.vsake.femboy.net.NetClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Pat pat (petpet): trzymasz klawisz celując w kogoś → ręka z GIF-a petpet klepie go po głowie,
 * dopóki nie puścisz. Animacja jak w generatorze petpet: 10 klatek, ręka zmienia się co 2 klatki,
 * obrazek (u nas głowa) ściska się najmocniej w klatce 5.
 */
public final class PatPat {
	private PatPat() {
	}

	/** Jedna klatka GIF-a (oryginał ma 20 ms – w grze trochę wolniej, żeby było widać ruch). */
	public static final int FRAME_MS = 30;
	private static final int FRAMES = 10;
	private static final int CYCLE_MS = FRAME_MS * FRAMES;
	/** Z jakiej odległości można zacząć głaskać. */
	private static final double REACH = 6.0;
	/** Jak daleko głaskany może odejść, zanim ręka zniknie. */
	private static final double KEEP = 10.0;

	private static final SoundEvent SOUND = SoundEvent.of(Identifier.of(FemboyMod.ID, "pat"));
	private static final Random RANDOM = Random.create();

	private static KeyBinding key;
	private static LivingEntity target;
	private static long startMs;
	private static long soundCycle = -1;

	/** Cudze głaskanie (od graczy z modem, przez serwer): kto (id sieciowe) → kogo. */
	private static final class Remote {
		final int target;
		final long startMs;
		long seenMs;
		long soundCycle = -1;
		/** Ile klepnięć tej serii już policzyłem (licznik głaskań – tylko gdy głaskany to ja). */
		long counted;

		Remote(int target, long now) {
			this.target = target;
			this.startMs = now;
			this.seenMs = now;
		}
	}

	private static final Map<Integer, Remote> REMOTE = new HashMap<>();
	/** Głaszczący odświeża co sekundę – bez odświeżenia przez 3 s uznajemy, że przestał (zgubiony pakiet, wyszedł z zasięgu). */
	private static final long REMOTE_TIMEOUT_MS = 3000, RESEND_MS = 1000;
	private static int sentTarget = -1;
	private static long sentMs;

	public static void init() {
		key = Keys.register("Ogólne", "Pat pat (trzymaj)", "pat", GLFW.GLFW_KEY_R);
		ClientTickEvents.END_CLIENT_TICK.register(PatPat::tick);
	}

	public static KeyBinding key() {
		return key;
	}

	/** Czy ktoś (ja albo inny gracz z modem) głaszcze teraz tę istotę – bez skutków ubocznych. */
	public static boolean patted(LivingEntity e) {
		if (e == target) return true;
		if (REMOTE.isEmpty()) return false;
		int id = e.getId();
		for (Remote r : REMOTE.values()) if (r.target == id) return true;
		return false;
	}

	/** Wszystkie głaskane teraz istoty (każda raz). */
	public static void forEachPatted(MinecraftClient mc, java.util.function.Consumer<LivingEntity> each) {
		if (target != null) each.accept(target);
		if (REMOTE.isEmpty() || mc.world == null) return;
		java.util.Set<Integer> done = new java.util.HashSet<>();
		if (target != null) done.add(target.getId());
		for (Remote r : REMOTE.values())
			if (done.add(r.target) && mc.world.getEntityById(r.target) instanceof LivingEntity l) each.accept(l);
	}

	/** Nazwa klawisza do komunikatów (gracz mógł go zmienić w Sterowaniu). */
	public static String keyName() {
		return key == null ? "?" : key.getBoundKeyLocalizedText().getString();
	}

	private static void tick(MinecraftClient mc) {
		update(mc);
		sync(mc);
	}

	/** Mówi serwerowi, kogo głaszczę – przy zmianie i co sekundę, póki głaszczę. */
	private static void sync(MinecraftClient mc) {
		int id = target == null || mc.world == null ? -1 : target.getId();
		long now = Util.getMeasuringTimeMs();
		if (id == sentTarget && (id < 0 || now - sentMs < RESEND_MS)) return;
		sentTarget = id;
		sentMs = now;
		NetClient.sendPat(id < 0 ? null : target);
	}

	/** Pakiet od serwera: patter głaszcze target (-1 = przestał). */
	public static void remote(int patter, int targetId) {
		long now = Util.getMeasuringTimeMs();
		if (targetId < 0) {
			REMOTE.remove(patter);
			return;
		}
		Remote r = REMOTE.get(patter);
		if (r != null && r.target == targetId) r.seenMs = now;
		else REMOTE.put(patter, new Remote(targetId, now));
	}

	/** Po rozłączeniu. */
	public static void clearRemote() {
		REMOTE.clear();
		sentTarget = -1;
	}

	private static void update(MinecraftClient mc) {
		if (!REMOTE.isEmpty()) {
			long now = Util.getMeasuringTimeMs();
			REMOTE.entrySet().removeIf(en -> now - en.getValue().seenMs > REMOTE_TIMEOUT_MS
				|| mc.world == null || mc.world.getEntityById(en.getKey()) == null);
		}
		countMine(mc);
		if (!FemboyMod.CONFIG.pat || mc.world == null || mc.player == null || !key.isPressed()) {
			stop();
			return;
		}
		if (target != null && !valid(mc, target)) stop();
		if (target == null) { // trzymasz klawisz i dopiero celujesz – zaczynamy, gdy ktoś wejdzie pod celownik
			LivingEntity t = aim(mc);
			if (t != null) {
				target = t;
				startMs = Util.getMeasuringTimeMs();
				soundCycle = -1;
			}
		}
	}

	public static void stop() {
		target = null;
	}

	private static long patsSavedMs;
	private static boolean patsDirty;

	/** Licznik głaskań: każde klepnięcie, które ktoś z modem daje MNIE, liczy mój klient (i wysyła innym razem ze strojem). */
	private static void countMine(MinecraftClient mc) {
		long now = Util.getMeasuringTimeMs();
		if (mc.player != null && !REMOTE.isEmpty()) {
			int me = mc.player.getId();
			for (Remote r : REMOTE.values()) {
				if (r.target != me) continue;
				// liczy się moment, w którym ręka dociska (klatka 4 GIF-a) – wtedy też słychać piśnięcie
				long done = (Math.min(now, r.seenMs + RESEND_MS) - r.startMs + CYCLE_MS - 4L * FRAME_MS) / CYCLE_MS;
				if (done > r.counted) {
					FemboyMod.CONFIG.pats += done - r.counted;
					r.counted = done;
					patsDirty = true;
				}
			}
		}
		if (patsDirty && now - patsSavedMs > 5000) {
			patsDirty = false;
			patsSavedMs = now;
			FemboyMod.CONFIG.save();
		}
	}

	/** Licznik głaskań gracza: mój z ustawień, cudzy z tego, co sam przysłał; -1 = nieznany. */
	public static long count(net.minecraft.entity.player.PlayerEntity p) {
		MinecraftClient mc = MinecraftClient.getInstance();
		return p == mc.player ? FemboyMod.CONFIG.pats : NetClient.pats(p.getUuid());
	}

	private static boolean valid(MinecraftClient mc, LivingEntity e) {
		return !e.isRemoved() && e.isAlive() && e.getEntityWorld() == mc.world && e.squaredDistanceTo(mc.player) <= KEEP * KEEP;
	}

	/** Istota pod celownikiem (gracz albo mob), nie dalej niż REACH i nie przez ściany. */
	public static LivingEntity aim(MinecraftClient mc) {
		Entity cam = mc.getCameraEntity();
		if (cam == null) return null;
		float tp = mc.getRenderTickCounter().getTickProgress(true);
		Vec3d from = cam.getCameraPosVec(tp);
		Vec3d dir = cam.getRotationVec(tp);
		Vec3d to = from.add(dir.multiply(REACH));
		double maxSq = REACH * REACH;
		HitResult block = cam.raycast(REACH, tp, false);
		if (block.getType() != HitResult.Type.MISS) maxSq = block.getPos().squaredDistanceTo(from);
		Box box = cam.getBoundingBox().stretch(dir.multiply(REACH)).expand(1.0);
		EntityHitResult hit = ProjectileUtil.raycast(cam, from, to, box,
			e -> e instanceof LivingEntity && e != mc.player && !e.isSpectator() && e.isAlive(), maxSq);
		return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
	}

	/**
	 * Czas animacji w klatkach GIF-a (0..10) dla tej istoty albo -1, gdy nikt jej nie głaszcze.
	 * Wołane przy każdym rysowaniu – tu też pada piśnięcie (raz na klepnięcie, gdy ręka dociska).
	 */
	public static float timeFor(LivingEntity e) {
		if (e == target) {
			long elapsed = Util.getMeasuringTimeMs() - startMs;
			long cycle = elapsed / CYCLE_MS;
			float t = (elapsed % CYCLE_MS) / (float) FRAME_MS;
			if (t >= 4f && cycle != soundCycle) {
				soundCycle = cycle;
				if (FemboyMod.CONFIG.patSound) squeak(e);
			}
			return t;
		}
		if (REMOTE.isEmpty()) return -1f; // cudze głaskanie widać zawsze, nawet z wyłączonym własnym
		int id = e.getId();
		for (Remote r : REMOTE.values()) {
			if (r.target != id) continue;
			long elapsed = Util.getMeasuringTimeMs() - r.startMs;
			long cycle = elapsed / CYCLE_MS;
			float t = (elapsed % CYCLE_MS) / (float) FRAME_MS;
			if (t >= 4f && cycle != r.soundCycle) {
				r.soundCycle = cycle;
				if (FemboyMod.CONFIG.patSound) squeak(e);
			}
			return t;
		}
		return -1f;
	}

	private static void squeak(LivingEntity e) {
		float pitch = 0.95f + RANDOM.nextFloat() * 0.25f;
		MinecraftClient.getInstance().getSoundManager().play(new PositionedSoundInstance(SOUND, SoundCategory.PLAYERS,
			0.8f, pitch, RANDOM, e.getX(), e.getY() + e.getHeight(), e.getZ()));
	}
}
