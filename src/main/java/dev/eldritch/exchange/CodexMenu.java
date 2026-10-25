package dev.eldritch.exchange;

import dev.eldritch.exchange.core.Demand;
import dev.eldritch.exchange.core.Economy;
import dev.eldritch.exchange.core.LedgerData;
import dev.eldritch.exchange.core.Risk;
import dev.eldritch.exchange.Thalggor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

public final class CodexMenu extends AbstractContainerMenu {

    public static final int COLUMNS = 9;
    public static final int ROWS = 6;
    public static final int PER_PAGE = COLUMNS * ROWS;
    public static final int GRID_Y = 54;

    static final int DATA_ESSENCE = 0;
    static final int DATA_MADNESS = 1;
    static final int DATA_PAGE = 2;
    static final int DATA_PAGES = 3;
    static final int DATA_DEMAND_ITEM = 4; // registry id, -1 when nothing is demanded
    static final int DATA_DEMAND_COUNT = 5;
    static final int DATA_SEALED_LOW = 6;  // bit i set = grid slot i is sealed
    static final int DATA_SEALED_HIGH = 7; // bit i set = grid slot 27 + i is sealed
    static final int DATA_DEMAND_KIND = 8; // Demand.Kind ordinal + 1, 0 when nothing is active
    static final int DATA_FAVOUR = 9;
    static final int DATA_SORT = 10;
    static final int SEALED_BITS = 27;

    static final int BUTTON_PREVIOUS = 0;
    static final int BUTTON_NEXT = 1;

    private static final java.util.Map<String, String> NAMES = new java.util.HashMap<>();

    private final SimpleContainer grid = new SimpleContainer(PER_PAGE);
    private final SimpleContainerData info = new SimpleContainerData(11);
    private final ServerPlayer owner;
    private List<String> shown = List.of();
    private String query = "";


