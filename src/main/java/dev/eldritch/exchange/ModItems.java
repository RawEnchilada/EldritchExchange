package dev.eldritch.exchange;

import net.minecraft.world.food.FoodProperties;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Eldritch.MODID);

    public static final DeferredItem<CodexItem> CODEX =
            ITEMS.registerItem("codex", props -> new CodexItem(props.stacksTo(1).fireResistant()));

    public static final DeferredItem<TentacleFleshItem> TENTACLE_FLESH =
            ITEMS.registerItem("tentacle_flesh", props -> new TentacleFleshItem(props.food(
                    new FoodProperties.Builder().nutrition(2).saturationModifier(0.1F).alwaysEdible().build())));

    public static final DeferredItem<Item> EYE_OF_AZATHOTH =
            ITEMS.registerItem("eye_of_azathoth", Item::new);

    public static final DeferredItem<BlockItem> MASS =
            ITEMS.registerSimpleBlockItem("mass", ModBlocks.MASS);

    private ModItems() {
    }
}
