package pl.vsake.femboy.gen;

/**
 * Obróbka skinów z plików na pikselach ARGB (wiersz po wierszu, indeks = y * szerokość + x).
 * Bez klas Minecrafta, więc da się to sprawdzić poza grą.
 */
public final class SkinPixels {
	private SkinPixels() {
	}

	public static final int SIZE = 64;

	/** Obszary bazowej warstwy rąk, których chudy model nie używa: {x0, y0, x1, y1}, razem 64 piksele. */
	private static final int[][] SLIM_UNUSED = {{54, 20, 56, 32}, {50, 16, 52, 20}, {46, 52, 48, 64}, {42, 48, 44, 52}};

	/** Minecraft przyjmuje tylko 64x64 i stare 64x32. */
	public static boolean validSize(int w, int h) {
		return w == SIZE && (h == SIZE || h == SIZE / 2);
	}

	/**
	 * Chudy model, jeśli miejsca nieużywane przez chudy model są w większości puste
	 * (pojedyncze zabłąkane piksele nie robią ze skina szerokiego). Liczone na pikselach prosto z pliku.
	 */
	public static boolean looksSlim(int[] argb, int w, int h) {
		if (w != SIZE || h != SIZE || argb.length != w * h) return false;
		int opaque = 0;
		for (int[] r : SLIM_UNUSED)
			for (int y = r[1]; y < r[3]; y++)
				for (int x = r[0]; x < r[2]; x++)
					if ((argb[y * w + x] >>> 24) != 0) opaque++;
		return opaque < 32;
	}

	/**
	 * To samo, co robi vanilla z pobranym skinem (PlayerSkinTextureDownloader.remapTexture w 1.21.11):
	 * stary 64x32 dostaje lewą rękę i nogę (lustro prawych), a bazowa warstwa jest w pełni kryjąca,
	 * żeby przez przezroczyste piksele nie było widać wnętrza modelu. Wynik zawsze 64x64; null = zły rozmiar.
	 */
	public static int[] remap(int[] argb, int w, int h) {
		if (!validSize(w, h) || argb.length != w * h) return null;
		int[] img = new int[SIZE * SIZE]; // dolna połowa starego skina zostaje pusta (0 = przezroczysty)
		System.arraycopy(argb, 0, img, 0, w * h);
		boolean legacy = h == SIZE / 2;
		if (legacy) {
			copyRect(img, 4, 16, 16, 32, 4, 4);
			copyRect(img, 8, 16, 16, 32, 4, 4);
			copyRect(img, 0, 20, 24, 32, 4, 12);
			copyRect(img, 4, 20, 16, 32, 4, 12);
			copyRect(img, 8, 20, 8, 32, 4, 12);
			copyRect(img, 12, 20, 16, 32, 4, 12);
			copyRect(img, 44, 16, -8, 32, 4, 4);
			copyRect(img, 48, 16, -8, 32, 4, 4);
			copyRect(img, 40, 20, 0, 32, 4, 12);
			copyRect(img, 44, 20, -8, 32, 4, 12);
			copyRect(img, 48, 20, -16, 32, 4, 12);
			copyRect(img, 52, 20, -8, 32, 4, 12);
		}
		stripAlpha(img, 0, 0, 32, 16);
		if (legacy) stripColor(img, 32, 0, 64, 32);
		stripAlpha(img, 0, 16, 64, 32);
		stripAlpha(img, 16, 48, 48, 64);
		return img;
	}

	/** NativeImage.copyRect(x, y, dx, dy, w, h, flipX = true, flipY = false). */
	private static void copyRect(int[] img, int x, int y, int dx, int dy, int w, int h) {
		for (int i = 0; i < h; i++)
			for (int j = 0; j < w; j++)
				img[(y + dy + i) * SIZE + (x + dx + (w - 1 - j))] = img[(y + i) * SIZE + (x + j)];
	}

	private static void stripAlpha(int[] img, int x0, int y0, int x1, int y1) {
		for (int x = x0; x < x1; x++)
			for (int y = y0; y < y1; y++) img[y * SIZE + x] |= 0xFF000000;
	}

	/** Stary skin z całkowicie zamalowaną czapką (częsty błąd edytorów) – czapka znika. */
	private static void stripColor(int[] img, int x0, int y0, int x1, int y1) {
		for (int x = x0; x < x1; x++)
			for (int y = y0; y < y1; y++)
				if ((img[y * SIZE + x] >>> 24) < 128) return;
		for (int x = x0; x < x1; x++)
			for (int y = y0; y < y1; y++) img[y * SIZE + x] &= 0x00FFFFFF;
	}
}
