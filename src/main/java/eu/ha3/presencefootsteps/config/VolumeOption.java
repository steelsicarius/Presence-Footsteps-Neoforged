package eu.ha3.presencefootsteps.config;

import net.minecraft.util.Mth;

public class VolumeOption {

    private final Config config;
    private final Setting<Integer> value;

    public VolumeOption(Config config, Setting<Integer> value) {
        this.config = config;
        this.value = value;
    }

    public int get() {
        return Mth.clamp(value.get(), 0, 100);
    }

    public float getPercentage() {
        return get() / 100F;
    }

    public float set(float volume) {
        value.set(volumeScaleToInt(volume));
        config.save();
        return get();
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

    private static int volumeScaleToInt(float volume) {
        return volume > 97 ? 100 : volume < 3 ? 0 : (int)volume;
    }
}
