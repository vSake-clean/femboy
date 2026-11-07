package pl.vsake.femboy;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.vsake.femboy.acc.Accessories;
import pl.vsake.femboy.acc.AccessoryFeature;
import pl.vsake.femboy.pat.PatPat;

import java.nio.file.Path;

/** Zamienia skiny wszystkich graczy na skiny femboyów / e-girls. Tylko po twojej stronie (klient). */
public final class FemboyMod implements ClientModInitializer {
	public static final String ID = "femboy";
	public static final Logger LOG = LoggerFactory.getLogger(ID);
	public static FemboyConfig CONFIG = new FemboyConfig();

	@Override
	public void onInitializeClient() {
		Path dir = FabricLoader.getInstance().getConfigDir();
		CONFIG = FemboyConfig.load(dir.resolve("femboy.json"));
		SkinManager.init(dir.resolve("femboy"));
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> FemboyCommands.register(dispatcher));
		PatPat.init();
		// dodatki (uszy, ogony…): dodatkowa warstwa w rendererze gracza, jak zbroja albo peleryna
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (!(renderer instanceof PlayerEntityRenderer<?> player)) return;
			try {
				helper.register(new AccessoryFeature(player));
			} catch (Throwable t) { // błąd w modelu dodatków nie może wywalić gry przy starcie
				Accessories.failed(t);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> Accessories.tick());
		// po wyjściu ze świata/serwera zwalniamy wygenerowane tekstury i przestajemy głaskać
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			SkinManager.clearGenerated();
			Accessories.clear();
			PatPat.stop();
		}));
	}
}
