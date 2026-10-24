package dev.eldritch.exchange.gametest;

import dev.eldritch.exchange.Eldritch;
import dev.eldritch.exchange.ModItems;
import dev.eldritch.exchange.ValueTable;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Eldritch.MODID)
@PrefixGameTestTemplate(false)
public final class EldritchGameTests {

    private EldritchGameTests() {
    }

    @GameTest(template = "empty")
    public static void codexItemIsRegistered(GameTestHelper helper) {
        String id = BuiltInRegistries.ITEM.getKey(ModItems.CODEX.get()).toString();
        if (!id.equals("eldritch:codex")) {
            helper.fail("codex registered as " + id);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void valuesComeFromRealRecipes(GameTestHelper helper) {
        ValueTable.refresh(helper.getLevel().getServer());
        expect(helper, "minecraft:iron_ingot", 64);
        expect(helper, "minecraft:iron_block", 576);
        expect(helper, "minecraft:stick", 1);
        expect(helper, "minecraft:diamond_sword", 513);
        expect(helper, "minecraft:netherite_ingot", 2304);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void uncraftableItemsHaveDefaultValues(GameTestHelper helper) {
        ValueTable.refresh(helper.getLevel().getServer());
        expect(helper, "minecraft:wither_skeleton_skull", 768);
        expect(helper, "minecraft:elytra", 1024);
        expect(helper, "minecraft:totem_of_undying", 512);
        expect(helper, "minecraft:trident", 512);
        expect(helper, "minecraft:enchanted_golden_apple", 512);
        expect(helper, "minecraft:name_tag", 64);
        expect(helper, "minecraft:turtle_scute", 64);
        expectAtLeast(helper, "minecraft:netherite_axe", 3000);
        expectAtLeast(helper, "minecraft:netherite_chestplate", 3000);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyItemHasAValue(GameTestHelper helper) {
        ValueTable.refresh(helper.getLevel().getServer());
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }
            String id = ValueTable.itemId(item);
            if (id.startsWith("eldritch:")) {
                continue;
            }
            if (ValueTable.value(id) < 1) {
                helper.fail(id + " has no value and cannot be burned");
            }
        }
        helper.succeed();
    }

    private static void expectAtLeast(GameTestHelper helper, String itemId, long minimum) {
        long actual = ValueTable.value(itemId);
        if (actual < minimum) {
            helper.fail(itemId + " valued " + actual + ", expected at least " + minimum);
        }
    }

    private static void expect(GameTestHelper helper, String itemId, long expected) {
        long actual = ValueTable.value(itemId);
        if (actual != expected) {
            helper.fail(itemId + " valued " + actual + ", expected " + expected);
        }
    }
}
