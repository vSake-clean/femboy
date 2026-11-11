package pl.vsake.femboy.net;

import pl.vsake.femboy.fx.Emote;
import pl.vsake.femboy.fx.Pose;

import java.util.Locale;
import java.util.UUID;

/**
 * Drobne sygnały między graczami z modem, jeden wspólny kanał (temat femboy/…/sig/UUID i pakiet Signal):
 * emotka nad głową, clicker, smycz, animacje ciała, boop, przytulas / za ręce / noszenie, wskaźnik laserowy i kłębek. Nonce rozpoznaje duplikaty (kilka brokerów + serwer z modem).
 * Bez klas klienta: ładowane też na serwerze (sprawdzanie pakietów) i w testach.
 */
public record Signal(Kind kind, String arg, int nonce) {
	public enum Kind {
		/** arg = id emotki. */
		EMOTE("emote"),
		/** arg = "-". */
		CLICK("click"),
		/** arg = UUID prowadzonego gracza albo "-" (puściłem smycz); odświeżane co 2 s. */
		LEASH("leash"),
		/** arg = id animacji ciała albo "-" (koniec); trzymane pozy odświeżane co 2 s. */
		POSE("pose"),
		/** arg = UUID osoby, której robię boop w nosek. */
		BOOP("boop"),
		/** arg = litera rodzaju (h przytulas, d za ręce, c na rękach, p na barana) + UUID drugiej osoby albo "-"; odświeżane co 2 s. */
		BOND("bond"),
		/** arg = UUID osoby, której pozwalam się nosić, albo "-" (już nie); odświeżane co 2 s. */
		OK("ok"),
		/** arg = "x,y,z" kropki wskaźnika laserowego (jedno miejsce po przecinku) albo "-" (zgasł). */
		LASER("laser"),
		/** arg = "x,y,z,vx,vy,vz" rzuconego kłębka włóczki. */
		YARN("yarn");

		public final String id;

		Kind(String id) {
			this.id = id;
		}

		static Kind byId(String s) {
			if (s == null) return null;
			String k = s.toLowerCase(Locale.ROOT);
			for (Kind x : values()) if (x.id.equals(k)) return x;
			return null;
		}
	}

	public static final String NONE = "-";
	public static final int MAX_ARG = 64;
	/** Rodzaje więzi (BOND): przytulas, za ręce, na rękach, na barana. */
	public static final String BOND_KINDS = "hdcp";

	public static Signal emote(Emote e, int nonce) {
		return new Signal(Kind.EMOTE, e.id, nonce);
	}

	public static Signal click(int nonce) {
		return new Signal(Kind.CLICK, NONE, nonce);
	}

	public static Signal leash(UUID target, int nonce) {
		return new Signal(Kind.LEASH, target == null ? NONE : target.toString(), nonce);
	}

	/** Tekst do brokera: "emote heart 1a2b3c4d". */
	public String encode() {
		return kind.id + " " + arg + " " + Integer.toHexString(nonce);
	}