    public CodexMenu(int containerId, Inventory inventory) {
        super(ModMenus.CODEX.get(), containerId);
        owner = inventory.player instanceof ServerPlayer player ? player : null;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                addSlot(new GridSlot(grid, row * COLUMNS + col, 8 + col * 18, GRID_Y + row * 18));
            }
        }
        addDataSlots(info);
        if (owner != null) {
            refresh(owner);
        }
    }

    public Item demandItem() {
        int id = info.get(DATA_DEMAND_ITEM);
        return id < 0 ? null : BuiltInRegistries.ITEM.byId(id);
    }

    public int demandCount() {
        return info.get(DATA_DEMAND_COUNT);
    }

    public int favour() {
        return info.get(DATA_FAVOUR);
    }

    public Demand.Kind demandKind() {
        int kind = info.get(DATA_DEMAND_KIND);
        return kind <= 0 ? null : Demand.Kind.values()[kind - 1];
    }

    public boolean isSealedSlot(int slot) {
        int word = slot < SEALED_BITS ? info.get(DATA_SEALED_LOW) : info.get(DATA_SEALED_HIGH);
        return ((word >> (slot % SEALED_BITS)) & 1) != 0;
    }

    @Override
    public void broadcastChanges() {
        if (owner != null && owner.server.getTickCount() % 20 == 0) {
            refreshDemand(owner);
        }
        super.broadcastChanges();
    }

    public int essence() {
        return info.get(DATA_ESSENCE);
    }

    public int madness() {
        return info.get(DATA_MADNESS);
    }

    public int page() {
        return info.get(DATA_PAGE);
    }

    public int pages() {
        return Math.max(1, info.get(DATA_PAGES));
    }

    public String query() {
        return query;
    }

    /** 0 cost, 1 name, 2 mod. Kept in a synced slot so the client's button and search see the real value. */
    public int sortMode() {
        return info.get(DATA_SORT);
    }

    public void applyFilter(ServerPlayer player, String query, int sortMode) {
        this.query = query == null ? "" : query;
        info.set(DATA_SORT, sortMode);
        info.set(DATA_PAGE, 0);
        refresh(player);
    }

    private void refresh(ServerPlayer player) {
        MinecraftServer server = player.server;
        Ledger ledger = Ledger.get(server);
        LedgerData data = ledger.data;
        List<String> learned = data.learned();
        LedgerData.Account account = data.account(player.getUUID());
        List<String> filtered = new ArrayList<>();
        for (String id : learned) {
            if (!account.isForgotten(id) && matches(id, query)) {
                filtered.add(id);
            }
        }
        filtered.sort(comparator(info.get(DATA_SORT)));
        shown = filtered;

        int pages = Math.max(1, (shown.size() + PER_PAGE - 1) / PER_PAGE);
        int page = Math.min(info.get(DATA_PAGE), pages - 1);
        if (page < 0) {
            page = 0;
        }
        info.set(DATA_PAGE, page);
        info.set(DATA_PAGES, pages);

        long essence = account.essence();
        for (int i = 0; i < PER_PAGE; i++) {
            int index = page * PER_PAGE + i;
            grid.setItem(i, index < shown.size() ? iconFor(shown.get(index), essence) : ItemStack.EMPTY);
        }

        info.set(DATA_ESSENCE, (int) Math.min(essence, Integer.MAX_VALUE));
        info.set(DATA_MADNESS, account.madness());
        refreshDemand(player);
    }

    private void refreshDemand(ServerPlayer player) {
        LedgerData.Account account = Ledger.get(player.server).data.account(player.getUUID());
        String demanded = account.demandItemId();
        boolean active = "ACTIVE".equals(account.demandState());
        int demandId = -1;
        if (active && demanded != null) {
            demandId = BuiltInRegistries.ITEM.getId(BuiltInRegistries.ITEM.get(ResourceLocation.parse(demanded)));
        }
        info.set(DATA_DEMAND_ITEM, demandId);
        info.set(DATA_DEMAND_COUNT, active ? account.demandCount() : 0);
        info.set(DATA_DEMAND_KIND, active && account.demandKind() != null ? account.demandKind().ordinal() + 1 : 0);
        info.set(DATA_FAVOUR, account.favour());

        int page = info.get(DATA_PAGE);
        int low = 0;
        int high = 0;
        for (int i = 0; i < PER_PAGE; i++) {
            int index = page * PER_PAGE + i;
            if (index < shown.size() && account.isSealed(shown.get(index))) {
                if (i < SEALED_BITS) {
                    low |= 1 << i;
                } else {
                    high |= 1 << (i - SEALED_BITS);
                }
            }
        }
        info.set(DATA_SEALED_LOW, low);
        info.set(DATA_SEALED_HIGH, high);
    }

    private static ItemStack iconFor(String itemId, long essence) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        ItemStack stack = new ItemStack(item);
        long value = ValueTable.value(itemId);
        ChatFormatting costColor = essence >= value ? ChatFormatting.GREEN : ChatFormatting.RED;
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Cost: " + value + " essence").withStyle(costColor))));
        return stack;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        int page = info.get(DATA_PAGE);
        int pages = info.get(DATA_PAGES);
        if (id == BUTTON_PREVIOUS && page > 0) {
            page--;
        } else if (id == BUTTON_NEXT && page < pages - 1) {
            page++;
        } else {
            return false;
        }
        info.set(DATA_PAGE, page);
        refresh(serverPlayer);
        return true;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < PER_PAGE) {
            if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer
                    && clickType == ClickType.PICKUP) {
                withdraw(serverPlayer, slotId);
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    private void withdraw(ServerPlayer player, int slot) {
        int index = info.get(DATA_PAGE) * PER_PAGE + slot;
        if (index < 0 || index >= shown.size()) {
            return;
        }
        String itemId = shown.get(index);
        long cost = ValueTable.value(itemId);
        if (cost <= 0) {
            return;
        }

        Ledger ledger = Ledger.get(player.server);
        if (ledger.data.account(player.getUUID()).isSealed(itemId)) {
            player.sendSystemMessage(Component.literal("Sealed by " + Thalggor.name(player.server, player.getUUID())
                    + " — offer what it demands."));
            refresh(player);
            return;
        }

        if (Economy.withdraw(ledger.data, player.getUUID(), cost) == Economy.Result.NOT_ENOUGH) {
            player.sendSystemMessage(Component.literal("The void is not satisfied. You lack the essence."));
            refresh(player);
            return;
        }
        ledger.setDirty();
        ledger.data.account(player.getUUID()).setLastGainTick(player.server.getTickCount());

        RandomSource random = player.getRandom();
        Risk.Event event = Risk.roll(cost, random.nextDouble(), random.nextDouble());
        boolean keep = RiskEffects.apply(player, event);
        if (keep) {
            // Low favour: the void sometimes hands over junk instead of what was paid for.
            String given = itemId;
            if (ledger.data.account(player.getUUID()).favour() <= Demand.LOW_FAVOUR
                    && random.nextDouble() < Demand.SUBSTITUTE_CHANCE) {
                given = random.nextBoolean() ? "minecraft:rotten_flesh" : "minecraft:poisonous_potato";
            }
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(given)));
            if (!player.addItem(stack)) {
                player.drop(stack, false);
            }
        }
        refresh(player);
    }

    private boolean matches(String itemId, String q) {
        if (q == null || q.trim().isEmpty()) {
            return true;
        }
        String needle = q.trim();
        if (needle.startsWith("@")) {
            String ns = needle.substring(1).trim().toLowerCase();
            String itemNs = itemId.contains(":") ? itemId.substring(0, itemId.indexOf(':')) : "";
            return ns.isEmpty() || itemNs.contains(ns);
        }
        return displayName(itemId).contains(needle.toLowerCase());
    }

    /** Lowercase display name, cached: search and name sorting would otherwise build an ItemStack per comparison. */
    private static String displayName(String itemId) {
        return NAMES.computeIfAbsent(itemId, id -> new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)))
                .getHoverName().getString().toLowerCase(java.util.Locale.ROOT));
    }

    private Comparator<String> comparator(int mode) {
        return switch (mode) {
            case 1 -> Comparator.comparing(CodexMenu::displayName);
            case 2 -> Comparator.comparing((String id) -> id.contains(":") ? id.substring(0, id.indexOf(':')) : "")
                    .thenComparing(CodexMenu::displayName);
            default -> Comparator.comparingLong(ValueTable::value).thenComparing(id -> id);
        };
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    private static final class GridSlot extends Slot {

        GridSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
