package pl.vsake.femboy.acc;

/** Mały deterministyczny generator (SplitMix64) – ten sam gracz zawsze dostaje to samo. */
public final class Rng {
	private long state;

	public Rng(long seed) {
		state = seed;
	}

	public static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	public long next() {
		state += 0x9E3779B97F4A7C15L;
		return mix(state);
	}

	public float nextFloat() {
		return (next() >>> 40) / (float) (1 << 24);
	}

	public boolean chance(float p) {
		return nextFloat() < p;
	}

	public int nextInt(int n) {
		return (int) Math.floorMod(next(), (long) n);
	}

	public <T> T pick(T[] arr) {
		return arr[nextInt(arr.length)];
	}

	public int pick(int[] arr) {
		return arr[nextInt(arr.length)];
	}
}
