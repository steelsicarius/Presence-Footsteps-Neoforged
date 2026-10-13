package eu.ha3.presencefootsteps.world;

import java.util.Set;

public interface Substrates {
    String DEFAULT = "";
    String CARPET = "carpet";
    String WET = "wet";
    String FENCE = "bigger";
    String FOLIAGE = "foliage";
    String MESSY = "messy";

    Set<String> SUPPLIMENTART_SUBSTRATES = Set.of(WET, FOLIAGE, MESSY);

    static boolean isDefault(String substrate) {
        return Substrates.DEFAULT.equals(substrate);
    }

    static boolean isSupplimentary(String substrate) {
        return SUPPLIMENTART_SUBSTRATES.contains(substrate);
    }
}
