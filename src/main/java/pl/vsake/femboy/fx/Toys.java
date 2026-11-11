package pl.vsake.femboy.fx;

import static pl.vsake.femboy.Lang.tr;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;
import pl.vsake.femboy.FemboyConfig;
import pl.vsake.femboy.FemboyMod;
import pl.vsake.femboy.Keys;
import pl.vsake.femboy.acc.Accessories;
import pl.vsake.femboy.acc.Look;
import pl.vsake.femboy.net.NetClient;
import pl.vsake.femboy.net.Signal;
import pl.vsake.femboy.voice.Voices;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Zabawki, które widzą inni gracze z modem (tylko wizualnie, przez sieć moda):
 * wskaźnik laserowy – trzymam klawisz i świecę czerwoną kropką po blokach, koty wokół wodzą za nią głową, a kto sam
 * włączył u siebie „idę za kropką” (domyślnie wyłączone, jak clicker), ten za nią chodzi;
 * kłębek włóczki – rzucam go, odbija się, turla i można go kopać / gonić (każdy liczy jego ruch u siebie).
 */
public final class Toys {
	private Toys() {
	}

	/** Kropka lasera: gdzie ma być i gdzie ją rysuję (dogania cel, żeby ruch był płynny mimo 5 wiadomości na sekundę). */
	public static final class Dot {
		public Vec3d target, shown;
		long seenMs;
	}

	public static final class Ball {
		public double x, y, z, px, py, pz, vx, vy, vz;
		/** Obrót od toczenia (radiany) i oś, wokół której się toczy. */
		public float spin, lastSpin, axisX = 1f, axisZ;
		public int color;
		public UUID owner;
		int age, kickCd;
		boolean rest;
		/** Ile ticków już go gonię i czy mi się znudził (albo sam z niego zrezygnowałem). */
		int chased;
		boolean bored;
	}

	public static final double RADIUS = 0.17;
	private static final double LASER_REACH = 40, FOLLOW_RANGE = 16, LOOK_RANGE = 10;
	private static final long LASER_SEND_MS = 200, DOT_TIMEOUT_MS = 900;
	private static final int BALL_TICKS = 20 * 45, MAX_BALLS = 24, MAX_PER_OWNER = 3;
	/** Jeden kłębek gonię najwyżej tyle ticków; po własnym ruchu tyle ms nic mnie nie ciągnie. */
	private static final int CHASE_TICKS = 20 * 6;
	private static final long MANUAL_MS = 3000;

	private static final Map<UUID, Dot> DOTS = new HashMap<>();
	private static final List<Ball> BALLS = new ArrayList<>();
	private static final Random RANDOM = new Random();
	private static KeyBinding laserKey, yarnKey;
	private static long laserSentMs, yarnMs;
	private static boolean laserOn;
	/** Gonienie: do kiedy steruję sam, w którą stronę obróciło mnie ostatnio (NaN = nie obracało) i czy już o nim napisałem. */
	private static long manualUntilMs;
	private static float followYaw = Float.NaN;
	private static boolean followSaid;

	public static void init() {
		laserKey = Keys.register("Zabawki", "Wskaźnik laserowy (trzymaj)", "laser", GLFW.GLFW_KEY_U);
		yarnKey = Keys.register("Zabawki", "Rzuć kłębek włóczki", "yarn", GLFW.GLFW_KEY_I);
		ClientTickEvents.END_CLIENT_TICK.register(Toys::tick);
	}

	public static Map<UUID, Dot> dots() {
		return DOTS;
	}

	public static List<Ball> balls() {
		return BALLS;
	}

	private static void tick(MinecraftClient mc) {
		while (yarnKey.wasPressed()) if (mc.player != null && mc.currentScreen == null) throwYarn();
		if (mc.player == null || mc.world == null) {
			if (!DOTS.isEmpty() || !BALLS.isEmpty()) clear();
			return;
		}
		long now = Util.getMeasuringTimeMs();
		laser(mc, now);
		if (!DOTS.isEmpty()) {
			UUID me = mc.player.getUuid();
			DOTS.entrySet().removeIf(e -> !e.getKey().equals(me) && now - e.getValue().seenMs > DOT_TIMEOUT_MS);
			for (Dot d : DOTS.values()) d.shown = d.shown.lerp(d.target, 0.5);
		}
		follow(mc);
		if (!mc.isPaused()) for (int i = BALLS.size() - 1; i >= 0; i--) if (!step(mc.world, BALLS.get(i))) BALLS.remove(i);
	}

