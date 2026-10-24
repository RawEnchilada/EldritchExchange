package dev.eldritch.exchange;

import dev.eldritch.exchange.core.ValueCalc;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import org.slf4j.Logger;

public final class ValueTable {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Hand-written values for vanilla items. Everything that can be reached from a
     * recipe is normally derived by {@link ValueCalc}, but many items are drops,
     * loot, trades or structures and have no recipe at all. Those need a value here
     * or they could never be burned or withdrawn.
     *
     * <p>The scale is anchored on the original seeds: diamond 256, netherite scrap
     * 512, nether star 1024. Crafted items are deliberately not listed so the
     * recipe pass keeps valuing them.
     */
    private static final Map<String, Long> DEFAULT_VALUES = defaultValues();

    private static Map<String, Long> values = Map.of();
    private static List<String> index = List.of();
    private static Object cachedManager;
    private static int cachedSize = -1;
    private static long cachedConfigStamp = -1L;
    private static final Map<Class<?>, Optional<Method>> ROLLABLE_RESULTS = new HashMap<>();

    private ValueTable() {
    }

    public static synchronized void refresh(MinecraftServer server) {
        Collection<RecipeHolder<?>> all = server.getRecipeManager().getRecipes();
        long configStamp = ValueConfig.stamp();
        if (configStamp == cachedConfigStamp
                && server.getRecipeManager() == cachedManager
                && all.size() == cachedSize) {
            return;
        }
        cachedManager = server.getRecipeManager();
        cachedSize = all.size();

        long started = System.nanoTime();
        try {
            rebuild(server, all);
            LOGGER.info("Value table rebuilt: {} items from {} recipes in {} ms", values.size(), all.size(),
                    (System.nanoTime() - started) / 1_000_000L);
        } catch (RuntimeException e) {
            // A pricing problem in one modded item must never take the server down.
            LOGGER.error("Could not rebuild the value table, keeping the previous values", e);
            if (values.isEmpty()) {
                values = withFallbacks(Map.of());
                index = sortedIndex(values);
            }
        } finally {
            cachedConfigStamp = ValueConfig.stamp();
        }
    }

