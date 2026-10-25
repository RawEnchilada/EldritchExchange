package dev.eldritch.exchange;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Eldritch.MODID);

    /** Every item and block the mod adds, except the Void Campfire, which only comes from converting a soul campfire. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + Eldritch.MODID))
                    .icon(() -> new ItemStack(ModItems.EYE_OF_AZATHOTH.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.CODEX.get());
                        output.accept(ModItems.TENTACLE_FLESH.get());
                        output.accept(ModItems.EYE_OF_AZATHOTH.get());
                        output.accept(ModItems.MASS.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
