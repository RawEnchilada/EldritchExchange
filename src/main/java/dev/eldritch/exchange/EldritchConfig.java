package dev.eldritch.exchange;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class EldritchConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MASS_TENTACLE_CAP = BUILDER
            .comment("Most tentacles that mass may have out at once, per player area. 0 disables mass tentacles.")
            .defineInRange("massTentacleCap", 5, 0, 50);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private EldritchConfig() {
    }
}
