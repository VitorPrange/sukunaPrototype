package com.prang.sukunaprototype;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.GameRules;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(SukunaPrototype.MODID)
public class SukunaPrototype {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "sukunaprototype";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // Max slash rate (slashes/sec) while holding X. Raised via /gamerule sukunaprototype:slashMaxRate.
    // Default 7. Clamped to 1-60 in code. The hold ramp never exceeds this cap.
    public static final GameRules.Key<GameRules.IntegerValue> SLASH_MAX_RATE =
            GameRules.register("sukunaprototype:slashMaxRate", GameRules.Category.MISC, GameRules.IntegerValue.create(7));

    // Real-time slash thickness in MILLIBLOCKS (×1000). /gamerule sukunaprototype:slashThickness 80 -> 0.08 blocks,
    // thinner instantly, even on already-spawned slashes (render() reads this every frame). Lower = thinner.
    public static final GameRules.Key<GameRules.IntegerValue> SLASH_THICKNESS =
            GameRules.register("sukunaprototype:slashThickness", GameRules.Category.MISC, GameRules.IntegerValue.create(160));

    // Real-time outline rim thickness in MILLIBLOCKS (×1000). /gamerule sukunaprototype:slashOutline 120 -> 0.12 blocks.
    public static final GameRules.Key<GameRules.IntegerValue> SLASH_OUTLINE =
            GameRules.register("sukunaprototype:slashOutline", GameRules.Category.MISC, GameRules.IntegerValue.create(120));

    // Real-time slash damage in MILLIHEARTS (×1000). /gamerule sukunaprototype:slashDamage 12000 -> 12 hearts (24 HP).
    // Applied once per slash at spawn (age==1) via AABB sweep. Millihearts for gamerule integer granularity.
    public static final GameRules.Key<GameRules.IntegerValue> SLASH_DAMAGE =
            GameRules.register("sukunaprototype:slashDamage", GameRules.Category.MISC, GameRules.IntegerValue.create(6000)); // 6 hearts default

    // Whether slashes should ignore entity invulnerability frames. Default false (respects i-frames).
    // When true, slashes reset invulnerableTime to 0 before applying damage.
    public static final GameRules.Key<GameRules.BooleanValue> SLASH_IGNORE_INVULNERABLE =
            GameRules.register("sukunaprototype:slashIgnoreInvulnerable", GameRules.Category.MISC, GameRules.BooleanValue.create(false));

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public SukunaPrototype(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (SukunaPrototype) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register commands on the NeoForge event bus
        NeoForge.EVENT_BUS.addListener(this::registerCommands);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
    public void registerCommands(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();
        var literal = net.minecraft.commands.Commands.literal("sukunaprototype");
        
        // Register all gamerule commands using helper method
        registerGameRuleCommand(literal, "slashMaxRate", SLASH_MAX_RATE, 1, 60, null);
        registerGameRuleCommand(literal, "slashThickness", SLASH_THICKNESS, 0, 3000, "milliBlocks");
        registerGameRuleCommand(literal, "slashOutline", SLASH_OUTLINE, 0, 1000, "milliBlocks");
        registerGameRuleCommand(literal, "slashDamage", SLASH_DAMAGE, 0, 100000, "milliHearts");
        registerGameRuleCommand(literal, "slashIgnoreInvulnerable", SLASH_IGNORE_INVULNERABLE);
        
        dispatcher.register(literal);
    }

    private void registerGameRuleCommand(
            net.minecraft.commands.builders.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> parent,
            String name,
            GameRules.Key<GameRules.IntegerValue> gameruleKey,
            int minValue,
            int maxValue,
            String unit) {
        parent.then(net.minecraft.commands.Commands.literal(name)
            .then(net.minecraft.commands.Commands.argument("value", IntegerArgumentType.integer(minValue, maxValue))
                .executes(ctx -> {
                    int value = IntegerArgumentType.getInteger(ctx, "value");
                    ctx.getSource().getServer().getGameRules().getRule(gameruleKey).set(value, ctx.getSource().getServer());
                    String msg = unit != null ? String.format("Set %s to %d (%s = %.3f)", name, value, unit, value / 1000.0) : String.format("Set %s to %d", name, value);
                    ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(msg), true);
                    return value;
                })
            )
            .executes(ctx -> {
                int current = ctx.getSource().getServer().getGameRules().getInt(gameruleKey);
                String msg = unit != null ? String.format("Current %s: %d (%s = %.3f)", name, current, unit, current / 1000.0) : String.format("Current %s: %d", name, current);
                ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(msg), false);
                return current;
            })
    }

    // Overloaded helper for boolean GameRules
    private void registerGameRuleCommand(
            net.minecraft.commands.builders.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> parent,
            String name,
            GameRules.Key<GameRules.BooleanValue> gameruleKey) {
        parent.then(net.minecraft.commands.Commands.literal(name)
            .then(net.minecraft.commands.Commands.argument("value", BoolArgumentType.bool())
                .executes(ctx -> {
                    boolean value = BoolArgumentType.getBool(ctx, "value");
                    ctx.getSource().getServer().getGameRules().getRule(gameruleKey).set(value, ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(String.format("Set %s to %s", name, value)), true);
                    return value ? 1 : 0;
                })
            )
            .executes(ctx -> {
                boolean current = ctx.getSource().getServer().getGameRules().getBoolean(gameruleKey);
                ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(String.format("Current %s: %s", name, current)), false);
                return current ? 1 : 0;
            })
        );
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");
        LOGGER.info("Slash VFX mod common setup complete");
    }
    }
    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }
}
