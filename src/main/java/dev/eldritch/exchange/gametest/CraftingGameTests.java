package dev.eldritch.exchange.gametest;

import dev.eldritch.exchange.Eldritch;
import dev.eldritch.exchange.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Eldritch.MODID)
@PrefixGameTestTemplate(false)
public final class CraftingGameTests {

    private CraftingGameTests() {
    }

    @GameTest(template = "empty")
    public static void eyeRecipeMatchesEightFleshAroundEnderPearl(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<ItemStack> grid = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            grid.add(i == 4 ? new ItemStack(Items.ENDER_PEARL) : new ItemStack(ModItems.TENTACLE_FLESH.get()));
        }
        CraftingInput input = CraftingInput.of(3, 3, grid);
        var match = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        if (match.isEmpty()) {
            helper.fail("no crafting recipe matches 8 tentacle flesh around an ender pearl");
            return;
        }
        ItemStack result = match.get().value().assemble(input, level.registryAccess());
        if (!result.is(ModItems.EYE_OF_AZATHOTH.get())) {
            helper.fail("crafting produced " + result + " instead of the Eye of Azathoth");
            return;
        }
        helper.succeed();
    }
}
