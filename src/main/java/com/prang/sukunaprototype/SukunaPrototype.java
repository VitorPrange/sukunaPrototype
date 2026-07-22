package com.prang.sukunaprototype;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
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
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(SukunaPrototype.MODID)
public class SukunaPrototype {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "sukunaprototype";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "sukunaprototype" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "sukunaprototype" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "sukunaprototype" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Creates a new Block with the id "examplemod:example_block", combining the namespace and path
    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", BlockBehaviour.Properties.of().mapColor(MapColor.STONE));
    // Creates a new BlockItem with the id "examplemod:example_block", combining the namespace and path
    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK);

    // Creates a new food item with the id "examplemod:example_id", nutrition 1 and saturation 2
    public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem("example_item", new Item.Properties().food(new FoodProperties.Builder()
            .alwaysEdible().nutrition(1).saturationModifier(2f).build()));

    // Creates a creative tab with the id "examplemod:example_tab" for the example item, that is placed after the combat tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.sukunaprototype")) //The language key for the title of your CreativeModeTab
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> EXAMPLE_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(EXAMPLE_ITEM.get()); // Add the example item to the tab. For your own tabs, this method is preferred over the event
            }).build());

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

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public SukunaPrototype(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (SukunaPrototype) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register commands on the NeoForge event bus
        NeoForge.EVENT_BUS.addListener(this::registerCommands);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    public void registerCommands(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();
        var literal = net.minecraft.commands.Commands.literal("sukunaprototype");
        
        // slashMaxRate
        literal.then(net.minecraft.commands.Commands.literal("slashMaxRate")
            .then(net.minecraft.commands.Commands.argument("value", IntegerArgumentType.integer(1, 60))
                .executes(ctx -> {
                    int value = IntegerArgumentType.getInteger(ctx, "value");
                    ctx.getSource().getServer().getGameRules().getRule(SLASH_MAX_RATE).set(value, ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Set slashMaxRate to " + value), true);
                    return value;
                })
            )
            .executes(ctx -> {
                int current = ctx.getSource().getServer().getGameRules().getInt(SLASH_MAX_RATE);
                ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Current slashMaxRate: " + current), false);
                return current;
            })
        );
        
        // slashThickness
        literal.then(net.minecraft.commands.Commands.literal("slashThickness")
            .then(net.minecraft.commands.Commands.argument("value", IntegerArgumentType.integer(0, 3000))
                .executes(ctx -> {
                    int value = IntegerArgumentType.getInteger(ctx, "value");
                    ctx.getSource().getServer().getGameRules().getRule(SLASH_THICKNESS).set(value, ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Set slashThickness to " + value + " (milliBlocks = " + (value / 1000.0) + " blocks)"), true);
                    return value;
                })
            )
            .executes(ctx -> {
                int current = ctx.getSource().getServer().getGameRules().getInt(SLASH_THICKNESS);
                ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Current slashThickness: " + current + " (milliBlocks = " + (current / 1000.0) + " blocks)"), false);
                return current;
            })
        );
        
        // slashOutline
        literal.then(net.minecraft.commands.Commands.literal("slashOutline")
            .then(net.minecraft.commands.Commands.argument("value", IntegerArgumentType.integer(0, 1000))
                .executes(ctx -> {
                    int value = IntegerArgumentType.getInteger(ctx, "value");
                    ctx.getSource().getServer().getGameRules().getRule(SLASH_OUTLINE).set(value, ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Set slashOutline to " + value + " (milliBlocks = " + (value / 1000.0) + " blocks)"), true);
                    return value;
                })
            )
            .executes(ctx -> {
                int current = ctx.getSource().getServer().getGameRules().getInt(SLASH_OUTLINE);
                ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Current slashOutline: " + current + " (milliBlocks = " + (current / 1000.0) + " blocks)"), false);
                return current;
            })
        );
        
        // slashDamage
        literal.then(net.minecraft.commands.Commands.literal("slashDamage")
            .then(net.minecraft.commands.Commands.argument("value", IntegerArgumentType.integer(0, 100000))
                .executes(ctx -> {
                    int value = IntegerArgumentType.getInteger(ctx, "value");
                    ctx.getSource().getServer().getGameRules().getRule(SLASH_DAMAGE).set(value, ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Set slashDamage to " + value + " (milliHearts = " + (value / 1000.0) + " hearts = " + (value / 500.0) + " HP)"), true);
                    return value;
                })
            )
            .executes(ctx -> {
                int current = ctx.getSource().getServer().getGameRules().getInt(SLASH_DAMAGE);
                ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Current slashDamage: " + current + " (milliHearts = " + (current / 1000.0) + " hearts = " + (current / 500.0) + " HP)"), false);
                return current;
            })
        );
        
        dispatcher.register(literal);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }

        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());

        Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(EXAMPLE_BLOCK_ITEM);
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }
}
