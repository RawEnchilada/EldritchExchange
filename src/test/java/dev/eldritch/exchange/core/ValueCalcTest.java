package dev.eldritch.exchange.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ValueCalcTest {

    @Test
    void seedsKeepTheirValues() {
        Map<String, Long> values = ValueCalc.compute(Map.of("minecraft:diamond", 256L), List.of());
        assertEquals(256L, values.get("minecraft:diamond"));
    }

    @Test
    void smeltingPassesValueThrough() {
        var recipes = List.of(new ValueCalc.Recipe("minecraft:iron_ingot", 1, List.of(List.of("minecraft:raw_iron"))));
        Map<String, Long> values = ValueCalc.compute(Map.of("minecraft:raw_iron", 64L), recipes);
        assertEquals(64L, values.get("minecraft:iron_ingot"));
    }

    @Test
    void craftingDividesByOutputCount() {
        var recipes = List.of(
                new ValueCalc.Recipe("minecraft:oak_planks", 4, List.of(List.of("minecraft:oak_log"))));
        Map<String, Long> values = ValueCalc.compute(Map.of("minecraft:oak_log", 4L), recipes);
        assertEquals(1L, values.get("minecraft:oak_planks"));
    }

    @Test
    void diamondSwordCostsTwoDiamondsPlusStick() {
        var recipes = List.of(
                new ValueCalc.Recipe("minecraft:stick", 4, List.of(List.of("minecraft:oak_planks"), List.of("minecraft:oak_planks"))),
                new ValueCalc.Recipe("minecraft:diamond_sword", 1, List.of(
                        List.of("minecraft:diamond"), List.of("minecraft:diamond"), List.of("minecraft:stick"))));
        Map<String, Long> values = ValueCalc.compute(
                Map.of("minecraft:diamond", 256L, "minecraft:oak_planks", 1L), recipes);
        assertEquals(513L, values.get("minecraft:diamond_sword"));
    }

    @Test
    void cheapestAlternativeWins() {
        var recipes = List.of(
                new ValueCalc.Recipe("minecraft:thing", 1, List.of(List.of("minecraft:expensive", "minecraft:cheap"))));
        Map<String, Long> values = ValueCalc.compute(
                Map.of("minecraft:expensive", 100L, "minecraft:cheap", 3L), recipes);
        assertEquals(3L, values.get("minecraft:thing"));
    }

    @Test
    void unreachableItemsHaveNoValue() {
        var recipes = List.of(
                new ValueCalc.Recipe("minecraft:orphan", 1, List.of(List.of("minecraft:nothing_known"))));
        Map<String, Long> values = ValueCalc.compute(Map.of("minecraft:diamond", 256L), recipes);
        assertNull(values.get("minecraft:orphan"));
    }

    @Test
    void cyclesDoNotLoopForever() {
        var recipes = List.of(
                new ValueCalc.Recipe("a:x", 1, List.of(List.of("a:y"))),
                new ValueCalc.Recipe("a:y", 1, List.of(List.of("a:x"))));
        Map<String, Long> values = ValueCalc.compute(Map.of("a:seed", 8L), recipes);
        assertFalse(values.containsKey("a:x"));
    }

    @Test
    void roundingNeverGivesFreeItems() {
        var recipes = List.of(
                new ValueCalc.Recipe("a:tiny", 9, List.of(List.of("a:seed"))));
        Map<String, Long> values = ValueCalc.compute(Map.of("a:seed", 1L), recipes);
        assertEquals(1L, values.get("a:tiny"));
    }

    @Test
    void uncraftableIngredientGetsTheLeftoverValue() {
        var recipes = List.of(
                new ValueCalc.Recipe("a:sword", 1, List.of(
                        List.of("a:gem"), List.of("a:ingot"))));
        Map<String, Long> values = ValueCalc.compute(
                Map.of("a:sword", 100L, "a:ingot", 10L), recipes);
        assertEquals(90L, values.get("a:gem"));
    }

    @Test
    void leftoverIsSplitBetweenSeveralUnknownIngredients() {
        var recipes = List.of(
                new ValueCalc.Recipe("a:sword", 1, List.of(
                        List.of("a:gem"), List.of("a:dust"), List.of("a:ingot"))));
        Map<String, Long> values = ValueCalc.compute(
                Map.of("a:sword", 100L, "a:ingot", 10L), recipes);
        assertEquals(45L, values.get("a:gem"));
        assertEquals(45L, values.get("a:dust"));
    }

    @Test
    void usagePredictionFallsBackToOtherIngredients() {
        var recipes = List.of(
                new ValueCalc.Recipe("a:blade", 1, List.of(
                        List.of("a:gem"), List.of("a:ingot"))));
        Map<String, Long> values = ValueCalc.compute(Map.of("a:ingot", 64L), recipes);
        assertEquals(64L, values.get("a:gem"));
        assertEquals(128L, values.get("a:blade"));
    }

    @Test
    void usagePredictionNeverOverwritesKnownValues() {
        var recipes = List.of(
                new ValueCalc.Recipe("a:sword", 1, List.of(
                        List.of("a:gem"), List.of("a:ingot"))));
        Map<String, Long> values = ValueCalc.compute(
                Map.of("a:sword", 100L, "a:ingot", 10L, "a:gem", 5L), recipes);
        assertEquals(5L, values.get("a:gem"));
    }

    @Test
    void usagePredictionUsesTheCheapestAlternative() {
        var recipes = List.of(
                new ValueCalc.Recipe("a:sword", 1, List.of(
                        List.of("a:gem"), List.of("a:gold", "a:iron"))));
        Map<String, Long> values = ValueCalc.compute(
                Map.of("a:sword", 100L, "a:iron", 10L, "a:gold", 80L), recipes);
        assertEquals(90L, values.get("a:gem"));
    }

    @Test
    void inferredValuesCannotUndercutKnownRecipes() {
        var recipes = List.of(
                // One ore makes five crushed material, which smelts into one ingot.
                new ValueCalc.Recipe("a:crushed", 5, List.of(List.of("a:ore"))),
                new ValueCalc.Recipe("a:ingot", 1, List.of(List.of("a:crushed"))),
                // The deepslate variant has no recipe, so its value is inferred from
                // smelting. Crushing it must not then multiply that guess sevenfold.
                new ValueCalc.Recipe("a:ingot", 1, List.of(List.of("a:deepslate_ore"))),
                new ValueCalc.Recipe("a:crushed", 7, List.of(List.of("a:deepslate_ore"))));
        Map<String, Long> values = ValueCalc.compute(Map.of("a:ore", 32L), recipes);
        assertEquals(7L, values.get("a:crushed"));
        assertEquals(7L, values.get("a:ingot"));
    }

    @Test
    void usagePredictionLeavesCircularRecipesAlone() {
        var recipes = List.of(
                new ValueCalc.Recipe("a:x", 1, List.of(List.of("a:y"))),
                new ValueCalc.Recipe("a:y", 1, List.of(List.of("a:x"))));
        Map<String, Long> values = ValueCalc.compute(Map.of("a:seed", 8L), recipes);
        assertFalse(values.containsKey("a:x"));
        assertFalse(values.containsKey("a:y"));
    }
}
