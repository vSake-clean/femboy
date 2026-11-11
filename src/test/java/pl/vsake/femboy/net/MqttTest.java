package pl.vsake.femboy.net;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Klient MQTT kontra udawany broker na localhoście: bajty pakietów zgodne z MQTT 3.1.1. */
class MqttTest {
	private static int readLen(DataInputStream in) throws Exception {
		int v = 0, mul = 1, b;
		do {
			b = in.readUnsignedByte();
			v += (b & 0x7F) * mul;
			mul *= 128;
		} while ((b & 0x80) != 0);
		return v;
	}

	private record Packet(int header, byte[] body) {
	}

	private static Packet read(DataInputStream in) throws Exception {
		int h = in.readUnsignedByte();
		byte[] b = new byte[readLen(in)];
		in.readFully(b);
		return new Packet(h, b);
	}

	private static String str(byte[] b, int at) {
		int len = ((b[at] & 0xFF) << 8) | (b[at + 1] & 0xFF);
		return new String(b, at + 2, len, StandardCharsets.UTF_8);
	}

	@Test
	void websocketAcceptKey() {
		// przykład z RFC 6455
		assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", WebSocket.expectedAccept("dGhlIHNhbXBsZSBub25jZQ=="));
	}

	@Test
	void connectSubscribePublishReceive() throws Exception {
		BlockingQueue<String> got = new ArrayBlockingQueue<>(4);
		BlockingQueue<Packet> broker = new ArrayBlockingQueue<>(8);
		try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			Thread fake = new Thread(() -> {
				try (Socket s = server.accept()) {
					DataInputStream in = new DataInputStream(s.getInputStream());
					OutputStream out = s.getOutputStream();
					broker.add(read(in));                       // CONNECT
					out.write(new byte[]{0x20, 2, 0, 0});       // CONNACK ok
					broker.add(read(in));                       // SUBSCRIBE
					// PUBLISH z dłuższym ładunkiem (dwubajtowa długość)
					String topic = "femboy/v1/look/x";
					byte[] payload = "a".repeat(200).getBytes(StandardCharsets.UTF_8);
					ByteArrayOutputStream b = new ByteArrayOutputStream();
					b.write(0);
					b.write(topic.length());
					b.write(topic.getBytes(StandardCharsets.UTF_8));
					b.write(payload);
					byte[] body = b.toByteArray();
					out.write(new byte[]{0x31, (byte) (body.length % 128 | 0x80), (byte) (body.length / 128)});
					out.write(body);
					out.flush();
					broker.add(read(in));                       // PUBLISH od klienta
					broker.add(read(in));                       // PINGREQ
				} catch (Exception ignored) {
				}
			});
			fake.setDaemon(true);
			fake.start();

			Mqtt m = Mqtt.connect(new Mqtt.Endpoint("localhost", server.getLocalPort(), Mqtt.Kind.TCP, null), "femboy-test", 30, new Mqtt.Listener() {
				@Override
				public void message(String topic, byte[] payload) {
					got.add(topic + "=" + payload.length);
				}

				@Override
				public void closed(Mqtt mqtt, Throwable why) {
				}
			});
			Packet connect = broker.poll(5, TimeUnit.SECONDS);
			assertNotNull(connect);
			assertEquals(0x10, connect.header());
			assertEquals("MQTT", str(connect.body(), 0));
			assertEquals(4, connect.body()[6]);
			assertEquals("femboy-test", str(connect.body(), 10));

			m.subscribe(List.of("femboy/v1/look/a", "femboy/v1/pat/a"));
			Packet sub = broker.poll(5, TimeUnit.SECONDS);
			assertNotNull(sub);
			assertEquals(0x82, sub.header());
			assertEquals("femboy/v1/look/a", str(sub.body(), 2));

			assertEquals("femboy/v1/look/x=200", got.poll(5, TimeUnit.SECONDS));

			m.publish("femboy/v1/look/me", "hi".getBytes(StandardCharsets.UTF_8), true);
			Packet pub = broker.poll(5, TimeUnit.SECONDS);
			assertNotNull(pub);
			assertEquals(0x31, pub.header());
			assertEquals("femboy/v1/look/me", str(pub.body(), 0));

			m.ping();
			Packet ping = broker.poll(5, TimeUnit.SECONDS);
			assertNotNull(ping);
			assertEquals(0xC0, ping.header());
			m.close();
			assertFalse(m.isOpen());
		}
	}
}
