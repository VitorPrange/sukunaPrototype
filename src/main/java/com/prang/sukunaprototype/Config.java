package com.prang.sukunaprototype;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("Whether to log the dirt block on common setup")
            .define("logDirtBlock", true);

    public static final ModConfigSpec.IntValue MAGIC_NUMBER = BUILDER
            .comment("A magic number")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
            .comment("What you want the introduction message to be for the magic number")
            .define("magicNumberIntroduction", "The magic number is... ");

    // a list of strings that are treated as resource locations for items
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
            .comment("A list of items to log on common setup.")
            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), () -> "", Config::validateItemName);

    // --- Slash VFX tuning ---
    public static final ModConfigSpec.DoubleValue SLASH_LENGTH = BUILDER
            .comment("Slash length in blocks (world size). Bigger = longer slash.")
            .defineInRange("slashLength", 12.0, 1.0, 40.0);

    public static final ModConfigSpec.DoubleValue SLASH_THICKNESS = BUILDER
            .comment("Slash thickness in blocks (world size). Smaller = thinner slash.")
            .defineInRange("slashThickness", 0.16, 0.02, 3.0);

    // Slash outline rim thickness (blocks). 0 = none. Drawn additive + depth-tested
    // so it glows in open air but vanishes where the slash enters a block/mob.
    public static final ModConfigSpec.DoubleValue SLASH_OUTLINE = BUILDER
            .comment("Slash outline rim thickness in blocks (0 = none). Vibrant additive edge.")
            .defineInRange("slashOutline", 0.12, 0.0, 1.0);

    // How bright/punchy the outline glow is. Additive, so >1 ramps the rim
    // up to a hard glowing edge. 1.0 = strong, 2.0+ = blown-out neon.
    public static final ModConfigSpec.DoubleValue SLASH_OUTLINE_VIBRANCY = BUILDER
            .comment("Outline glow intensity (additive). Higher = more vibrant neon rim.")
            .defineInRange("slashOutlineVibrancy", 1.8, 0.2, 3.0);

    // Per-spawn length randomness: final length = configLength * (1 ± jitter).
    // 0.0 = always exactly config length; 0.5 = anywhere from half to 1.5x.
    public static final ModConfigSpec.DoubleValue SLASH_LENGTH_JITTER = BUILDER
            .comment("Random length variation per slash (0 = none, 0.5 = half to 1.5x). Thickness is unaffected.")
            .defineInRange("slashLengthJitter", 0.35, 0.0, 1.0);

    static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }
}