	// ======================== laser ========================

	private static void laser(MinecraftClient mc, long now) {
		UUID me = mc.player.getUuid();
		boolean want = mc.currentScreen == null && laserKey.isPressed();
		Vec3d hit = null;
		if (want) {
			float tp = mc.getRenderTickCounter().getTickProgress(true);
			HitResult r = mc.player.raycast(LASER_REACH, tp, false);
			if (r.getType() == HitResult.Type.BLOCK) {
				Vec3d eye = mc.player.getCameraPosVec(tp);
				Vec3d back = eye.subtract(r.getPos());
				// odrobinę przed ścianą, żeby kropka nie migała w bloku
				hit = back.lengthSquared() < 1e-6 ? r.getPos() : r.getPos().add(back.normalize().multiply(0.03));
			}
		}
		if (hit != null) {
			Dot d = DOTS.get(me);
			if (d == null) {
				d = new Dot();
				d.shown = hit;
				DOTS.put(me, d);
			}
			d.target = hit;
			d.shown = hit; // swoją kropkę widzę bez opóźnienia
			d.seenMs = now;
			laserOn = true;
			if (now - laserSentMs >= LASER_SEND_MS) {
				laserSentMs = now;
				NetClient.sendSignal(Signal.laser(hit.x, hit.y, hit.z, RANDOM.nextInt()));
			}
		} else if (laserOn) {
			laserOn = false;
			DOTS.remove(me);
			NetClient.sendSignal(Signal.laserOff(RANDOM.nextInt()));
		}
	}

