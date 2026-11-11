package pl.vsake.femboy.net;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

/**
 * Najprostszy klient MQTT 3.1.1 (QoS 0): łączy się, subskrybuje, publikuje, pinguje. Bez bibliotek – mod ma być jednym jarem.
 * Wiadomości przychodzą na osobnym wątku (Listener), zapisywać można z dowolnego wątku.
 */
final class Mqtt implements Closeable {
	interface Listener {
		void message(String topic, byte[] payload);

		void closed(Mqtt mqtt, Throwable why);
	}

	/** Większych wiadomości nie przyjmujemy (nasze mają ~150 bajtów). */
	private static final int MAX_IN = 16 * 1024;

	private final Socket socket;
	private final OutputStream out;
	private final DataInputStream in;
	private final Listener listener;
	private final Object writeLock = new Object();
	private volatile boolean closed;
	private int packetId;
	final String host;

	private Mqtt(String host, Socket socket, InputStream in, OutputStream out, Listener listener) {
		this.host = host;
		this.socket = socket;
		this.out = out;
		this.in = new DataInputStream(in);
		this.listener = listener;
	}

	/** Jak się dostać do brokera: TLS (8883), zwykły TCP (1883) albo WebSocket przez HTTPS (443 – przechodzi przez prawie każde Wi-Fi). */
	enum Kind { TLS, TCP, WSS }

	record Endpoint(String host, int port, Kind kind, String path) {
		@Override
		public String toString() {
			return (kind == Kind.WSS ? "wss://" : kind == Kind.TLS ? "mqtts://" : "mqtt://") + host + ":" + port + (path == null ? "" : path);
		}
	}

	/** Łączy i czeka na CONNACK. keepAlive w sekundach. */
	static Mqtt connect(Endpoint ep, String clientId, int keepAlive, Listener listener) throws IOException {
		Socket raw = new Socket();
		try {
			raw.connect(new InetSocketAddress(ep.host(), ep.port()), 8000);
			raw.setTcpNoDelay(true);
			raw.setSoTimeout(10_000);
			Socket s = raw;
			if (ep.kind() != Kind.TCP) {
				SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(raw, ep.host(), ep.port(), true);
				SSLParameters p = ssl.getSSLParameters();
				p.setEndpointIdentificationAlgorithm("HTTPS");
				ssl.setSSLParameters(p);
				ssl.startHandshake();
				s = ssl;
			}
			InputStream rin = s.getInputStream();
			OutputStream rout = s.getOutputStream();
			if (ep.kind() == Kind.WSS) {
				WebSocket ws = WebSocket.open(rin, rout, ep.host(), ep.port(), ep.path(), "mqtt");
				rin = ws.in;
				rout = ws.out;
			}
			Mqtt m = new Mqtt(ep.host(), s, rin, rout, listener);
			ByteArrayOutputStream b = new ByteArrayOutputStream();
			str(b, "MQTT");
			b.write(4);          // MQTT 3.1.1
			b.write(0x02);       // clean session, bez loginu
			b.write(keepAlive >> 8);
			b.write(keepAlive);
			str(b, clientId);
			m.send(0x10, b.toByteArray());
			int type = m.in.readUnsignedByte();
			int len = m.readLength();
			byte[] ack = new byte[len];
			m.in.readFully(ack);
			if (type != 0x20 || len < 2 || ack[1] != 0) throw new IOException("broker odrzucił połączenie (" + (len >= 2 ? ack[1] : -1) + ")");
			// brak czegokolwiek (nawet odpowiedzi na ping) przez 2× keepAlive = połączenie martwe
			s.setSoTimeout(keepAlive * 2000);
			Thread t = new Thread(m::readLoop, "femboy-mqtt " + ep);
			t.setDaemon(true);
			t.start();
			return m;
		} catch (IOException | RuntimeException e) {
			try {
				raw.close();
			} catch (IOException ignored) {
			}
			throw e;
		}
	}

