package pl.vsake.femboy.fx;

import static pl.vsake.femboy.Lang.tr;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import org.lwjgl.glfw.GLFW;
import pl.vsake.femboy.FemboyMod;
import pl.vsake.femboy.Keys;
import pl.vsake.femboy.net.NetClient;
import pl.vsake.femboy.net.Signal;
import pl.vsake.femboy.pat.PatPat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Smycz – dodatek dla dwóch osób: celuję w gracza i naciskam klawisz → linka od mojej ręki do jego szyi (rysuje ją
 * gra, tak jak smycz moba). Widzą ją wszyscy z modem. Prowadzony jest naprawdę ciągnięty TYLKO, gdy sam włączył
 * u siebie „daję się ciągnąć” (domyślnie wyłączone) – wtedy jego własny klient lekko popycha go w stronę prowadzącego,
 * kiedy linka się napnie. Skradanie = stawiam opór.
 */
public final class Leash {
	private Leash() {
	}

	/** Długość luźnej linki; dalej zaczyna ciągnąć. */
	private static final double SLACK = 3.5;
	/** Dalej smycz się zrywa. */
	private static final double BREAK = 24;
	private static final long RESEND_MS = 2000, TIMEOUT_MS = 6500, LOST_MS = 8000;

	private record Link(UUID target, long seenMs) {
	}

	private static final Map<UUID, Link> REMOTE = new HashMap<>();
	private static final Random NONCE = new Random();
	private static KeyBinding key;
	/** Kogo prowadzę (null = nikogo). */
	private static UUID mine;
	private static String mineName;
	private static long sentMs, lastSeenMs;

	public static void init() {
		key = Keys.register("Zabawa", "Smycz (celuj w gracza)", "leash", GLFW.GLFW_KEY_B);
		ClientTickEvents.END_CLIENT_TICK.register(Leash::tick);
	}

	public static KeyBinding key() {
		return key;
	}

	private static void tick(MinecraftClient mc) {
		while (key.wasPressed()) if (mc.player != null && mc.currentScreen == null) toggleAimed();
		if (mc.player == null || mc.world == null) return;
		long now = Util.getMeasuringTimeMs();
		if (!REMOTE.isEmpty()) REMOTE.values().removeIf(l -> now - l.seenMs > TIMEOUT_MS);
		if (mine != null) {
			PlayerEntity t = mc.world.getPlayerByUuid(mine);
			if (t != null && t.squaredDistanceTo(mc.player) <= BREAK * BREAK) lastSeenMs = now;
			if (now - lastSeenMs > LOST_MS || t != null && t.squaredDistanceTo(mc.player) > BREAK * BREAK) {
				release("Smycz się zerwała");
			} else if (now - sentMs >= RESEND_MS) {
				sentMs = now;
				NetClient.sendSignal(Signal.leash(mine, NONCE.nextInt()));
			}
		}
		pull(mc);
	}

	/** Jestem na smyczy i pozwalam się ciągnąć → gdy linka napięta, mój klient popycha mnie do prowadzącego. */
	private static void pull(MinecraftClient mc) {
		if (!FemboyMod.CONFIG.leashPull || mc.player.isSneaking() || mc.player.hasVehicle() || mc.player.isSpectator()) return;
		UUID h = holderOf(mc.player.getUuid());
		if (h == null || !Trust.allows(Trust.What.LEASH, h)) return;
		PlayerEntity holder = mc.world.getPlayerByUuid(h);
		if (holder == null || holder == mc.player) return;
		double dx = holder.getX() - mc.player.getX(), dy = holder.getY() - mc.player.getY(), dz = holder.getZ() - mc.player.getZ();
		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (dist <= SLACK || dist > BREAK) return;
		// łagodnie: najwyżej trochę ponad tempo marszu, żeby serwer nie uznał tego za oszustwo
		double k = Math.min(0.11, (dist - SLACK) * 0.04) / dist;
		boolean floating = !mc.player.isOnGround();
		mc.player.addVelocity(dx * k, floating && dy > 0 ? Math.min(0.05, dy * k) : 0, dz * k);
	}

	private static void toggleAimed() {
		MinecraftClient mc = MinecraftClient.getInstance();
		LivingEntity aimed = PatPat.aim(mc);
		if (aimed instanceof PlayerEntity p) {
			if (p.getUuid().equals(mine)) release("Puszczasz smycz");
			else take(p.getUuid(), p.getGameProfile().name());
		} else if (mine != null) release("Puszczasz smycz");
		else say(Text.literal(tr("Wyceluj w gracza, żeby wziąć go na smycz")).formatted(Formatting.GRAY));
	}

