package pl.vsake.femboy.fx;

import static pl.vsake.femboy.Lang.tr;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import pl.vsake.femboy.Keys;
import pl.vsake.femboy.net.NetClient;
import pl.vsake.femboy.net.Signal;
import pl.vsake.femboy.pat.PatPat;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Rzeczy we dwoje, wszystko tylko wizualne (nikogo nie przesuwa naprawdę):
 * przytulas (trzymam klawisz przy kimś – obejmujemy się, druga osoba też, jeśli ma moda), trzymanie się za ręce
 * (dłonie połączone, dopóki idziemy obok siebie) i noszenie na rękach / na barana (druga osoba „siedzi” na mnie).
 * Noszenie wymaga zgody obu stron: ja proszę klawiszem, noszony zgadza się swoim klawiszem (albo ma mnie zaznaczonego
 * jako zaufanego); kucnięcie = schodzę.
 */
public final class Bonds {
	private Bonds() {
	}

	public static final char HUG = 'h', HANDS = 'd', CARRY = 'c', PIGGY = 'p';

	private record Link(char kind, UUID target, long startMs, long seenMs) {
	}

	private record Consent(UUID holder, long seenMs) {
	}

	private static final long RESEND_MS = 2000, TIMEOUT_MS = 9000, ASK_MS = 15_000;
	private static final double HUG_RANGE = 3.2, HANDS_RANGE = 2.8, CARRY_RANGE = 10, LOST_RANGE = 14;

	private static final Map<UUID, Link> REMOTE = new HashMap<>();
	private static final Map<UUID, Consent> CONSENT = new HashMap<>();
	private static final Random NONCE = new Random();
	private static KeyBinding hugKey, handsKey, carryKey, piggyKey;
	private static Link mine;
	private static String mineName;
	private static long sentMs, okSentMs, lostSinceMs;
	/** Komu pozwalam się nosić (null = nikomu). */
	private static UUID myConsent;
	/** Kto ostatnio poprosił o noszenie mnie i do kiedy czekam na zgodę. */
	private static UUID askFrom;
	private static long askUntilMs;

	public static void init() {
		hugKey = Keys.register("We dwoje", "Przytulas (trzymaj przy kimś)", "hug", GLFW.GLFW_KEY_H);
		handsKey = Keys.register("We dwoje", "Za ręce (celuj w gracza)", "hands", GLFW.GLFW_KEY_J);
		carryKey = Keys.register("We dwoje", "Noszenie na rękach / zgoda", "carry", GLFW.GLFW_KEY_K);
		piggyKey = Keys.register("We dwoje", "Na barana / zgoda", "piggy", GLFW.GLFW_KEY_UNKNOWN);
		ClientTickEvents.END_CLIENT_TICK.register(Bonds::tick);
		ClientTickEvents.START_CLIENT_TICK.register(Bonds::carriedTick);
	}

	/** Niosący, który mnie teraz naprawdę niesie (prosi, zgodziłem się, jest blisko) albo null. */
	private static PlayerEntity carrier(MinecraftClient mc) {
		if (myConsent == null || mc.player == null || mc.world == null) return null;
		Link l = REMOTE.get(myConsent);
		if (l == null || l.kind != CARRY && l.kind != PIGGY || !l.target.equals(mc.player.getUuid())) return null;
		PlayerEntity h = mc.world.getPlayerByUuid(myConsent);
		return h == null || h.squaredDistanceTo(mc.player) > CARRY_RANGE * CARRY_RANGE ? null : h;
	}