    private static void rebuild(MinecraftServer server, Collection<RecipeHolder<?>> all) {
        HolderLookup.Provider access = server.registryAccess();
        List<ValueCalc.Recipe> recipes = new ArrayList<>();
        for (RecipeHolder<?> holder : all) {
            Recipe<?> recipe = holder.value();
            ItemStack output = recipe.getResultItem(access);
            if (output.isEmpty() || uncertainOutput(recipe)) {
                continue;
            }
            List<List<String>> slots = new ArrayList<>();
            boolean usable = true;
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient.isEmpty()) {
                    continue;
                }
                List<String> options = new ArrayList<>();
                for (ItemStack option : ingredient.getItems()) {
                    if (!option.isEmpty()) {
                        options.add(itemId(option.getItem()));
                    }
                }
                if (options.isEmpty()) {
                    usable = false;
                    break;
                }
                slots.add(options);
            }
            // Smithing recipes expose no ingredients through getIngredients(), so the
            // three slots are recovered by matching every item against the recipe.
            if (usable && slots.isEmpty() && recipe instanceof SmithingTransformRecipe smithing) {
                slots.add(matchingItems(smithing::isTemplateIngredient));
                slots.add(matchingItems(smithing::isBaseIngredient));
                slots.add(matchingItems(smithing::isAdditionIngredient));
                usable = slots.stream().noneMatch(List::isEmpty);
            }
            if (usable && !slots.isEmpty()) {
                recipes.add(new ValueCalc.Recipe(itemId(output.getItem()), output.getCount(), slots));
            }
        }

        ValueConfig.Data config = ValueConfig.load(LOGGER);
        Map<String, Long> overrides = ValueConfig.overrides(config);

        Map<String, Long> automatic = withFallbacks(ValueCalc.compute(seeds(), recipes));
        Map<String, Long> known;
        if (overrides.isEmpty()) {
            known = automatic;
        } else {
            Map<String, Long> seeded = new HashMap<>(seeds());
            seeded.putAll(overrides);
            known = withFallbacks(ValueCalc.compute(seeded, recipes));
        }
        values = known;
        index = sortedIndex(values);
        ValueConfig.save(values, automatic, LOGGER);
    }

    /**
     * Modded processing recipes often expose only the first output and hide its
     * probability. Treating a 40% chance byproduct as guaranteed would let a cheap
     * stone turn into an expensive metal, so such recipes are left out entirely.
     */
    private static boolean uncertainOutput(Recipe<?> recipe) {
        Class<?> type = recipe.getClass();
        Optional<Method> method = ROLLABLE_RESULTS.computeIfAbsent(type, key -> {
            try {
                return Optional.of(key.getMethod("getRollableResults"));
            } catch (NoSuchMethodException | SecurityException e) {
                return Optional.empty();
            }
        });
        if (method.isEmpty()) {
            return false;
        }
        try {
            Object results = method.get().invoke(recipe);
            if (!(results instanceof List<?> list) || list.isEmpty()) {
                return false;
            }
            Object first = list.get(0);
            Object chance = first.getClass().getMethod("getChance").invoke(first);
            return chance instanceof Number number && number.floatValue() < 0.999F;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static List<String> sortedIndex(Map<String, Long> table) {
        return table.entrySet().stream()
                .sorted(Comparator.comparingLong(Map.Entry<String, Long>::getValue).thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .toList();
    }

    public static long value(String itemId) {
        return values.getOrDefault(itemId, 0L);
    }

    public static List<String> index() {
        return index;
    }

    public static String itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private static List<String> matchingItems(java.util.function.Predicate<ItemStack> predicate) {
        List<String> ids = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            if (predicate.test(stack)) {
                ids.add(itemId(item));
            }
        }
        return ids;
    }

    /**
     * Gives every registered item a value, so nothing is silently unburnable. Items
     * the recipe pass and the usage prediction could not value fall back to their
     * rarity. The mod's own items stay worthless on purpose.
     */
    private static Map<String, Long> withFallbacks(Map<String, Long> known) {
        Map<String, Long> complete = new HashMap<>(known);
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }
            String id = itemId(item);
            if (complete.containsKey(id) || id.startsWith("eldritch:")) {
                continue;
            }
            complete.put(id, fallbackValue(item));
        }
        return complete;
    }

    private static long fallbackValue(Item item) {
        Rarity rarity = item.getDefaultInstance().getRarity();
        // Rarity is a NeoForge extensible enum: mods can add constants, so an
        // exhaustive switch would throw MatchException on a modded pack.
        if (rarity == Rarity.UNCOMMON) {
            return 8L;
        }
        if (rarity == Rarity.RARE) {
            return 32L;
        }
        if (rarity == Rarity.EPIC) {
            return 128L;
        }
        return 1L;
    }

    private static Map<String, Long> seeds() {
        Map<String, Long> resolved = new HashMap<>();
        // Tags first, explicit ids second, so an explicit value always wins.
        for (Map.Entry<String, Long> entry : DEFAULT_VALUES.entrySet()) {
            if (!entry.getKey().startsWith("#")) {
                continue;
            }
            TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(entry.getKey().substring(1)));
            for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                resolved.put(itemId(holder.value()), entry.getValue());
            }
        }
        for (Map.Entry<String, Long> entry : DEFAULT_VALUES.entrySet()) {
            if (entry.getKey().startsWith("#")) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.getKey()));
            if (item != null && item != Items.AIR) {
                resolved.put(entry.getKey(), entry.getValue());
            }
        }
        return resolved;
    }

    private static Map<String, Long> defaultValues() {
        Map<String, Long> values = new LinkedHashMap<>();

        // Common ground, plants and mob drops.
        add(values, 1,
                "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:rooted_dirt", "minecraft:mud",
                "minecraft:cobblestone", "minecraft:cobbled_deepslate", "minecraft:deepslate", "minecraft:stone",
                "minecraft:andesite", "minecraft:diorite", "minecraft:granite", "minecraft:tuff",
                "minecraft:sand", "minecraft:red_sand", "minecraft:gravel", "minecraft:clay_ball",
                "minecraft:netherrack", "minecraft:blackstone", "minecraft:basalt", "minecraft:end_stone",
                "minecraft:snowball", "minecraft:egg", "minecraft:wheat_seeds", "minecraft:beetroot_seeds",
                "minecraft:melon_seeds", "minecraft:pumpkin_seeds", "minecraft:moss_block", "minecraft:moss_carpet",
                "minecraft:vine", "minecraft:glow_lichen", "minecraft:seagrass", "minecraft:kelp",
                "minecraft:small_dripleaf", "minecraft:hanging_roots", "minecraft:rotten_flesh",
                "minecraft:string", "minecraft:flint", "minecraft:bone", "minecraft:feather",
                "minecraft:leather", "minecraft:rabbit_hide");
        add(values, 2,
                "minecraft:bamboo", "minecraft:sugar_cane", "minecraft:melon_slice");
        add(values, 4,
                "#minecraft:logs", "minecraft:redstone", "minecraft:gunpowder", "minecraft:spider_eye",
                "minecraft:soul_sand", "minecraft:soul_soil", "minecraft:calcite", "minecraft:smooth_basalt",
                "minecraft:cocoa_beans", "minecraft:sweet_berries", "minecraft:glow_berries",
                "minecraft:popped_chorus_fruit", "minecraft:carrot", "minecraft:potato", "minecraft:beetroot",
                "minecraft:podzol", "minecraft:mycelium", "minecraft:spore_blossom", "minecraft:big_dripleaf");
        add(values, 8,
                "minecraft:slime_ball", "minecraft:phantom_membrane", "minecraft:prismarine_shard",
                "minecraft:amethyst_shard", "minecraft:ink_sac", "minecraft:nether_wart", "minecraft:rabbit_foot",
                "minecraft:armadillo_scute", "minecraft:small_amethyst_bud", "minecraft:medium_amethyst_bud",
                "minecraft:sculk_vein", "minecraft:apple", "minecraft:chorus_fruit", "minecraft:wheat",
                "minecraft:beef", "minecraft:porkchop", "minecraft:chicken", "minecraft:mutton",
                "minecraft:rabbit", "minecraft:cod", "minecraft:salmon", "minecraft:tropical_fish",
                "minecraft:pufferfish", "minecraft:pointed_dripstone");
        add(values, 16,
                "minecraft:coal", "minecraft:lapis_lazuli", "minecraft:quartz", "minecraft:ender_pearl",
                "minecraft:glowstone_dust", "minecraft:glow_ink_sac", "minecraft:prismarine_crystals",
                "minecraft:honeycomb", "minecraft:honey_bottle", "minecraft:crying_obsidian",
                "minecraft:large_amethyst_bud", "minecraft:sculk");
        add(values, 32,
                "minecraft:raw_copper", "minecraft:blaze_rod", "minecraft:breeze_rod", "minecraft:copper_ore",
                "minecraft:obsidian", "minecraft:amethyst_cluster", "minecraft:disc_fragment_5",
                "minecraft:poisonous_potato", "#minecraft:decorated_pot_sherds");
        add(values, 64,
                "minecraft:raw_iron", "minecraft:raw_gold", "minecraft:iron_ore", "minecraft:gold_ore",
                "minecraft:ghast_tear", "minecraft:nautilus_shell", "minecraft:turtle_scute", "minecraft:name_tag",
                "minecraft:experience_bottle", "minecraft:glowstone", "minecraft:sculk_catalyst",
                "minecraft:sculk_shrieker", "minecraft:suspicious_stew", "minecraft:pitcher_pod",
                "minecraft:torchflower_seeds");
        add(values, 128,
                "minecraft:shulker_shell", "minecraft:saddle", "minecraft:skeleton_skull", "minecraft:zombie_head",
                "minecraft:creeper_head", "minecraft:piglin_head", "minecraft:goat_horn",
                "minecraft:sculk_sensor", "minecraft:calibrated_sculk_sensor", "minecraft:wither_rose",
                "minecraft:trial_key",
                "#minecraft:trim_templates",
                "minecraft:music_disc_13", "minecraft:music_disc_cat", "minecraft:music_disc_blocks",
                "minecraft:music_disc_chirp", "minecraft:music_disc_far", "minecraft:music_disc_mall",
                "minecraft:music_disc_mellohi", "minecraft:music_disc_stal", "minecraft:music_disc_strad",
                "minecraft:music_disc_ward", "minecraft:music_disc_11", "minecraft:music_disc_wait",
                "minecraft:music_disc_pigstep", "minecraft:music_disc_otherside", "minecraft:music_disc_5",
                "minecraft:music_disc_relic");
        add(values, 256,
                "minecraft:diamond", "minecraft:emerald", "minecraft:echo_shard", "minecraft:heart_of_the_sea",
                "minecraft:sponge", "minecraft:wet_sponge", "minecraft:enchanted_book",
                "minecraft:ominous_bottle", "minecraft:ominous_trial_key", "minecraft:chainmail_helmet",
                "minecraft:chainmail_chestplate", "minecraft:chainmail_leggings", "minecraft:chainmail_boots",
                "minecraft:chorus_flower");
        add(values, 512,
                "minecraft:netherite_scrap", "minecraft:ancient_debris", "minecraft:dragon_breath",
                "minecraft:totem_of_undying", "minecraft:trident", "minecraft:enchanted_golden_apple",
                "minecraft:heavy_core", "minecraft:budding_amethyst", "minecraft:sniffer_egg",
                "minecraft:dragon_head", "minecraft:iron_horse_armor", "minecraft:golden_horse_armor",
                "minecraft:netherite_upgrade_smithing_template");
        add(values, 768, "minecraft:wither_skeleton_skull");
        add(values, 1024, "minecraft:nether_star", "minecraft:elytra", "minecraft:diamond_horse_armor");
        add(values, 4096, "minecraft:dragon_egg");

        return Map.copyOf(values);
    }

    private static void add(Map<String, Long> values, long value, String... names) {
        for (String name : names) {
            values.put(name, value);
        }
    }
}
