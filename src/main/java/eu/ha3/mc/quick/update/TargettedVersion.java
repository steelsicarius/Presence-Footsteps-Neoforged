package eu.ha3.mc.quick.update;

import java.util.Optional;

import com.google.gson.JsonObject;

import net.minecraft.SharedConstants;
import net.minecraft.util.GsonHelper;
import net.neoforged.fml.ModList;

public record TargettedVersion (
        Version minecraft,
        Version version) {

    public TargettedVersion(JsonObject json) throws VersionParsingException {
        this(
                Version.parse(GsonHelper.getAsString(json, "minecraft")),
                Version.parse(GsonHelper.getAsString(json, "version")));
    }

    public TargettedVersion(String modid) {
        this(getVersion("minecraft"), getVersion(modid));
    }

    public static Version getVersion(String modid) {
        return ModList.get().getModContainerById(modid)
                .map(container -> container.getModInfo().getVersion().toString())
                .or(() -> "minecraft".equals(modid) ? Optional.of(SharedConstants.getCurrentVersion().id()) : Optional.empty())
                .map(Version::new)
                .orElseThrow(() -> new IllegalArgumentException("Unknown mod id: " + modid));
    }
}
