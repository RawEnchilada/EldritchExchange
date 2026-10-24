package dev.eldritch.exchange;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Eldritch.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<CodexMenu>> CODEX =
            MENUS.register("codex", () -> new MenuType<>(CodexMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private ModMenus() {
    }
}
