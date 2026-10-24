package dev.eldritch.exchange;

import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.item.Item;

public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Eldritch.MODID);

    public static final DeferredItem<CodexItem> CODEX =
            ITEMS.registerItem("codex", props -> new CodexItem(props.stacksTo(1)));

    private ModItems() {
    }
}
