package pl.vsake.femboy.fx;

/**
 * Poza ludzika do przeróbki: kąty (radiany) i przesunięcia (piksele modelu) głowy, tułowia, rąk, nóg i całej postaci.
 * Układ jak w modelu gracza: Y w dół, przód to -Z, prawa ręka przy -X. Mixin wczytuje tu pozę z gry, PoseMath ją zmienia,
 * mixin zapisuje z powrotem. Czysta Java (testy bez gry).
 */
public final class Rig {
	public static final class Limb {
		public float pitch, yaw, roll, x, y, z;

		/** Przejście do podanych kątów: w = 0 zostaje poza z gry, 1 = cała nasza. */
		public Limb to(float p, float yw, float r, float w) {
			pitch += (p - pitch) * w;
			yaw += (yw - yaw) * w;
			roll += (r - roll) * w;
			return this;
		}

		/** Dodatek do tego, co jest (drgania, kołysanie). */
		public Limb add(float p, float yw, float r) {
			pitch += p;
			yaw += yw;
			roll += r;
			return this;
		}

		public Limb move(float dx, float dy, float dz) {
			x += dx;
			y += dy;
			z += dz;
			return this;
		}

		void set(float p, float yw, float r, float px, float py, float pz) {
			pitch = p;
			yaw = yw;
			roll = r;
			x = px;
			y = py;
			z = pz;
		}

		boolean finite() {
			return Float.isFinite(pitch) && Float.isFinite(yaw) && Float.isFinite(roll) && Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z);
		}
	}

	public final Limb head = new Limb(), body = new Limb(), rightArm = new Limb(), leftArm = new Limb(),
		rightLeg = new Limb(), leftLeg = new Limb();
	/** Cała postać: obrót i przesunięcie wokół szyi (0,0,0 modelu = 1,5 bloku nad stopami). */
	public final Limb root = new Limb();
	/** Głowa ściśnięta w pionie (boop): 0 = normalna. */
	public float headSquash;

	/** Poza stojącego gracza z vanilli – do testów i jako punkt wyjścia. */
	public Rig standing() {
		head.set(0, 0, 0, 0, 0, 0);
		body.set(0, 0, 0, 0, 0, 0);
		rightArm.set(0, 0, 0, -5, 2, 0);
		leftArm.set(0, 0, 0, 5, 2, 0);
		rightLeg.set(0, 0, 0, -1.9f, 12, 0);
		leftLeg.set(0, 0, 0, 1.9f, 12, 0);
		root.set(0, 0, 0, 0, 0, 0);
		headSquash = 0;
		return this;
	}

	public boolean finite() {
		return head.finite() && body.finite() && rightArm.finite() && leftArm.finite() && rightLeg.finite() && leftLeg.finite()
			&& root.finite() && Float.isFinite(headSquash);
	}

	public Limb arm(boolean right) {
		return right ? rightArm : leftArm;
	}
}
