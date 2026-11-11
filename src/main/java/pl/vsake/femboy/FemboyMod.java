package pl.vsake.femboy;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.vsake.femboy.acc.Accessories;
import pl.vsake.femboy.acc.AccessoryFeature;
import pl.vsake.femboy.fx.Clicker;
import pl.vsake.femboy.fx.Effects;
import pl.vsake.femboy.fx.Leash;
import pl.vsake.femboy.fx.Reactions;
import pl.vsake.femboy.gui.FemboyScreen;
import pl.vsake.femboy.net.NetClient;
import pl.vsake.femboy.pat.PatPat;
import pl.vsake.femboy.voice.Voices;

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
		// klawisz otwierający GUI wyboru wyglądu (domyślnie G) – pierwszy na liście klawiszy w oknie moda
		KeyBinding guiKey = Keys.register("Ogólne", "Okno moda", "gui", GLFW.GLFW_KEY_G);
		PatPat.init();
		// odgłosy (miau, hau…) pod klawiszami – słyszą je inni gracze z modem
		Voices.init();
		// moje dodatki i głaskanie dla innych graczy z modem (gdy serwer ma moda) – patrz net/
		NetClient.init();
		// emotki nad głową, clicker, smycz, dzwoneczki / mruczenie / serduszka – patrz fx/
		Reactions.init();
		Clicker.init();
		Leash.init();
		Effects.init();
		// animacje ciała, boop, przytulas / za ręce / noszenie, zabawki (laser, kłębek), ślady łapek, szafa
		pl.vsake.femboy.fx.Poses.init();
		pl.vsake.femboy.fx.Boop.init();
		pl.vsake.femboy.fx.Bonds.init();
		pl.vsake.femboy.fx.Toys.init();
		pl.vsake.femboy.fx.Prints.init();
		pl.vsake.femboy.fx.WorldDraw.init();
		Wardrobe.init();
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// okno moda i komunikaty: polski tylko przy polskim języku gry, inaczej angielski
			Lang.polish = client.getLanguageManager().getLanguage().toLowerCase(java.util.Locale.ROOT).startsWith("pl");
			while (guiKey.wasPressed()) if (client.player != null) FemboyScreen.open();
		});
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
		// zrzuty do README (tylko z -Dfemboy.shots=…, w zwykłej grze nic nie robi)
		pl.vsake.femboy.dev.ShotDriver.initIfEnabled();
		// po wyjściu ze świata/serwera zwalniamy wygenerowane tekstury i przestajemy głaskać
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			SkinManager.clearGenerated();
			Accessories.clear();
			PatPat.stop();
			PatPat.clearRemote();
			Voices.clear();
			Reactions.clear();
			Leash.clear();
			Effects.clear();
			pl.vsake.femboy.fx.Poses.clear();
			pl.vsake.femboy.fx.Boop.clear();
			pl.vsake.femboy.fx.Bonds.clear();
			pl.vsake.femboy.fx.Toys.clear();
			pl.vsake.femboy.fx.Prints.clear();
		}));
	}
}
