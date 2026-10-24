package dev.eldritch.exchange;

import dev.eldritch.exchange.network.SetCodexFilterPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class CodexScreen extends AbstractContainerScreen<CodexMenu> {

    private static final int OUTER_CALM = 0xFF4B2A7A;
    private static final int OUTER_MAD = 0xFF8A1F4F;
    private static final int PANEL = 0xF0120A1E;
    private static final int HEADER = 0xFF1C1033;
    private static final int SLOT_BG = 0xFF2A1A44;
    private static final int TRACK = 0xFF2A1A44;
    private static final int FILL_CALM = 0xFF9B6BFF;
    private static final int FILL_MAD = 0xFFE0408A;
    private static final int TEXT = 0xFFD7B6FF;

    private EditBox searchBox;
    private Button sortButton;
    private static final String[] SORT_LABELS = {"Cost", "Name", "Mod"};

    public CodexScreen(CodexMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 180;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        searchBox = new EditBox(font, leftPos + 8, topPos + 18, 100, 14, Component.literal("Search"));
        searchBox.setResponder(text -> sendFilter());
        addRenderableWidget(searchBox);

        sortButton = Button.builder(Component.literal("Sort: " + SORT_LABELS[menu.sortMode()]), b -> {
            int next = (menu.sortMode() + 1) % SORT_LABELS.length;
            sendFilterWithSort(next);
        }).bounds(leftPos + 112, topPos + 18, 56, 14).build();
        addRenderableWidget(sortButton);

        addRenderableWidget(Button.builder(Component.literal("<"), b -> press(CodexMenu.BUTTON_PREVIOUS))
                .bounds(leftPos + 8, topPos + 152, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> press(CodexMenu.BUTTON_NEXT))
                .bounds(leftPos + imageWidth - 28, topPos + 152, 20, 18).build());
    }

    private void sendFilter() {
        if (minecraft != null && minecraft.getConnection() != null) {
            minecraft.getConnection().send(new SetCodexFilterPayload(menu.containerId, searchBox.getValue(), menu.sortMode()));
        }
    }

    private void sendFilterWithSort(int sortMode) {
        sortButton.setMessage(Component.literal("Sort: " + SORT_LABELS[sortMode]));
        if (minecraft != null && minecraft.getConnection() != null) {
            minecraft.getConnection().send(new SetCodexFilterPayload(menu.containerId, searchBox.getValue(), sortMode));
        }
    }

    private void press(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        boolean deep = menu.madness() >= 50;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, deep ? OUTER_MAD : OUTER_CALM);
        graphics.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, PANEL);
        graphics.fill(x + 2, y + 2, x + imageWidth - 2, y + 32, HEADER);

        for (int row = 0; row < CodexMenu.ROWS; row++) {
            for (int col = 0; col < CodexMenu.COLUMNS; col++) {
                int sx = x + 8 + col * 18;
                int sy = y + 36 + row * 18;
                graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, SLOT_BG);
            }
        }

        int barX = x + 8;
        int barY = y + 26;
        int barWidth = imageWidth - 16;
        graphics.fill(barX, barY, barX + barWidth, barY + 3, TRACK);
        int filled = (int) Math.round(barWidth * (menu.madness() / (double) dev.eldritch.exchange.core.Madness.MAX));
        graphics.fill(barX, barY, barX + filled, barY + 3, deep ? FILL_MAD : FILL_CALM);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Component title = this.title;
        if (menu.madness() >= 50) {
            title = title.copy().withStyle(ChatFormatting.OBFUSCATED);
        }
        graphics.drawString(font, title, (imageWidth - font.width(title)) / 2, 6, TEXT, false);
        graphics.drawString(font, "Essence: " + menu.essence(), 8, 14, TEXT, false);
        String madness = "Madness: " + menu.madness();
        graphics.drawString(font, madness, imageWidth - 8 - font.width(madness), 14, TEXT, false);
        String page = "Page " + (menu.page() + 1) + " / " + menu.pages();
        graphics.drawString(font, page, (imageWidth - font.width(page)) / 2, 157, TEXT, false);
    }
}
