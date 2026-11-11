package pl.vsake.femboy.fx;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;

/**
 * Przekłada pozę z PoseMath na model gracza. Wołane po vanillowym układaniu modelu (BipedEntityModelMixin), więc działa
 * tak samo dla skina, zbroi i modelu dodatków. Jeden wspólny Rig – wszystko dzieje się na wątku rysowania.
 */
public final class PoseRig {
	private PoseRig() {
	}

	private static final Rig RIG = new Rig();

	public static void apply(BipedEntityModel<?> m, Reactions.State s) {
		if (s.pose == null && s.pair == 0 && s.boop <= 0f) return;
		Rig r = RIG;
		read(m.head, r.head);
		read(m.body, r.body);
		read(m.rightArm, r.rightArm);
		read(m.leftArm, r.leftArm);
		read(m.rightLeg, r.rightLeg);
		read(m.leftLeg, r.leftLeg);
		ModelPart root = m.getRootPart();
		read(root, r.root);
		r.headSquash = 0f;
		PoseMath.apply(s.pose, s.poseW, s.poseT, r);
		PoseMath.pair(s.pair, s.pairW, s.age / 20f, s.aimX, s.aimY, s.aimZ, s.aimRight, r);
		PoseMath.boop(r, s.boop);
		if (!r.finite()) return; // zła liczba nie może rozwalić modelu
		write(r.head, m.head);
		write(r.body, m.body);
		write(r.rightArm, m.rightArm);
		write(r.leftArm, m.leftArm);
		write(r.rightLeg, m.rightLeg);
		write(r.leftLeg, m.leftLeg);
		write(r.root, root);
		if (r.headSquash > 0f) {
			m.head.yScale *= 1f - r.headSquash;
			m.head.xScale *= 1f + r.headSquash * 0.5f;
			m.head.zScale *= 1f + r.headSquash * 0.5f;
		}
	}

	private static void read(ModelPart p, Rig.Limb l) {
		l.pitch = p.pitch;
		l.yaw = p.yaw;
		l.roll = p.roll;
		l.x = p.originX;
		l.y = p.originY;
		l.z = p.originZ;
	}

	private static void write(Rig.Limb l, ModelPart p) {
		p.pitch = l.pitch;
		p.yaw = l.yaw;
		p.roll = l.roll;
		p.originX = l.x;
		p.originY = l.y;
		p.originZ = l.z;
	}
}