	/**
	 * Czy tego gracza rysuję gdzie indziej, niż naprawdę stoi (na rękach / barkach niosącego). Gra chowa postacie według
	 * prawdziwej pozycji (poza kadrem, za ścianą), więc takiej osobie trzeba to chowanie wyłączyć, bo znika z rąk.
	 */
	public static boolean shownElsewhere(net.minecraft.entity.Entity e) {
		if (mine == null && REMOTE.isEmpty() || !(e instanceof PlayerEntity p)) return false;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) return false;
		UUID me = mc.player.getUuid(), id = p.getUuid();
		UUID h = holderOf(id, me);
		if (h == null || h.equals(id)) return false;
		Link in = linkOf(h, me);
		if (in == null || in.kind != CARRY && in.kind != PIGGY || !agreed(id, h, me)) return false;
		PlayerEntity holder = p.getEntityWorld().getPlayerByUuid(h);
		return holder != null && holder.squaredDistanceTo(p) <= CARRY_RANGE * CARRY_RANGE;
	}

	// mod EntityCulling (jest w wielu paczkach) chowa postacie zasłonięte ścianą – też według prawdziwej pozycji
	private static java.lang.reflect.Method cullOff, cameraOff;
	private static boolean cullLooked;

	private static void keepVisible(MinecraftClient mc) {
		if (mine == null && REMOTE.isEmpty()) return;
		if (!cullLooked) {
			cullLooked = true;
			try {
				Class<?> c = Class.forName("dev.tr7zw.entityculling.versionless.access.Cullable");
				cullOff = c.getMethod("setCulled", boolean.class);
				cameraOff = c.getMethod("setOutOfCamera", boolean.class);
			} catch (Throwable t) { // nie ma tego moda (albo ma inne API) – nic do zrobienia
				cullOff = cameraOff = null;
			}
		}
		if (cullOff == null) return;
		for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
			if (!shownElsewhere(p) || !cullOff.getDeclaringClass().isInstance(p)) continue;
			try {
				cullOff.invoke(p, false); // przy okazji wymusza widoczność na sekundę
				cameraOff.invoke(p, false);
			} catch (Throwable t) {
				cullOff = null;
				return;
			}
		}
	}

	/** Czy ktoś mnie teraz niesie (nie steruję wtedy postacią). */
	public static boolean carried() {
		return carrier(MinecraftClient.getInstance()) != null;
	}

	/**
	 * Niesiony nie steruje sobą: przed każdym tickiem puszczam klawisze ruchu, a postać sama trzyma się niosącego
	 * (naprawdę, nie tylko na ekranie – żeby serwer i inni widzieli mnie tam, gdzie niosący). Zejść można klawiszem noszenia.
	 */
	private static void carriedTick(MinecraftClient mc) {
		PlayerEntity h = carrier(mc);
		if (h == null || mc.player.isSpectator()) return;
		net.minecraft.client.option.GameOptions o = mc.options;
		for (net.minecraft.client.option.KeyBinding k : new net.minecraft.client.option.KeyBinding[]{o.forwardKey, o.backKey, o.leftKey, o.rightKey,
			o.jumpKey, o.sneakKey, o.sprintKey})
			k.setPressed(false);
		if (mc.player.hasVehicle()) return;
		double dx = h.getX() - mc.player.getX(), dz = h.getZ() - mc.player.getZ(), dy = h.getY() - mc.player.getY();
		double flat = Math.sqrt(dx * dx + dz * dz);
		mc.player.setYaw(mc.player.getYaw() + MathHelper.wrapDegrees(h.getYaw() - mc.player.getYaw()) * 0.35f);
		if (flat > 0.25) {
			// nadążam nawet za biegnącym; im dalej, tym mocniej
			double k = Math.min(0.2, 0.03 + flat * 0.09) / flat;
			mc.player.addVelocity(dx * k, 0, dz * k);
		}
		if (mc.player.isOnGround() && (mc.player.horizontalCollision && flat > 0.4 || dy > 0.6)) mc.player.jump();
		if (mc.player.isTouchingWater() && dy > -0.2) mc.player.addVelocity(0, 0.04, 0);
	}

	private static void tick(MinecraftClient mc) {
		boolean free = mc.player != null && mc.currentScreen == null;
		while (handsKey.wasPressed()) if (free) toggleAimed(HANDS);
		while (carryKey.wasPressed()) if (free) toggleAimed(CARRY);
		while (piggyKey.wasPressed()) if (free) toggleAimed(PIGGY);
		if (mc.player == null || mc.world == null) return;
		long now = Util.getMeasuringTimeMs();
		keepVisible(mc);
		// przytulas: dopóki trzymam klawisz i ktoś jest pod celownikiem
		if (free && hugKey.isPressed()) {
			if (mine == null && PatPat.aim(mc) instanceof PlayerEntity p && p.squaredDistanceTo(mc.player) <= HUG_RANGE * HUG_RANGE)
				start(HUG, p.getUuid(), p.getGameProfile().name());
		} else if (mine != null && mine.kind == HUG) stop(null);
		if (!REMOTE.isEmpty()) REMOTE.values().removeIf(l -> now - l.seenMs > TIMEOUT_MS);
		if (!CONSENT.isEmpty()) CONSENT.values().removeIf(c -> now - c.seenMs > TIMEOUT_MS);
		if (mine != null) {
			PlayerEntity t = mc.world.getPlayerByUuid(mine.target);
			double max = mine.kind == HUG ? HUG_RANGE + 1 : LOST_RANGE;
			if (t != null && t.squaredDistanceTo(mc.player) <= max * max) lostSinceMs = now;
			if (now - lostSinceMs > 4000) stop("Za daleko – puszczasz");
			else if (now - sentMs >= RESEND_MS) {
				sentMs = now;
				NetClient.sendSignal(Signal.bond(mine.kind, mine.target, NONCE.nextInt()));
			}
		}
		if (myConsent != null) {
			Link l = REMOTE.get(myConsent);
			boolean wants = l != null && (l.kind == CARRY || l.kind == PIGGY) && l.target.equals(mc.player.getUuid());
			if (!wants) withdraw(null);
			else if (now - okSentMs >= RESEND_MS) {
				okSentMs = now;
				NetClient.sendSignal(Signal.ok(myConsent, NONCE.nextInt()));
			}
		}
		if (askFrom != null && now > askUntilMs) askFrom = null;
	}

	private static void toggleAimed(char kind) {
		MinecraftClient mc = MinecraftClient.getInstance();
		boolean carry = kind == CARRY || kind == PIGGY;
		// ktoś prosi o noszenie mnie → klawisz noszenia to zgoda
		if (carry && askFrom != null && myConsent == null) {
			consent(askFrom);
			return;
		}
		if (carry && myConsent != null) {
			withdraw("Schodzisz");
			return;
		}
		LivingEntity aimed = PatPat.aim(mc);
		if (mine != null && (mine.kind == kind || !(aimed instanceof PlayerEntity))) {
			stop(kind == HANDS ? "Puszczasz rękę" : "Stawiasz na ziemi");
			return;
		}
		if (aimed instanceof PlayerEntity p) start(kind, p.getUuid(), p.getGameProfile().name());
		else say(Text.literal(tr("Wyceluj w gracza")).formatted(Formatting.GRAY));
	}

	/** Zaczynam (klawisz, okno moda, komenda). Poprzednia więź się kończy. */
	public static void start(char kind, UUID target, String name) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || target.equals(mc.player.getUuid()) || Signal.BOND_KINDS.indexOf(kind) < 0) return;
		long now = Util.getMeasuringTimeMs();
		mine = new Link(kind, target, now, now);
		mineName = name;
		sentMs = lostSinceMs = now;
		NetClient.sendSignal(Signal.bond(kind, target, NONCE.nextInt()));
		Text who = Text.literal(name).formatted(Formatting.LIGHT_PURPLE);
		say(switch (kind) {
			case HUG -> Text.literal(tr("Przytulasz: ")).append(who);
			case HANDS -> Text.literal(tr("Trzymasz za rękę: ")).append(who);
			case CARRY -> Text.literal(tr("Prosisz o zgodę na noszenie na rękach: ")).append(who);
			default -> Text.literal(tr("Prosisz o zgodę na noszenie na barana: ")).append(who);
		});
	}

	public static void stop() {
		if (mine != null) stop("Puszczasz");
	}

	private static void stop(String why) {
		if (mine == null) return;
		mine = null;
		mineName = null;
		NetClient.sendSignal(Signal.bond(HUG, null, NONCE.nextInt()));
		if (why != null) say(Text.literal(tr(why)).formatted(Formatting.GRAY));
	}

	/** Zgadzam się, żeby ta osoba mnie nosiła (klawisz albo zaufanie). */
	public static void consent(UUID holder) {
		long now = Util.getMeasuringTimeMs();
		myConsent = holder;
		askFrom = null;
		okSentMs = now;
		NetClient.sendSignal(Signal.ok(holder, NONCE.nextInt()));
		say(Text.literal(tr("Dajesz się nieść – nie sterujesz, dopóki cię nie puści (") + Keys.name(carryKey) + tr(" = zejdź)")).formatted(Formatting.WHITE));
	}

	private static void withdraw(String why) {
		if (myConsent == null) return;
		myConsent = null;
		NetClient.sendSignal(Signal.ok(null, NONCE.nextInt()));
		if (why != null) say(Text.literal(tr(why)).formatted(Formatting.GRAY));
	}

	private static void say(Text t) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null) mc.player.sendMessage(t, true);
	}

	/** Moja więź: litera rodzaju albo 0. */
	public static char mineKind() {
		return mine == null ? 0 : mine.kind;
	}

	public static String mineName() {
		return mineName;
	}

	/** Czy ktoś czeka na moją zgodę na noszenie (do podpowiedzi w oknie moda). */
	public static UUID asking() {
		return askFrom;
	}

	public static UUID consenting() {
		return myConsent;
	}

	/** Sygnał od innego gracza: holder zaczyna / kończy (kind 0). */
	public static void remote(UUID holder, char kind, UUID target) {
		MinecraftClient mc = MinecraftClient.getInstance();
		long now = Util.getMeasuringTimeMs();
		if (kind == 0 || target == null) {
			REMOTE.remove(holder);
			return;
		}
		Link old = REMOTE.get(holder);
		boolean same = old != null && old.kind == kind && old.target.equals(target);
		REMOTE.put(holder, new Link(kind, target, same ? old.startMs : now, now));
		if (same || mc.player == null || mc.world == null || !target.equals(mc.player.getUuid())) return;
		PlayerEntity h = mc.world.getPlayerByUuid(holder);
		if (h == null) return;
		Text who = Text.literal(h.getGameProfile().name()).formatted(Formatting.LIGHT_PURPLE);
		switch (kind) {
			case HUG -> say(who.copy().append(Text.literal(tr(" cię przytula")).formatted(Formatting.WHITE)));
			case HANDS -> say(who.copy().append(Text.literal(tr(" bierze cię za rękę")).formatted(Formatting.WHITE)));
			default -> {
				if (myConsent != null) return;
				// zaufana osoba: zgoda od razu; inaczej czekam na klawisz
				if (Trust.picked(Trust.What.CARRY, holder)) consent(holder);
				else {
					askFrom = holder;
					askUntilMs = now + ASK_MS;
					say(who.copy().append(Text.literal(tr(" chce cię ponieść ") + (kind == CARRY ? tr("na rękach") : tr("na barana")) + tr(" – naciśnij ")).formatted(Formatting.WHITE))
						.append(Text.literal(Keys.name(carryKey)).formatted(Formatting.YELLOW)).append(Text.literal(tr(", żeby się zgodzić")).formatted(Formatting.WHITE)));
				}
			}
		}
	}

	/** Zgoda innego gracza na noszenie przez holdera (null = wycofana). */
	public static void remoteOk(UUID carried, UUID holder) {
		if (holder == null) CONSENT.remove(carried);
		else CONSENT.put(carried, new Consent(holder, Util.getMeasuringTimeMs()));
	}

	private static Link linkOf(UUID holder, UUID me) {
		return holder.equals(me) ? mine : REMOTE.get(holder);
	}

	private static boolean agreed(UUID carried, UUID holder, UUID me) {
		if (carried.equals(me)) return holder.equals(myConsent);
		Consent c = CONSENT.get(carried);
		return c != null && c.holder.equals(holder);
	}

	/** Kto mnie trzyma (pierwsza pasująca więź) albo null. */
	private static UUID holderOf(UUID target, UUID me) {
		if (mine != null && mine.target.equals(target)) return me;
		for (Map.Entry<UUID, Link> e : REMOTE.entrySet()) if (e.getValue().target.equals(target)) return e.getKey();
		return null;
	}

	/** Co klatkę: ustawia pozę we dwoje, a noszonego przestawia (tylko na ekranie) na ręce / barki niosącego. */
	public static void fill(AbstractClientPlayerEntity player, PlayerEntityRenderState state, Reactions.State s, float tp) {
		if (mine == null && REMOTE.isEmpty()) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) return;
		UUID me = mc.player.getUuid(), id = player.getUuid();
		long now = Util.getMeasuringTimeMs();
		Link out = linkOf(id, me);
		if (out != null) {
			PlayerEntity t = player.getEntityWorld().getPlayerByUuid(out.target);
			if (t != null && t != player) {
				float w = PoseMath.ease((now - out.startMs) / 250f);
				double d2 = t.squaredDistanceTo(player);
				switch (out.kind) {
					case HUG -> {
						if (d2 <= HUG_RANGE * HUG_RANGE) {
							set(s, PoseMath.PAIR_HUG, w);
							face(player, t, state, tp, w);
						}
					}
					case HANDS -> {
						if (d2 <= HANDS_RANGE * HANDS_RANGE) hands(player, t, state, s, tp, w);
					}
					case CARRY, PIGGY -> {
						if (d2 <= CARRY_RANGE * CARRY_RANGE && agreed(out.target, id, me))
							set(s, out.kind == CARRY ? PoseMath.PAIR_CARRY_HOLD : PoseMath.PAIR_PIGGY_HOLD, w);
					}
					default -> {
					}
				}
			}
		}
		UUID h = holderOf(id, me);
		if (h == null || s.pair != 0) return;
		Link in = linkOf(h, me);
		PlayerEntity holder = player.getEntityWorld().getPlayerByUuid(h);
		if (in == null || holder == null || holder == player) return;
		float w = PoseMath.ease((now - in.startMs) / 250f);
		double d2 = holder.squaredDistanceTo(player);
		switch (in.kind) {
			case HUG -> {
				// druga osoba odwzajemnia, jeśli ma moda (albo to ja)
				if (d2 <= HUG_RANGE * HUG_RANGE && (id.equals(me) || NetClient.shared(id) != null)) {
					set(s, PoseMath.PAIR_HUG, w);
					face(player, holder, state, tp, w);
				}
			}
			case HANDS -> {
				if (d2 <= HANDS_RANGE * HANDS_RANGE) hands(player, holder, state, s, tp, w);
			}
			case CARRY, PIGGY -> {
				if (d2 > CARRY_RANGE * CARRY_RANGE || !agreed(id, h, me)) return;
				float yaw = MathHelper.lerpAngleDegrees(tp, holder.lastBodyYaw, holder.bodyYaw);
				Vec3d base = holder.getLerpedPos(tp);
				Vec3d fwd = Vec3d.fromPolar(0f, yaw);
				boolean carry = in.kind == CARRY;
				Vec3d at = carry ? base.add(fwd.multiply(0.55)).add(0, 0.82, 0) : base.add(fwd.multiply(-0.34)).add(0, 0.5, 0);
				state.x = at.x;
				state.y = at.y;
				state.z = at.z;
				state.bodyYaw = carry ? yaw - 90f : yaw;
				state.relativeHeadYaw = carry ? 0f : MathHelper.clamp(state.relativeHeadYaw, -40f, 40f);
				state.pitch = carry ? 0f : state.pitch;
				state.limbSwingAmplitude = 0f;
				state.isInSneakingPose = false;
				state.hasVehicle = !carry; // na barana: siad jak na koniu
				state.sneaking = false;
				s.pose = null;
				set(s, carry ? PoseMath.PAIR_CARRIED : PoseMath.PAIR_PIGGY_RIDER, 1f);
			}
			default -> {
			}
		}
	}

	private static void set(Reactions.State s, int pair, float w) {
		s.pair = pair;
		s.pairW = w;
	}

	/** Tułów zwraca się do partnera (tylko na ekranie). */
	private static void face(AbstractClientPlayerEntity player, PlayerEntity other, PlayerEntityRenderState state, float tp, float w) {
		Vec3d a = player.getLerpedPos(tp), b = other.getLerpedPos(tp);
		float want = (float) (Math.atan2(b.z - a.z, b.x - a.x) * 180.0 / Math.PI) - 90f;
		float d = MathHelper.wrapDegrees(want - state.bodyYaw) * w;
		state.bodyYaw += d;
		state.relativeHeadYaw = MathHelper.clamp(state.relativeHeadYaw - d, -35f, 35f);
	}

	/** Ręka od strony partnera wyciągnięta do połowy drogi między nami – dłonie się spotykają. */
	private static void hands(AbstractClientPlayerEntity player, PlayerEntity other, PlayerEntityRenderState state, Reactions.State s, float tp, float w) {
		Vec3d a = player.getLerpedPos(tp), b = other.getLerpedPos(tp);
		float yaw = state.bodyYaw * 0.017453292f;
		// układ gracza: przód i lewa strona w świecie
		double fx = -Math.sin(yaw), fz = Math.cos(yaw), lx = Math.cos(yaw), lz = Math.sin(yaw);
		double side = (b.x - a.x) * lx + (b.z - a.z) * lz;
		boolean right = side < 0;
		double sx = a.x + lx * (right ? -0.3125 : 0.3125), sy = a.y + 1.375, sz = a.z + lz * (right ? -0.3125 : 0.3125);
		double mx = (a.x + b.x) / 2 - sx, my = (a.y + b.y) / 2 + 0.8 - sy, mz = (a.z + b.z) / 2 - sz;
		s.pair = PoseMath.PAIR_HANDS;
		s.pairW = w;
		s.aimRight = right;
		s.aimX = (float) (mx * lx + mz * lz);
		s.aimY = (float) -my;
		s.aimZ = (float) -(mx * fx + mz * fz);
	}

	/** Po rozłączeniu. */
	public static void clear() {
		REMOTE.clear();
		CONSENT.clear();
		mine = null;
		mineName = null;
		myConsent = null;
		askFrom = null;
	}
}
