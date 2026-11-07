package pl.vsake.femboy.acc;

/**
 * Kolor włosów ze skina 64x64 (już po obróbce jak w vanilli): najczęstszy kolor na czubku i z tyłu głowy,
 * biorąc to, co faktycznie widać (warstwa włosów przykrywa głowę). Dzięki temu uszy i ogon "auto" pasują do fryzury.
 */
public final class Hair {
	private Hair() {
	}

	/** @return 0xRRGGBB albo -1, gdy nie da się ustalić */
	public static int detect(int[] argb, int w, int h) {
		if (argb == null || w != 64 || h != 64) return -1;
		int[] weight = new int[4096];
		long[] sumR = new long[4096], sumG = new long[4096], sumB = new long[4096];
		// {x głowy, y głowy, x warstwy, y warstwy, szer., wys., waga}
		int[][] regions = {
			{8, 0, 40, 0, 8, 8, 3},    // czubek
			{24, 8, 56, 8, 8, 4, 2},   // tył głowy, górna połowa
			{0, 8, 32, 8, 8, 3, 1},    // prawy bok, góra
			{16, 8, 48, 8, 8, 3, 1},   // lewy bok, góra
			{8, 8, 40, 8, 8, 1, 1},    // grzywka (górny rząd twarzy)
		};
		int total = 0;
		for (int[] r : regions) {
			for (int dy = 0; dy < r[5]; dy++)
				for (int dx = 0; dx < r[4]; dx++) {
					int over = argb[(r[3] + dy) * w + r[2] + dx];
					int c = (over >>> 24) >= 128 ? over : argb[(r[1] + dy) * w + r[0] + dx];
					if ((c >>> 24) < 128) continue;
					int rr = c >> 16 & 255, gg = c >> 8 & 255, bb = c & 255;
					int k = (rr >> 4) << 8 | (gg >> 4) << 4 | (bb >> 4);
					weight[k] += r[6];
					sumR[k] += (long) rr * r[6];
					sumG[k] += (long) gg * r[6];
					sumB[k] += (long) bb * r[6];
					total += r[6];
				}
		}
		if (total == 0) return -1;
		// kubełek razem z sąsiadami (odcienie tego samego koloru) – wygrywa najliczniejsza "rodzina"
		int best = -1, bestW = 0;
		for (int k = 0; k < 4096; k++) {
			if (weight[k] == 0) continue;
			int fam = family(weight, k);
			if (fam > bestW) {
				bestW = fam;
				best = k;
			}
		}
		long r = 0, g = 0, b = 0, n = 0;
		int br = best >> 8, bg = best >> 4 & 15, bb = best & 15;
		for (int dr = -1; dr <= 1; dr++)
			for (int dg = -1; dg <= 1; dg++)
				for (int db = -1; db <= 1; db++) {
					int qr = br + dr, qg = bg + dg, qb = bb + db;
					if (qr < 0 || qg < 0 || qb < 0 || qr > 15 || qg > 15 || qb > 15) continue;
					int k = qr << 8 | qg << 4 | qb;
					r += sumR[k];
					g += sumG[k];
					b += sumB[k];
					n += weight[k];
				}
		if (n == 0) return -1;
		return (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
	}

	private static int family(int[] weight, int k) {
		int br = k >> 8, bg = k >> 4 & 15, bb = k & 15, sum = 0;
		for (int dr = -1; dr <= 1; dr++)
			for (int dg = -1; dg <= 1; dg++)
				for (int db = -1; db <= 1; db++) {
					int qr = br + dr, qg = bg + dg, qb = bb + db;
					if (qr < 0 || qg < 0 || qb < 0 || qr > 15 || qg > 15 || qb > 15) continue;
					sum += weight[qr << 8 | qg << 4 | qb];
				}
		return sum;
	}
}
