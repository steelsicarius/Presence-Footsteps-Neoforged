package eu.ha3.presencefootsteps.sound.generator;

import java.util.Optional;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

/**
 * Compat shim for Mine Little Pony.
 *
 * The Mine Little Pony API is not available at compile time in the NeoForge
 * build, so the locomotion lookup is done entirely through reflection once
 * the mod is detected at runtime.
 */
public class MineLP {
    private static boolean checkCompleted = false;
    private static boolean hasMineLP;

    public static boolean hasPonies() {
        if (!checkCompleted) {
            checkCompleted = true;
            hasMineLP = ModList.get().isLoaded("minelp");
        }

        return hasMineLP;
    }

    public static Locomotion getLocomotion(Entity entity, Locomotion fallback) {
        if (!hasPonies() || entity == null) {
            return fallback;
        }

        try {
            Class<?> ponyClass = Class.forName("com.minelittlepony.api.pony.Pony");

            Object manager = ponyClass.getMethod("getManager").invoke(null);
            @SuppressWarnings("unchecked")
            Optional<Object> pony = (Optional<Object>)manager.getClass()
                    .getMethod("getPony", Entity.class)
                    .invoke(manager, entity);

            if (pony.isEmpty()) {
                return fallback;
            }

            Object race = pony.get().getClass().getMethod("race").invoke(pony.get());

            if ((boolean)race.getClass().getMethod("isHuman").invoke(race)) {
                return fallback;
            }

            return (boolean)race.getClass().getMethod("hasWings").invoke(race)
                    ? Locomotion.FLYING
                    : Locomotion.QUADRUPED;
        } catch (Throwable t) {
            hasMineLP = false;
            return fallback;
        }
    }

    public static Locomotion getLocomotion(Player ply) {
        return getLocomotion(ply, Locomotion.BIPED);
    }
}