	/** Biorę gracza na smycz (z klawisza, okna moda albo komendy). */
	public static void take(UUID target, String name) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || target.equals(mc.player.getUuid())) return;
		mine = target;
		mineName = name;
		sentMs = lastSeenMs = Util.getMeasuringTimeMs();
		NetClient.sendSignal(Signal.leash(target, NONCE.nextInt()));
		say(Text.literal(tr("Prowadzisz na smyczy: ")).formatted(Formatting.WHITE).append(Text.literal(name).formatted(Formatting.LIGHT_PURPLE)));
	}

	public static void release() {
		if (mine != null) release("Puszczasz smycz");
	}

	private static void release(String why) {
		mine = null;
		mineName = null;
		NetClient.sendSignal(Signal.leash(null, NONCE.nextInt()));
		say(Text.literal(tr(why)).formatted(Formatting.GRAY));
	}

	private static void say(Text t) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null) mc.player.sendMessage(t, true);
	}

	/** Kogo prowadzę albo null. */
	public static UUID leading() {
		return mine;
	}

	public static String leadingName() {
		return mineName;
	}

	/** Sygnał od innego gracza: holder prowadzi target (null = puścił). */
	public static void remote(UUID holder, UUID target) {
		MinecraftClient mc = MinecraftClient.getInstance();
		boolean me = mc.player != null && mc.player.getUuid().equals(target);
		Link old = target == null ? REMOTE.remove(holder) : REMOTE.put(holder, new Link(target, Util.getMeasuringTimeMs()));
		if (me && (old == null || !target.equals(old.target))) {
			PlayerEntity h = mc.world == null ? null : mc.world.getPlayerByUuid(holder);
			if (h != null) say(Text.literal(h.getGameProfile().name()).formatted(Formatting.LIGHT_PURPLE)
				.append(Text.literal(tr(" bierze cię na smycz") + (FemboyMod.CONFIG.leashPull && Trust.allows(Trust.What.LEASH, holder) ? "" : tr(" (nie ciągnie cię – tak masz ustawione)"))).formatted(Formatting.WHITE)));
		}
	}

	/** Kto prowadzi tego gracza (UUID prowadzącego) albo null. */
	public static UUID holderOf(UUID target) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (target.equals(mine) && mc.player != null) return mc.player.getUuid();
		for (Map.Entry<UUID, Link> e : REMOTE.entrySet()) if (e.getValue().target.equals(target)) return e.getKey();
		return null;
	}

	/** Co klatkę dla rysowanego gracza: jeśli jest na smyczy, dokładamy linkę do jego stanu (gra rysuje ją sama). */
	public static void fill(AbstractClientPlayerEntity player, EntityRenderState state, float tickProgress) {
		if (mine == null && REMOTE.isEmpty()) return;
		// kto jest na smyczy, sam jej przy sobie nie widzi (chyba że włączy to w oknie moda)
		if (!FemboyMod.CONFIG.leashSeeOwn && player == MinecraftClient.getInstance().player) return;
		UUID h = holderOf(player.getUuid());
		if (h == null) return;
		PlayerEntity holder = player.getEntityWorld().getPlayerByUuid(h);
		if (holder == null || holder == player || holder.squaredDistanceTo(player) > BREAK * BREAK) return;
		EntityRenderState.LeashData d = new EntityRenderState.LeashData();
		float yaw = player.lerpYaw(tickProgress) * 0.017453292f;
		// szyja: tuż pod głową, odrobinę przed tułowiem
		d.offset = new Vec3d(0, player.getHeight() * 0.77, player.getWidth() * 0.22).rotateY(-yaw);
		d.startPos = player.getLerpedPos(tickProgress).add(d.offset);
		d.endPos = holder.getLeashPos(tickProgress);
		BlockPos a = BlockPos.ofFloored(player.getCameraPosVec(tickProgress)), b = BlockPos.ofFloored(holder.getCameraPosVec(tickProgress));
		d.leashedEntityBlockLight = player.getEntityWorld().getLightLevel(LightType.BLOCK, a);
		d.leashHolderBlockLight = player.getEntityWorld().getLightLevel(LightType.BLOCK, b);
		d.leashedEntitySkyLight = player.getEntityWorld().getLightLevel(LightType.SKY, a);
		d.leashHolderSkyLight = player.getEntityWorld().getLightLevel(LightType.SKY, b);
		List<EntityRenderState.LeashData> list = new ArrayList<>(1);
		list.add(d);
		state.leashDatas = list;
	}

	/** Po rozłączeniu. */
	public static void clear() {
		REMOTE.clear();
		mine = null;
		mineName = null;
	}
}
