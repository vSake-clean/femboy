package pl.vsake.femboy.fx;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import pl.vsake.femboy.FemboyMod;
import pl.vsake.femboy.acc.Accessories;
import pl.vsake.femboy.acc.Item;
import pl.vsake.femboy.acc.Look;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ślady łapek: kto ma zwierzęce dodatki (uszy, ogon albo łapki) i idzie po śniegu albo piasku, zostawia za sobą odciski,
 * które po chwili bledną. Każdy liczy je u siebie z tego, co widzi – bez sieci.
 */
public final class Prints {
	private Prints() {
	}

	public record Print(double x, double y, double z, float yaw, long bornMs, boolean snow, boolean left) {
	}

	public static final long LIFE_MS = 22_000, FADE_MS = 6_000;
	private static final double STEP = 0.55;
	private static final int MAX = 500;

	private static final class Walk {
		double x, z, dist;
		boolean left;
	}

	private static final Deque<Print> PRINTS = new ArrayDeque<>();
	private static final Map<UUID, Walk> WALK = new HashMap<>();
	private static int ticks;

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(Prints::tick);
	}

	public static Deque<Print> all() {
		return PRINTS;
	}

	private static void tick(MinecraftClient mc) {
		if (mc.world == null || mc.player == null) {
			if (!PRINTS.isEmpty() || !WALK.isEmpty()) clear();
			return;
		}
		if (mc.isPaused()) return;
		long now = Util.getMeasuringTimeMs();
		while (!PRINTS.isEmpty() && now - PRINTS.peekFirst().bornMs > LIFE_MS) PRINTS.pollFirst();
		if (!FemboyMod.CONFIG.pawPrints) return;
		for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
			Walk w = WALK.get(p.getUuid());
			if (w == null) {
				w = new Walk();
				w.x = p.getX();
				w.z = p.getZ();
				WALK.put(p.getUuid(), w);
			}
			double dx = p.getX() - w.x, dz = p.getZ() - w.z;
			w.x = p.getX();
			w.z = p.getZ();
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d > 4 || !p.isOnGround() || p.isSpectator() || p.isInvisible() || p.hasVehicle()) { // teleport, skok, lot
				w.dist = 0;
				continue;
			}
			w.dist += d;
			if (w.dist < STEP || d < 1e-4) continue;
			w.dist = 0;
			Look look = Accessories.current(p.getUuid(), p.getGameProfile().name());
			if (look == null || look.get(Item.Slot.EARS) == null && look.get(Item.Slot.TAIL) == null && !look.has(Item.PAWS)) continue;
			// śnieg leżący warstwą to blok, w którym stoję; pełny śnieg i piasek są pod stopami
			BlockPos feet = BlockPos.ofFloored(p.getX(), p.getY() + 0.01, p.getZ());
			BlockState in = mc.world.getBlockState(feet), under = mc.world.getBlockState(feet.down());
			boolean snow = in.isIn(BlockTags.SNOW) || in.isAir() && under.isIn(BlockTags.SNOW);
			boolean sand = !snow && in.isAir() && under.isIn(BlockTags.SAND);
			if (!snow && !sand) continue;
			w.left = !w.left;
			float yaw = (float) Math.atan2(-dx, dz);
			// łapki na zmianę po lewej i prawej stronie toru
			double side = w.left ? 0.14 : -0.14, sx = Math.cos(yaw) * side, sz = Math.sin(yaw) * side;
			double y = p.getY();
			if (in.isIn(BlockTags.SNOW)) { // cienka warstwa śniegu nie ma kolizji – odcisk na jej wierzchu, nie pod nią
				net.minecraft.util.shape.VoxelShape shape = in.getOutlineShape(mc.world, feet);
				if (!shape.isEmpty()) y = Math.max(y, feet.getY() + shape.getMax(net.minecraft.util.math.Direction.Axis.Y));
			}
			// każda łapka odrobinę inaczej skręcona (stale dla danego miejsca), palcami lekko na zewnątrz
			float turn = (w.left ? 0.16f : -0.16f) + (float) (Math.sin(p.getX() * 12.9898 + p.getZ() * 78.233) * 0.12);
			PRINTS.addLast(new Print(p.getX() + sx, y + 0.012, p.getZ() + sz, yaw + turn, now, snow, w.left));
			if (PRINTS.size() > MAX) PRINTS.pollFirst();
		}
		if (++ticks % 200 == 0) {
			Set<UUID> here = new HashSet<>();
			for (AbstractClientPlayerEntity p : mc.world.getPlayers()) here.add(p.getUuid());
			WALK.keySet().retainAll(here);
		}
	}

	/** Po rozłączeniu. */
	public static void clear() {
		PRINTS.clear();
		WALK.clear();
	}
}