	boolean isOpen() {
		return !closed;
	}

	void publish(String topic, byte[] payload, boolean retain) throws IOException {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		str(b, topic);
		b.write(payload);
		send(0x30 | (retain ? 1 : 0), b.toByteArray());
	}

	void subscribe(Collection<String> topics) throws IOException {
		if (topics.isEmpty()) return;
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		id(b);
		for (String t : topics) {
			str(b, t);
			b.write(0); // QoS 0
		}
		send(0x82, b.toByteArray());
	}

	void unsubscribe(Collection<String> topics) throws IOException {
		if (topics.isEmpty()) return;
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		id(b);
		for (String t : topics) str(b, t);
		send(0xA2, b.toByteArray());
	}

	void ping() throws IOException {
		send(0xC0, new byte[0]);
	}

	@Override
	public void close() {
		if (closed) return;
		closed = true;
		try {
			synchronized (writeLock) {
				out.write(new byte[]{(byte) 0xE0, 0});
				out.flush();
			}
		} catch (IOException ignored) {
		}
		try {
			socket.close();
		} catch (IOException ignored) {
		}
	}

	private void readLoop() {
		Throwable why = null;
		try {
			while (!closed) {
				int header = in.readUnsignedByte();
				int len = readLength();
				if (len > MAX_IN) throw new IOException("za duża wiadomość: " + len);
				byte[] body = new byte[len];
				in.readFully(body);
				if ((header >> 4) == 3) onPublish(header, body); // SUBACK, UNSUBACK, PINGRESP – nic nie trzeba robić
			}
		} catch (EOFException e) {
			why = e;
		} catch (Throwable e) {
			why = e;
		}
		boolean wasOpen = !closed;
		close();
		if (wasOpen) listener.closed(this, why);
	}

	private void onPublish(int header, byte[] body) throws IOException {
		int qos = (header >> 1) & 3;
		int tl = ((body[0] & 0xFF) << 8) | (body[1] & 0xFF);
		String topic = new String(body, 2, tl, StandardCharsets.UTF_8);
		int pos = 2 + tl;
		if (qos > 0) {
			if (qos == 1) send(0x40, new byte[]{body[pos], body[pos + 1]}); // PUBACK
			pos += 2;
		}
		byte[] payload = new byte[body.length - pos];
		System.arraycopy(body, pos, payload, 0, payload.length);
		try {
			listener.message(topic, payload);
		} catch (Throwable ignored) { // zła wiadomość od kogoś nie zrywa połączenia
		}
	}

	private int readLength() throws IOException {
		int value = 0, mul = 1, b;
		do {
			b = in.readUnsignedByte();
			value += (b & 0x7F) * mul;
			mul *= 128;
			if (mul > 128 * 128 * 128 * 128) throw new IOException("zła długość pakietu");
		} while ((b & 0x80) != 0);
		return value;
	}

	private void send(int header, byte[] body) throws IOException {
		if (closed) throw new IOException("zamknięte");
		ByteArrayOutputStream b = new ByteArrayOutputStream(body.length + 5);
		b.write(header);
		int len = body.length;
		do {
			int d = len % 128;
			len /= 128;
			b.write(len > 0 ? d | 0x80 : d);
		} while (len > 0);
		b.write(body);
		try {
			synchronized (writeLock) {
				out.write(b.toByteArray());
				out.flush();
			}
		} catch (IOException e) {
			close();
			throw e;
		}
	}

	private void id(ByteArrayOutputStream b) {
		synchronized (writeLock) {
			packetId = packetId % 65535 + 1;
			b.write(packetId >> 8);
			b.write(packetId);
		}
	}

	private static void str(ByteArrayOutputStream b, String s) throws IOException {
		byte[] u = s.getBytes(StandardCharsets.UTF_8);
		b.write(u.length >> 8);
		b.write(u.length);
		b.write(u);
	}
}
