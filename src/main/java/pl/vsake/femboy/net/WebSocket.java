package pl.vsake.femboy.net;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

/**
 * WebSocket (RFC 6455) jako zwykłe strumienie bajtów – żeby MQTT szło przez port 443 jak strona HTTPS.
 * out: każde write() = jedna ramka binarna (MQTT pisze cały pakiet naraz). in: sklejone dane z ramek, ping → pong.
 */
final class WebSocket {
	private static final SecureRandom RANDOM = new SecureRandom();

	final InputStream in;
	final OutputStream out;

	private WebSocket(InputStream rawIn, OutputStream rawOut) {
		Out o = new Out(rawOut);
		this.out = o;
		this.in = new In(rawIn, o);
	}

	static WebSocket open(InputStream rawIn, OutputStream rawOut, String host, int port, String path, String protocol) throws IOException {
		byte[] keyBytes = new byte[16];
		RANDOM.nextBytes(keyBytes);
		String key = Base64.getEncoder().encodeToString(keyBytes);
		String req = "GET " + (path == null || path.isEmpty() ? "/" : path) + " HTTP/1.1\r\n"
			+ "Host: " + host + (port == 443 ? "" : ":" + port) + "\r\n"
			+ "Upgrade: websocket\r\n"
			+ "Connection: Upgrade\r\n"
			+ "Sec-WebSocket-Key: " + key + "\r\n"
			+ "Sec-WebSocket-Version: 13\r\n"
			+ "Sec-WebSocket-Protocol: " + protocol + "\r\n"
			+ "User-Agent: femboy-mod\r\n\r\n";
		rawOut.write(req.getBytes(StandardCharsets.US_ASCII));
		rawOut.flush();
		String status = line(rawIn);
		if (!status.startsWith("HTTP/1.1 101") && !status.startsWith("HTTP/1.0 101"))
			throw new IOException("WebSocket odrzucony: " + status);
		String accept = null;
		for (String l = line(rawIn); !l.isEmpty(); l = line(rawIn)) {
			int c = l.indexOf(':');
			if (c > 0 && l.substring(0, c).trim().toLowerCase(Locale.ROOT).equals("sec-websocket-accept")) accept = l.substring(c + 1).trim();
		}
		if (!expectedAccept(key).equals(accept)) throw new IOException("WebSocket: zła odpowiedź serwera");
		return new WebSocket(rawIn, rawOut);
	}

	static String expectedAccept(String key) {
		try {
			MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
			byte[] d = sha1.digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII));
			return Base64.getEncoder().encodeToString(d);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static String line(InputStream in) throws IOException {
		StringBuilder sb = new StringBuilder();
		while (true) {
			int b = in.read();
			if (b < 0) throw new EOFException();
			if (b == '\n') break;
			if (b != '\r') sb.append((char) b);
			if (sb.length() > 8192) throw new IOException("za długi nagłówek");
		}
		return sb.toString();
	}

	private static final class Out extends OutputStream {
		private final OutputStream raw;

		Out(OutputStream raw) {
			this.raw = raw;
		}

		@Override
		public void write(int b) throws IOException {
			write(new byte[]{(byte) b}, 0, 1);
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			frame(0x2, b, off, len);
		}

		synchronized void frame(int opcode, byte[] b, int off, int len) throws IOException {
			ByteArrayOutputStream f = new ByteArrayOutputStream(len + 14);
			f.write(0x80 | opcode);
			if (len < 126) f.write(0x80 | len);
			else if (len < 65536) {
				f.write(0x80 | 126);
				f.write(len >> 8);
				f.write(len);
			} else {
				f.write(0x80 | 127);
				for (int i = 7; i >= 0; i--) f.write((int) ((long) len >> (8 * i)));
			}
			byte[] mask = new byte[4];
			RANDOM.nextBytes(mask);
			f.write(mask, 0, 4);
			for (int i = 0; i < len; i++) f.write(b[off + i] ^ mask[i & 3]);
			raw.write(f.toByteArray());
			raw.flush();
		}

		@Override
		public void flush() throws IOException {
			raw.flush();
		}
	}

	private static final class In extends InputStream {
		private final InputStream raw;
		private final Out out;
		private byte[] buf = new byte[0];
		private int pos;

		In(InputStream raw, Out out) {
			this.raw = raw;
			this.out = out;
		}

		@Override
		public int read() throws IOException {
			while (pos >= buf.length) next();
			return buf[pos++] & 0xFF;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			if (len == 0) return 0;
			while (pos >= buf.length) next();
			int n = Math.min(len, buf.length - pos);
			System.arraycopy(buf, pos, b, off, n);
			pos += n;
			return n;
		}

		private int u8() throws IOException {
			int b = raw.read();
			if (b < 0) throw new EOFException();
			return b;
		}

		private void next() throws IOException {
			int h = u8(), l = u8();
			int opcode = h & 0x0F;
			long len = l & 0x7F;
			if (len == 126) len = (u8() << 8) | u8();
			else if (len == 127) {
				len = 0;
				for (int i = 0; i < 8; i++) len = (len << 8) | u8();
			}
			if (len > 1 << 20) throw new IOException("za duża ramka");
			byte[] mask = (l & 0x80) != 0 ? new byte[]{(byte) u8(), (byte) u8(), (byte) u8(), (byte) u8()} : null;
			byte[] data = new byte[(int) len];
			for (int i = 0; i < data.length; ) {
				int n = raw.read(data, i, data.length - i);
				if (n < 0) throw new EOFException();
				i += n;
			}
			if (mask != null) for (int i = 0; i < data.length; i++) data[i] ^= mask[i & 3];
			switch (opcode) {
				case 0x0, 0x1, 0x2 -> {
					buf = data;
					pos = 0;
				}
				case 0x8 -> throw new EOFException("WebSocket zamknięty");
				case 0x9 -> out.frame(0xA, data, 0, data.length); // ping → pong
				default -> { // pong i inne – pomijamy
				}
			}
		}
	}
}