	/** null = śmieci / nieznany sygnał (np. od nowszej wersji moda). */
	public static Signal decode(String s) {
		if (s == null || s.length() > 110) return null;
		String[] p = s.trim().split(" ");
		if (p.length != 3) return null;
		try {
			return of(p[0], p[1], Integer.parseUnsignedInt(p[2], 16));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Sprawdza wszystko, co przyszło z sieci; null = odrzucić. */
	public static Signal of(String kind, String arg, int nonce) {
		Kind k = Kind.byId(kind);
		if (k == null || arg == null || arg.length() > MAX_ARG) return null;
		switch (k) {
			case EMOTE -> {
				Emote e = Emote.byId(arg);
				if (e == null) return null;
				arg = e.id;
			}
			case CLICK -> arg = NONE;
			case LEASH, OK -> {
				if (!arg.equals(NONE)) {
					arg = uuid(arg);
					if (arg == null) return null;
				}
			}
			case BOOP -> {
				arg = uuid(arg);
				if (arg == null) return null;
			}
			case POSE -> {
				if (!arg.equals(NONE)) {
					Pose p = Pose.byId(arg);
					if (p == null) return null;
					arg = p.id;
				}
			}
			case BOND -> {
				if (!arg.equals(NONE)) {
					if (arg.length() < 2 || BOND_KINDS.indexOf(arg.charAt(0)) < 0) return null;
					String u = uuid(arg.substring(1));
					if (u == null) return null;
					arg = arg.charAt(0) + u;
				}
			}
			case LASER -> {
				if (!arg.equals(NONE) && numbers(arg, 3) == null) return null;
			}
			case YARN -> {
				double[] n = numbers(arg, 6);
				// prędkość rzutu ograniczona – nikt nie wystrzeli kłębka przez pół mapy
				if (n == null || Math.abs(n[3]) > 2 || Math.abs(n[4]) > 2 || Math.abs(n[5]) > 2) return null;
			}
		}
		return new Signal(k, arg, nonce);
	}

	private static String uuid(String s) {
		try {
			return UUID.fromString(s).toString();
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** "1.5,-2,3" → liczby; null, gdy jest ich inna liczba, są nieskończone albo spoza świata. */
	public static double[] numbers(String s, int count) {
		if (s == null) return null;
		String[] p = s.split(",");
		if (p.length != count) return null;
		double[] out = new double[count];
		try {
			for (int i = 0; i < count; i++) {
				out[i] = Double.parseDouble(p[i]);
				if (!Double.isFinite(out[i]) || Math.abs(out[i]) > 3.0e7) return null;
			}
		} catch (NumberFormatException e) {
			return null;
		}
		return out;
	}

	private static String num(double v, int decimals) {
		String s = String.format(Locale.ROOT, "%." + decimals + "f", v);
		if (s.indexOf('.') >= 0) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
		return s.equals("-0") ? "0" : s;
	}

	public static Signal pose(Pose p, int nonce) {
		return new Signal(Kind.POSE, p == null ? NONE : p.id, nonce);
	}

	public static Signal boop(UUID target, int nonce) {
		return new Signal(Kind.BOOP, target.toString(), nonce);
	}

	/** @param kind litera z BOND_KINDS; target null = koniec */
	public static Signal bond(char kind, UUID target, int nonce) {
		return new Signal(Kind.BOND, target == null ? NONE : kind + target.toString(), nonce);
	}

	public static Signal ok(UUID holder, int nonce) {
		return new Signal(Kind.OK, holder == null ? NONE : holder.toString(), nonce);
	}

	public static Signal laser(double x, double y, double z, int nonce) {
		return new Signal(Kind.LASER, num(x, 1) + "," + num(y, 1) + "," + num(z, 1), nonce);
	}

	public static Signal laserOff(int nonce) {
		return new Signal(Kind.LASER, NONE, nonce);
	}

	public static Signal yarn(double x, double y, double z, double vx, double vy, double vz, int nonce) {
		return new Signal(Kind.YARN, num(x, 1) + "," + num(y, 1) + "," + num(z, 1) + "," + num(vx, 2) + "," + num(vy, 2) + "," + num(vz, 2), nonce);
	}

	/** Animacja ciała albo null ("-" = koniec / inny sygnał). */
	public Pose poseArg() {
		return kind == Kind.POSE ? Pose.byId(arg) : null;
	}

	/** UUID z sygnałów BOOP / OK / LEASH albo null. */
	public UUID uuidArg() {
		return (kind == Kind.BOOP || kind == Kind.OK || kind == Kind.LEASH) && !arg.equals(NONE) ? UUID.fromString(arg) : null;
	}

	/** Litera rodzaju więzi albo 0 (koniec). */
	public char bondKind() {
		return kind == Kind.BOND && !arg.equals(NONE) ? arg.charAt(0) : 0;
	}

	public UUID bondTarget() {
		return kind == Kind.BOND && !arg.equals(NONE) ? UUID.fromString(arg.substring(1)) : null;
	}

	/** Liczby z LASER (3) / YARN (6) albo null. */
	public double[] numbersArg() {
		return kind == Kind.LASER ? (arg.equals(NONE) ? null : numbers(arg, 3)) : kind == Kind.YARN ? numbers(arg, 6) : null;
	}

	public Emote emoteArg() {
		return kind == Kind.EMOTE ? Emote.byId(arg) : null;
	}

	/** Cel smyczy albo null (puszczona / inny sygnał). */
	public UUID leashTarget() {
		return kind == Kind.LEASH && !arg.equals(NONE) ? UUID.fromString(arg) : null;
	}
}
