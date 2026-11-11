package pl.vsake.femboy.net;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prawdziwe publiczne brokery (potrzebny internet). Tylko na żądanie:
 *   FEMBOY_LIVE=1 ./gradlew test --offline --tests '*LiveBrokerTest' -i
 * Dla każdego sposobu połączenia: wysyła wiadomość do siebie i czeka, aż wróci.
 */
@EnabledIfEnvironmentVariable(named = "FEMBOY_LIVE", matches = "1")
class LiveBrokerTest {
	@Test
	void everyEndpoint() throws Exception {
		int ok = 0;
		for (Mqtt.Endpoint ep : Relay.endpoints()) {
			String topic = "femboy/v1/test/" + UUID.randomUUID();
			BlockingQueue<String> got = new ArrayBlockingQueue<>(2);
			long t0 = System.currentTimeMillis();
			try {
				Mqtt m = Mqtt.connect(ep, "femboy-test-" + UUID.randomUUID().toString().substring(0, 8), 30, new Mqtt.Listener() {
					@Override
					public void message(String t, byte[] payload) {
						got.add(new String(payload, StandardCharsets.UTF_8));
					}

					@Override
					public void closed(Mqtt mqtt, Throwable why) {
					}
				});
				m.subscribe(List.of(topic));
				Thread.sleep(500);
				m.publish(topic, "pat pat".getBytes(StandardCharsets.UTF_8), false);
				String back = got.poll(8, TimeUnit.SECONDS);
				m.close();
				if ("pat pat".equals(back)) {
					ok++;
					System.out.println("OK    " + ep + " (" + (System.currentTimeMillis() - t0) + " ms)");
				} else System.out.println("BRAK  " + ep + " – połączono, ale wiadomość nie wróciła");
			} catch (Exception e) {
				System.out.println("BŁĄD  " + ep + " – " + e);
			}
		}
		assertTrue(ok > 0, "żaden broker nie działa z tej sieci");
	}
}
