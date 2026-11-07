package pl.vsake.femboy.pat;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
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

	public static void init() {
		KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(FemboyMod.ID, "main"));
		key = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.femboy.pat", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, category));
		ClientTickEvents.END_CLIENT_TICK.register(PatPat::tick);
	}

	/** Nazwa klawisza do komunikatów (gracz mógł go zmienić w Sterowaniu). */
	public static String keyName() {
		return key == null ? "?" : key.getBoundKeyLocalizedText().getString();
	}

	private static void tick(MinecraftClient mc) {
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

	private static boolean valid(MinecraftClient mc, LivingEntity e) {
		return !e.isRemoved() && e.isAlive() && e.getEntityWorld() == mc.world && e.squaredDistanceTo(mc.player) <= KEEP * KEEP;
	}

	/** Istota pod celownikiem (gracz albo mob), nie dalej niż REACH i nie przez ściany. */
	private static LivingEntity aim(MinecraftClient mc) {
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
		if (e != target) return -1f;
		long elapsed = Util.getMeasuringTimeMs() - startMs;
		long cycle = elapsed / CYCLE_MS;
		float t = (elapsed % CYCLE_MS) / (float) FRAME_MS;
		if (t >= 4f && cycle != soundCycle) {
			soundCycle = cycle;
			if (FemboyMod.CONFIG.patSound) squeak(e);
		}
		return t;
	}

	private static void squeak(LivingEntity e) {
		float pitch = 0.95f + RANDOM.nextFloat() * 0.25f;
		MinecraftClient.getInstance().getSoundManager().play(new PositionedSoundInstance(SOUND, SoundCategory.PLAYERS,
			0.8f, pitch, RANDOM, e.getX(), e.getY() + e.getHeight(), e.getZ()));
	}
}
