package eu.ha3.presencefootsteps;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import eu.ha3.mc.quick.update.UpdateChecker;
import eu.ha3.mc.quick.update.UpdaterConfig;
import eu.ha3.presencefootsteps.sound.SoundEngine;
import eu.ha3.presencefootsteps.util.Edge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterDebugEntriesEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = PresenceFootsteps.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = PresenceFootsteps.MODID, value = Dist.CLIENT)
public class PresenceFootsteps {
    public static final Logger logger = LogManager.getLogger("PFSolver");

    public static final String MODID = "presencefootsteps";
    private static final KeyMapping.Category KEY_BINDING_CATEGORY = KeyMapping.Category.register(id("category"));
    private static final String UPDATER_ENDPOINT = "https://raw.githubusercontent.com/Sollace/Presence-Footsteps/master/version/latest.json";

    public static final Component MOD_NAME = Component.translatable("mod.presencefootsteps.name");

    public static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath(MODID, name);
    }

    private static PresenceFootsteps instance;

    public static PresenceFootsteps getInstance() {
        return instance;
    }

    private final Path pfFolder = FMLPaths.CONFIGDIR.get().resolve("presencefootsteps");
    private final PFConfig config = new PFConfig(pfFolder.resolve("userconfig.json"), this);
    private final SoundEngine engine = new SoundEngine(config);
    private final PFDebugHud debugHud = new PFDebugHud(engine);

    private final UpdaterConfig updaterConfig = new UpdaterConfig(pfFolder.resolve("updater.json"));
    private final UpdateChecker updater = new UpdateChecker(updaterConfig, MODID, UPDATER_ENDPOINT, (newVersion, _) -> {
        showSystemToast(
                Component.translatable("pf.update.title"),
                Component.translatable("pf.update.text", newVersion.version().getFriendlyString(), newVersion.minecraft().getFriendlyString())
        );
    });

    private final KeyMapping optionsKeyBinding = new KeyMapping("key.presencefootsteps.settings", InputConstants.Type.KEYSYM, InputConstants.KEY_F10, KEY_BINDING_CATEGORY);
    private final KeyMapping toggleKeyBinding = new KeyMapping("key.presencefootsteps.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, KEY_BINDING_CATEGORY);
    private final KeyMapping debugToggleKeyBinding = new KeyMapping("key.presencefootsteps.debug_toggle", InputConstants.Type.KEYSYM, InputConstants.KEY_Z, KEY_BINDING_CATEGORY);
    private final Edge toggler = new Edge(z -> {
        if (z) {
            config.toggleDisabled();
        }
    });
    private final Edge debugToggle = new Edge(z -> {
        if (z) {
            Minecraft.getInstance().debugEntries.toggleStatus(PFDebugHud.ID);
        }
    });

    private final AtomicBoolean configChanged = new AtomicBoolean();

    public PresenceFootsteps(ModContainer container) {
        instance = this;

        // Allows NeoForge to show this mod's config screen from the mod list.
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, modListScreen) -> new PFOptionsScreen(modListScreen));

        updaterConfig.load();
        config.load();
        config.onChangedExternally(_ -> configChanged.set(true));
    }

    public PFDebugHud getDebugHud() {
        return debugHud;
    }

    public SoundEngine getEngine() {
        return engine;
    }
    public PFConfig getConfig() {
        return config;
    }

    public KeyMapping getOptionsKeyBinding() {
        return optionsKeyBinding;
    }

    public UpdateChecker getUpdateChecker() {
        return updater;
    }

    /**
     * 26.1's Minecraft.isSingleplayer(): a local integrated server that is not published to LAN.
     */
    public static boolean isLocalServerUnpublished() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        return server != null && !server.isPublished();
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        getInstance().onTick(Minecraft.getInstance());
    }

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        PresenceFootsteps pf = getInstance();
        event.register(pf.optionsKeyBinding);
        event.register(pf.toggleKeyBinding);
        event.register(pf.debugToggleKeyBinding);
    }

    @SubscribeEvent
    static void onRegisterReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(SoundEngine.ID, getInstance().getEngine());
    }

    @SubscribeEvent
    static void onRegisterDebugEntries(RegisterDebugEntriesEvent event) {
        event.register(PFDebugHud.ID, getInstance().getDebugHud());
    }

    private void onTick(Minecraft client) {
        if (client.gui.screen() instanceof PFOptionsScreen screen && configChanged.getAndSet(false)) {
            screen.init(screen.width, screen.height);
        }

        debugToggle.accept(InputConstants.isKeyDown(client.getWindow(), InputConstants.KEY_F3) && debugToggleKeyBinding.isDown());

        Optional.ofNullable(client.player).filter(e -> !e.isRemoved()).ifPresent(cameraEntity -> {
            if (client.gui.screen() == null) {
                if (optionsKeyBinding.isDown()) {
                    client.setScreenAndShow(new PFOptionsScreen(client.gui.screen()));
                }
                toggler.accept(toggleKeyBinding.isDown());
            }

            engine.onFrame(client, cameraEntity);

            if (!ModList.get().isLoaded("modmenu")) {
                updater.attempt();
            }
        });
    }

    void onEnabledStateChange(boolean enabled) {
        engine.reload();
        showSystemToast(
                MOD_NAME,
                Component.translatable("key.presencefootsteps.toggle." + (enabled ? "enabled" : "disabled")).withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY)
        );
    }

    public void showSystemToast(Component title, Component body) {
        Minecraft client = Minecraft.getInstance();
        SystemToast.addOrUpdate(client.gui.toastManager(), SystemToast.SystemToastId.PACK_LOAD_FAILURE, title, body);
    }
}
