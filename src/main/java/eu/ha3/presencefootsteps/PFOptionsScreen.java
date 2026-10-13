package eu.ha3.presencefootsteps;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import com.google.gson.FormattingStyle;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;
import com.mojang.serialization.JsonOps;

import eu.ha3.mc.quick.update.Versions;
import eu.ha3.presencefootsteps.config.VolumeOption;
import eu.ha3.presencefootsteps.sound.acoustics.Acoustic;
import eu.ha3.presencefootsteps.sound.acoustics.AcousticsFile;
import eu.ha3.presencefootsteps.sound.generator.Locomotion;
import eu.ha3.presencefootsteps.util.BlockReport;
import eu.ha3.presencefootsteps.util.ResourceUtils;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

class PFOptionsScreen extends Screen {
    public static final Component TITLE = Component.translatable("menu.pf.title");
    public static final Component UP_TO_DATE = Component.translatable("pf.update.up_to_date");
    public static final Component VOLUME_MIN = Component.translatable("menu.pf.volume.min");

    private static final int WIDE_WIDTH = 310;
    private static final int SMALL_WIDTH = 150;
    private static final int ROW_HEIGHT = 20;
    private static final int SCROLL_AREA_MIN_HEIGHT = 130;

    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private final List<AbstractWidget> disableAware = new ArrayList<>();

    @Nullable
    private final Screen parent;

    private ScrollableLayout scrollArea;
    private Button updateButton;

    public PFOptionsScreen(@Nullable Screen parent) {
        super(Component.translatable("%s (%s)", TITLE, PresenceFootsteps.getInstance().getOptionsKeyBinding().getTranslatedKeyMessage()));
        this.parent = parent;
    }