	/** Kropka innego gracza (null = zgasła). */
	public static void remoteLaser(UUID who, double[] pos) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (pos == null || !FemboyMod.CONFIG.toys || mc.world == null) {
			DOTS.remove(who);
			return;
		}
		PlayerEntity owner = mc.world.getPlayerByUuid(who);
		Vec3d p = new Vec3d(pos[0], pos[1], pos[2]);
		// wskaźnik sięga 40 bloków – kropka dalej od właściciela to śmieci
		if (owner == null || owner.squaredDistanceTo(p) > (LASER_REACH + 6) * (LASER_REACH + 6)) return;
		Dot d = DOTS.get(who);
		if (d == null) {
			d = new Dot();
			d.shown = p;
			DOTS.put(who, d);
		}
		d.target = p;
		d.seenMs = Util.getMeasuringTimeMs();
	}

	/**
	 * Sam idę za kropką lasera albo gonię cudzy kłębek – tylko gdy włączyłem to u siebie (i, jeśli tak ustawiłem, tylko za
	 * zabawkami wybranych osób). To ma być zabawa, nie pułapka, więc sterowanie zawsze wygrywa: każdy mój klawisz ruchu
	 * albo ruch myszką przerywa gonienie na parę sekund, kucnięcie kończy gonienie tego kłębka na dobre, a jeden kłębek
	 * gonię tylko chwilę i mi się nudzi. Kropka ma pierwszeństwo przed kłębkiem.
	 */
	private static void follow(MinecraftClient mc) {
		FemboyConfig cfg = FemboyMod.CONFIG;
		float turned = followYaw;
		followYaw = Float.NaN;
		if (!cfg.laserFollow && !cfg.yarnFollow) return;
		if (mc.player.hasVehicle() || mc.player.isSpectator() || mc.currentScreen != null || Bonds.carried()) return;
		long now = Util.getMeasuringTimeMs();
		net.minecraft.client.option.GameOptions o = mc.options;
		if (mc.player.isSneaking() || o.sneakKey.isPressed()) {
			// kucnięcie: stoję, a kłębki w zasięgu przestają mnie obchodzić
			for (Ball b : BALLS) if (mc.player.squaredDistanceTo(b.x, b.y, b.z) < FOLLOW_RANGE * FOLLOW_RANGE) b.bored = true;
			manualUntilMs = Math.max(manualUntilMs, now + 600);
			return;
		}
		boolean moved = o.forwardKey.isPressed() || o.backKey.isPressed() || o.leftKey.isPressed() || o.rightKey.isPressed() || o.jumpKey.isPressed();
		// obróciło mnie w zeszłym ticku, a patrzę gdzie indziej – to gracz ruszył myszką
		boolean looked = !Float.isNaN(turned) && Math.abs(MathHelper.wrapDegrees(mc.player.getYaw() - turned)) > 1.5f;
		if (moved || looked) manualUntilMs = now + MANUAL_MS;
		if (now < manualUntilMs) {
			followSaid = false;
			return;
		}
		UUID me = mc.player.getUuid();
		Vec3d best = null;
		Ball ball = null;
		double bestD = FOLLOW_RANGE * FOLLOW_RANGE, stop = 1.1;
		if (cfg.laserFollow)
			for (Map.Entry<UUID, Dot> e : DOTS.entrySet()) {
				if (e.getKey().equals(me) || !Trust.allows(Trust.What.LASER, e.getKey())) continue;
				double d = mc.player.squaredDistanceTo(e.getValue().shown);
				if (d < bestD) {
					bestD = d;
					best = e.getValue().shown;
				}
			}
		if (best == null && cfg.yarnFollow) {
			stop = 0.3; // wbiegam w kłębek – wtedy go kopię i gonię dalej
			for (Ball b : BALLS) {
				if (b.bored || b.owner.equals(me) || !Trust.allows(Trust.What.LASER, b.owner)) continue;
				double d = mc.player.squaredDistanceTo(b.x, b.y, b.z);
				if (d < bestD) {
					bestD = d;
					best = new Vec3d(b.x, b.y, b.z);
					ball = b;
				}
			}
		}
		if (best == null) {
			followSaid = false;
			return;
		}
		if (ball != null && ++ball.chased > CHASE_TICKS) {
			ball.bored = true;
			return;
		}
		if (!followSaid) {
			followSaid = true;
			say(Text.literal(tr(ball != null ? "Gonisz kłębek (ruch albo kucnięcie = koniec)" : "Idziesz za kropką (ruch = przerwa, kucnięcie = stoisz)")));
		}
		double dx = best.x - mc.player.getX(), dz = best.z - mc.player.getZ(), dy = best.y - mc.player.getEyeY();
		double flat = Math.sqrt(dx * dx + dz * dz);
		float wantYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
		mc.player.setYaw(mc.player.getYaw() + MathHelper.wrapDegrees(wantYaw - mc.player.getYaw()) * (ball != null ? 0.2f : 0.3f));
		followYaw = mc.player.getYaw();
		if (ball == null) { // za kropką wodzę też wzrokiem w górę i w dół; przy kłębku kamera w pionie zostaje moja
			float wantPitch = (float) -(Math.atan2(dy, flat) * 180.0 / Math.PI);
			mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + (wantPitch - mc.player.getPitch()) * 0.3f, -90f, 90f));
		}
		if (flat < stop) return;
		// tempo marszu, nie szybciej – jak smycz
		double k = Math.min(0.1, flat * 0.05) / flat;
		mc.player.addVelocity(dx * k, 0, dz * k);
		if (mc.player.horizontalCollision && mc.player.isOnGround()) mc.player.jump();
	}

	// ======================== kłębek ========================

	/** Klawisz / przycisk / komenda: rzucam kłębek przed siebie. false = za szybko. */
	public static boolean throwYarn() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null) return false;
		long now = Util.getMeasuringTimeMs();
		if (now - yarnMs < 700) return false;
		yarnMs = now;
		Vec3d dir = mc.player.getRotationVec(1f);
		Vec3d from = mc.player.getEyePos().add(dir.multiply(0.45)).add(0, -0.15, 0);
		Vec3d v = dir.multiply(0.55).add(0, 0.14, 0);
		int nonce = RANDOM.nextInt();
		Voices.fresh(mc.player.getUuid(), nonce);
		Signal s = Signal.yarn(from.x, from.y, from.z, v.x, v.y, v.z, nonce);
		// u mnie leci dokładnie to samo, co u innych (liczby po zaokrągleniu do wysłania)
		spawn(mc.player.getUuid(), s.numbersArg());
		NetClient.sendSignal(s);
		return true;
	}

	public static void remoteYarn(UUID who, double[] n) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (n == null || !FemboyMod.CONFIG.toys || mc.world == null) return;
		PlayerEntity owner = mc.world.getPlayerByUuid(who);
		// kłębek wylatuje z ręki – nie może pojawić się daleko od rzucającego
		if (owner == null || owner.squaredDistanceTo(n[0], n[1], n[2]) > 6 * 6) return;
		spawn(who, n);
	}

	private static void spawn(UUID owner, double[] n) {
		int mine = 0;
		Ball oldest = null;
		for (Ball b : BALLS)
			if (b.owner.equals(owner)) {
				mine++;
				if (oldest == null || b.age > oldest.age) oldest = b;
			}
		if (mine >= MAX_PER_OWNER) BALLS.remove(oldest);
		if (BALLS.size() >= MAX_BALLS) BALLS.remove(0);
		Ball b = new Ball();
		b.x = b.px = n[0];
		b.y = b.py = n[1];
		b.z = b.pz = n[2];
		b.vx = n[3];
		b.vy = n[4];
		b.vz = n[5];
		b.owner = owner;
		MinecraftClient mc = MinecraftClient.getInstance();
		PlayerEntity p = mc.world == null ? null : mc.world.getPlayerByUuid(owner);
		Look look = p == null ? null : Accessories.current(owner, p.getGameProfile().name());
		b.color = look == null ? 0xF48FB1 : look.accent;
		BALLS.add(b);
	}

	/** Jeden tick fizyki kłębka. false = zniknął. */
	private static boolean step(ClientWorld world, Ball b) {
		if (++b.age > BALL_TICKS || b.y < world.getBottomY() - 8) return false;
		b.px = b.x;
		b.py = b.y;
		b.pz = b.z;
		b.lastSpin = b.spin;
		if (b.kickCd > 0) b.kickCd--;
		kick(world, b);
		if (b.rest) return true;
		b.vy -= 0.045;
		Vec3d from = new Vec3d(b.x, b.y, b.z), v = new Vec3d(b.vx, b.vy, b.vz);
		double speed = v.length();
		boolean grounded = false;
		if (speed > 1e-5) {
			Vec3d to = from.add(v);
			BlockHitResult hit = world.raycast(new RaycastContext(from, to.add(v.multiply(RADIUS / speed)), RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, ShapeContext.absent()));
			if (hit.getType() == HitResult.Type.BLOCK) {
				Vec3d n = hit.getSide().getDoubleVector();
				Vec3d at = hit.getPos().add(n.multiply(RADIUS));
				b.x = at.x;
				b.y = at.y;
				b.z = at.z;
				double vn = v.dotProduct(n);
				Vec3d out = v.subtract(n.multiply(1.5 * vn)).multiply(0.86); // odbicie z utratą energii
				b.vx = out.x;
				b.vy = out.y;
				b.vz = out.z;
				if (n.y > 0.5) {
					grounded = true;
					if (Math.abs(b.vy) < 0.1) b.vy = 0;
				}
			} else {
				b.x = to.x;
				b.y = to.y;
				b.z = to.z;
			}
		}
		// podłoga pod kłębkiem: leży na niej dokładnie (bez podskakiwania przy toczeniu)
		Vec3d here = new Vec3d(b.x, b.y, b.z);
		BlockHitResult floor = world.raycast(new RaycastContext(here, here.add(0, -(RADIUS + 0.03), 0), RaycastContext.ShapeType.COLLIDER,
			RaycastContext.FluidHandling.NONE, ShapeContext.absent()));
		if (floor.getType() == HitResult.Type.BLOCK && b.vy <= 0.0001) {
			b.y = floor.getPos().y + RADIUS;
			b.vy = b.vy < -0.14 ? -b.vy * 0.45 : 0;
			grounded = true;
		}
		double drag = grounded ? 0.93 : 0.985;
		b.vx *= drag;
		b.vz *= drag;
		double flat = Math.sqrt(b.vx * b.vx + b.vz * b.vz);
		if (flat > 0.002) {
			b.spin += (float) (flat / RADIUS);
			b.axisX = (float) (b.vz / flat);
			b.axisZ = (float) (-b.vx / flat);
		}
		if (grounded && flat < 0.006 && b.vy == 0) {
			b.vx = b.vz = 0;
			b.rest = true;
		}
		return true;
	}

	/** Kto wbiegnie w kłębek, ten go kopie – w stronę, w którą biegnie. */
	private static void kick(ClientWorld world, Ball b) {
		if (b.kickCd > 0 || b.age < 6) return;
		for (AbstractClientPlayerEntity p : world.getPlayers()) {
			if (p.isSpectator()) continue;
			double dx = b.x - p.getX(), dz = b.z - p.getZ(), dy = b.y - p.getY();
			if (dy < -0.3 || dy > 1.0 || dx * dx + dz * dz > 0.5 * 0.5) continue;
			double mx = p.getX() - p.lastX, mz = p.getZ() - p.lastZ;
			double m = Math.sqrt(mx * mx + mz * mz);
			if (m < 0.03) continue; // stoi nad kłębkiem – nic się nie dzieje
			double d = Math.max(0.05, Math.sqrt(dx * dx + dz * dz));
			b.vx = mx * 1.6 + dx / d * 0.1;
			b.vz = mz * 1.6 + dz / d * 0.1;
			b.vy = 0.16 + m * 0.5;
			b.rest = false;
			b.kickCd = 8;
			return;
		}
	}

	// ======================== koty patrzą ========================

	/** Co klatkę: kot wodzi głową za najbliższą kropką lasera albo toczącym się kłębkiem (tylko na ekranie). */
	public static void fill(AbstractClientPlayerEntity player, PlayerEntityRenderState state, Reactions.State s, Look look, float tp) {
		if (look == null || !look.isCat() || DOTS.isEmpty() && BALLS.isEmpty() || s.pair == PoseMath.PAIR_CARRIED) return;
		if (s.pose != null && (s.pose == Pose.NAP || s.pose == Pose.LOAF || s.pose == Pose.BELLY)) return;
		UUID id = player.getUuid();
		Vec3d eye = player.getCameraPosVec(tp);
		Vec3d best = null;
		double bestD = LOOK_RANGE * LOOK_RANGE;
		for (Map.Entry<UUID, Dot> e : DOTS.entrySet()) {
			if (e.getKey().equals(id)) continue; // na własną kropkę i tak patrzę
			double d = eye.squaredDistanceTo(e.getValue().shown);
			if (d < bestD) {
				bestD = d;
				best = e.getValue().shown;
			}
		}
		if (best == null)
			for (Ball b : BALLS) {
				if (b.rest) continue;
				double d = eye.squaredDistanceTo(b.x, b.y, b.z);
				if (d < bestD) {
					bestD = d;
					best = new Vec3d(MathHelper.lerp(tp, b.px, b.x), MathHelper.lerp(tp, b.py, b.y), MathHelper.lerp(tp, b.pz, b.z));
				}
			}
		if (best == null) return;
		double dx = best.x - eye.x, dy = best.y - eye.y, dz = best.z - eye.z;
		float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
		float rel = MathHelper.wrapDegrees(yaw - state.bodyYaw);
		if (Math.abs(rel) > 95f) return; // za plecami – kot nie wykręca szyi
		state.relativeHeadYaw = MathHelper.clamp(rel, -80f, 80f);
		state.pitch = MathHelper.clamp((float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180.0 / Math.PI), -60f, 75f);
		s.perk = Math.max(s.perk, 0.7f);
		s.hunt = Math.max(s.hunt, 0.6f);
	}

	public static boolean laserFollowing() {
		return FemboyMod.CONFIG.laserFollow;
	}

	public static void say(Text t) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null) mc.player.sendMessage(t.copy().formatted(Formatting.GRAY), true);
	}

	/** Po rozłączeniu. */
	public static void clear() {
		DOTS.clear();
		BALLS.clear();
		laserOn = false;
	}
}