    @Override
    protected void init() {
        disableAware.clear();

        layout.addTitleHeader(getTitle(), font);

        PFConfig config = PresenceFootsteps.getInstance().getConfig();

        LinearLayout rows = LinearLayout.vertical().spacing(6);

        CycleButton<Boolean> disabledToggle = CycleButton.onOffBuilder(config.getDisabled()).create(0, 0, WIDE_WIDTH, ROW_HEIGHT, Component.translatable("menu.pf.disable_mod"), (button, disabled) -> {
            updateDisableState(config.setDisabled(disabled));
        });
        rows.addChild(disabledToggle);

        rows.addChild(new StringWidget(WIDE_WIDTH, ROW_HEIGHT, Component.translatable("menu.pf.group.volume"), font));

        disableAware.add(rows.addChild(new VolumeSlider(WIDE_WIDTH, 0, 100, config.getGlobalVolume(),
                PFOptionsScreen::formatGlobalVolume, config::setGlobalVolume)
                .tooltip("menu.pf.volume.tooltip")));

        rows.addChild(volumeRow(config.clientPlayerVolume, "player", config.otherPlayerVolume, "other_players"));
        rows.addChild(volumeRow(config.hostileEntitiesVolume, "hostile_entities", config.passiveEntitiesVolume, "passive_entities"));
        rows.addChild(volumeRow(config.wetSoundsVolume, "wet", config.foliageSoundsVolume, "foliage"));

        disableAware.add(rows.addChild(new VolumeSlider(WIDE_WIDTH, -100, 100, config.getRunningVolumeIncrease(),
                formatVolume("menu.pf.volume.running"), config::setRunningVolumeIncrease)
                .tooltip("menu.pf.volume.running.tooltip")));

        rows.addChild(new StringWidget(WIDE_WIDTH, ROW_HEIGHT, Component.translatable("menu.pf.group.footsteps"), font));

        disableAware.add(rows.addChild(CycleButton.builder(Locomotion::getOptionName, config.getLocomotion())
                .withValues(Locomotion.values())
                .withTooltip(value -> Tooltip.create(value.getOptionTooltip()))
                .displayOnlyValue()
                .create(0, 0, WIDE_WIDTH, ROW_HEIGHT, Component.translatable("menu.pf.group.footsteps"),
                        (button, value) -> config.setLocomotion(value))));

        LinearLayout targetRow = LinearLayout.horizontal().spacing(10);

        disableAware.add(targetRow.addChild(Button.builder(targetSelectorLabel(config), button -> {
            config.cycleTargetSelector();
            button.setMessage(targetSelectorLabel(config));
        }).size(SMALL_WIDTH, ROW_HEIGHT).build()));

        disableAware.add(targetRow.addChild(Button.builder(multiplayerLabel(config), button -> {
            config.toggleMultiplayer();
            button.setMessage(multiplayerLabel(config));
        }).size(SMALL_WIDTH, ROW_HEIGHT).build()));

        rows.addChild(targetRow);

        LinearLayout footwearRow = LinearLayout.horizontal().spacing(10);

        disableAware.add(footwearRow.addChild(Button.builder(footwearLabel(config), button -> {
            config.toggleFootwear();
            button.setMessage(footwearLabel(config));
        }).size(SMALL_WIDTH, ROW_HEIGHT).build()));

        disableAware.add(footwearRow.addChild(Button.builder(exclusiveModeLabel(config), button -> {
            config.toggleExclusiveMode();
            button.setMessage(exclusiveModeLabel(config));
        }).size(SMALL_WIDTH, ROW_HEIGHT).build()));

        rows.addChild(footwearRow);

        rows.addChild(new StringWidget(WIDE_WIDTH, ROW_HEIGHT, Component.translatable("menu.pf.group.sound_packs"), font));

        disableAware.add(rows.addChild(Button.builder(Component.translatable("options.resourcepack"), button -> {
            minecraft.setScreen(new PackSelectionScreen(
                    minecraft.getResourcePackRepository(),
                    repo -> {
                        minecraft.options.loadSelectedResourcePacks(repo);
                        minecraft.setScreen(this);
                    },
                    minecraft.getResourcePackDirectory(),
                    Component.translatable("resourcePack.title")
            ));
        }).size(WIDE_WIDTH, ROW_HEIGHT).build()));

        rows.addChild(new StringWidget(WIDE_WIDTH, ROW_HEIGHT, Component.translatable("menu.pf.group.debugging"), font));

        LinearLayout reportRow = LinearLayout.horizontal().spacing(10);

        disableAware.add(reportRow.addChild(Button.builder(Component.translatable("menu.pf.report.concise"), button -> {
            button.active = false;
            BlockReport.execute(PresenceFootsteps.getInstance().getEngine().getIsolator(), "report_concise", false).thenRun(() -> button.active = true);
        }).size(SMALL_WIDTH, ROW_HEIGHT).tooltip(Tooltip.create(Component.translatable("menu.pf.report.concise.tooltip"))).build()));

        disableAware.add(reportRow.addChild(Button.builder(Component.translatable("menu.pf.report.full"), button -> {
            button.active = false;
            BlockReport.execute(PresenceFootsteps.getInstance().getEngine().getIsolator(), "report_full", true).thenRun(() -> button.active = true);
        }).size(SMALL_WIDTH, ROW_HEIGHT).tooltip(Tooltip.create(Component.translatable("menu.pf.report.full.tooltip"))).build()));

        rows.addChild(reportRow);

        disableAware.add(rows.addChild(Button.builder(Component.translatable("menu.pf.report.acoustics"), button -> {
            button.active = false;
            BlockReport.execute(loc -> {
                ResourceUtils.forEach(AcousticsFile.FILE_LOCATION, minecraft.getResourceManager(), reader -> {
                    Map<String, Acoustic> acoustics = new HashMap<>();
                    @SuppressWarnings("deprecation")
                    AcousticsFile file = AcousticsFile.read(reader, acoustics::put, true);
                    if (file != null) {
                        for (var acoustic : acoustics.entrySet()) {
                            Acoustic.CODEC.encodeStart(JsonOps.INSTANCE, acoustic.getValue()).resultOrPartial(error -> {
                                PresenceFootsteps.logger.error("Error whilst exporting acoustic: " + error);
                            }).ifPresent(json -> {
                                try (var writer = new JsonWriter(Files.newBufferedWriter(loc.resolve(acoustic.getKey().toLowerCase(Locale.ROOT) + ".json")))) {
                                    writer.setFormattingStyle(FormattingStyle.PRETTY);
                                    Streams.write(json, writer);
                                } catch (IOException e) {
                                    PresenceFootsteps.logger.error("Error whilst exporting acoustics", e);
                                }
                            });
                        }
                    }
                });
            }, "acoustics", "").thenRun(() -> button.active = true);
        }).size(WIDE_WIDTH, ROW_HEIGHT).tooltip(Tooltip.create(Component.translatable("menu.pf.report.acoustics.tooltip"))).build()));

        scrollArea = new ScrollableLayout(minecraft, rows, SCROLL_AREA_MIN_HEIGHT);
        scrollArea.setMinWidth(WIDE_WIDTH);

        layout.addToContents(scrollArea);

        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).build());

        layout.visitWidgets(this::addRenderableWidget);

        updateButton = addRenderableWidget(Button.builder(updateButtonLabel(), button -> {
            button.active = false;
            button.setTooltip(Tooltip.create(Component.translatable("pf.update.checking")));
            PresenceFootsteps.getInstance().getUpdateChecker().checkNow().thenAccept(_ -> redrawUpdateButton(button));
        }).size(25, ROW_HEIGHT).build());

        repositionElements();

        updateDisableState(config.getDisabled());
    }

    @Override
    protected void repositionElements() {
        scrollArea.setMaxHeight(SCROLL_AREA_MIN_HEIGHT);
        layout.arrangeElements();
        int availableExtraHeight = this.height - this.layout.getFooterHeight() - this.scrollArea.getRectangle().bottom();
        this.scrollArea.setMaxHeight(this.scrollArea.getHeight() + availableExtraHeight);
        if (updateButton != null) {
            updateButton.setPosition(this.width - 30, this.height - 25);
        }
    }

    private LinearLayout volumeRow(VolumeOption left, String leftName, VolumeOption right, String rightName) {
        LinearLayout row = LinearLayout.horizontal().spacing(10);

        disableAware.add(row.addChild(new VolumeSlider(SMALL_WIDTH, 0, 100, left.get(),
                formatVolume("menu.pf.volume." + leftName), left::set)
                .tooltip("menu.pf.volume." + leftName + ".tooltip")));

        disableAware.add(row.addChild(new VolumeSlider(SMALL_WIDTH, 0, 100, right.get(),
                formatVolume("menu.pf.volume." + rightName), right::set)
                .tooltip("menu.pf.volume." + rightName + ".tooltip")));

        return row;
    }

    private void updateDisableState(boolean disabled) {
        disableAware.forEach(widget -> widget.active = !disabled);
    }

    private void redrawUpdateButton(Button button) {
        Optional<Versions> versions = PresenceFootsteps.getInstance().getUpdateChecker().getNewer();
        boolean hasUpdate = versions.isPresent();

        button.active = true;
        button.setMessage(hasUpdate ? Component.literal("🙁") : Component.literal("🙂"));
        button.setTooltip(Tooltip.create(versions
                .map(Versions::latest)
                .map(latest -> (Component)Component.translatable("pf.update.updates_available",
                        latest.version().getFriendlyString(),
                        latest.minecraft().getFriendlyString()))
                .orElse(UP_TO_DATE)));
    }

    private Component updateButtonLabel() {
        Optional<Versions> versions = PresenceFootsteps.getInstance().getUpdateChecker().getNewer();
        return Component.literal(versions.isPresent() ? "🙁" : "🙂");
    }

    private static Component targetSelectorLabel(PFConfig config) {
        return Component.translatable("menu.pf.global." + config.getEntitySelector().name().toLowerCase(Locale.ROOT));
    }

    private static Component multiplayerLabel(PFConfig config) {
        return Component.translatable("menu.pf.multiplayer." + config.getEnabledMP());
    }

    private static Component footwearLabel(PFConfig config) {
        return Component.translatable("menu.pf.footwear." + (config.getEnabledFootwear() ? "on" : "off"));
    }

    private static Component exclusiveModeLabel(PFConfig config) {
        return Component.translatable("menu.pf.exclusive_mode." + (config.isExclusiveMode() ? "on" : "off"));
    }

    private static Component formatGlobalVolume(float volume) {
        if (volume <= 0) {
            return VOLUME_MIN;
        }

        return Component.translatable("menu.pf.volume", (int)Math.floor(volume));
    }

    static Function<Float, Component> formatVolume(String key) {
        return volume -> Component.translatable(key, (int)Math.floor(volume));
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    /**
     * A volume slider tracking a value between {@code min} and {@code max}.
     */
    private static final class VolumeSlider extends AbstractSliderButton {
        private final float min;
        private final float max;
        private final Function<Float, Component> formatter;
        private final Consumer<Float> onChange;
        private float volume;

        VolumeSlider(int width, float min, float max, float initial, Function<Float, Component> formatter, Consumer<Float> onChange) {
            super(0, 0, width, DEFAULT_HEIGHT, formatter.apply(initial), (initial - min) / (max - min));
            this.min = min;
            this.max = max;
            this.volume = initial;
            this.formatter = formatter;
            this.onChange = onChange;
        }

        VolumeSlider tooltip(String key) {
            setTooltip(Tooltip.create(Component.translatable(key)));
            return this;
        }

        @Override
        protected void updateMessage() {
            setMessage(formatter.apply(volume));
        }

        @Override
        protected void applyValue() {
            float newValue = (float)(min + (max - min) * this.value);

            if (newValue != volume) {
                volume = newValue;
                onChange.accept(newValue);
            }
        }
    }
}
