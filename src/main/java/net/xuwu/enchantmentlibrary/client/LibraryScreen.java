package net.xuwu.enchantmentlibrary.client;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.xuwu.enchantmentlibrary.menu.LibraryMenu;
import net.xuwu.enchantmentlibrary.network.LibraryNetwork;
import net.xuwu.enchantmentlibrary.storage.EnchantmentBatch;
import net.xuwu.enchantmentlibrary.storage.EnchantmentLedger;
import org.lwjgl.glfw.GLFW;

public final class LibraryScreen extends AbstractContainerScreen<LibraryMenu> {
    private static final Identifier CONTAINER = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final Identifier SCROLLER = Identifier.withDefaultNamespace("widget/scroller");
    private static final Identifier SCROLLER_BACKGROUND = Identifier.withDefaultNamespace("widget/scroller_background");
    private static final int ROWS = 4, LIST_X = 122, LIST_Y = 43, LIST_W = 206, ROW_H = 24;
    private final Map<Identifier, Integer> chosenLevels = new HashMap<>();
    private final Set<Identifier> checked = new LinkedHashSet<>();
    private final List<AbstractWidget> rowWidgets = new ArrayList<>();
    private final Map<Identifier, Checkbox> checkboxes = new HashMap<>();
    private final Map<Identifier, LevelSlider> sliders = new HashMap<>();
    private final Map<Identifier, ItemStack> icons = new HashMap<>();
    private EditBox search;
    private Button store, withdraw, enchant, clear;
    private List<LibraryNetwork.Entry> visible = List.of();
    private int scroll, lastRevision = -1;
    private boolean draggingScrollbar;
    private LevelSlider draggingLevelSlider;

    public LibraryScreen(LibraryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 336, 236);
    }

    @Override
    protected void init() {
        String previousSearch = search == null ? "" : search.getValue();
        super.init();
        rowWidgets.clear();
        checkboxes.clear();
        sliders.clear();
        search = new EditBox(font, leftPos + LIST_X, topPos + 23, LIST_W, 17, tr("search"));
        search.setMaxLength(128);
        search.setHint(tr("search"));
        search.setValue(previousSearch);
        search.setTooltip(Tooltip.create(tr(PinyinSearch.available() ? "pinyin_enabled" : "pinyin_optional")));
        addRenderableWidget(search);
        store = button("store", 42, 40, 72, () -> send(LibraryNetwork.STORE));
        store.setTooltip(Tooltip.create(tr("store_help")));
        enchant = button("enchant", 42, 81, 72, () -> send(LibraryNetwork.ENCHANT));
        enchant.setTooltip(Tooltip.create(tr("enchant_help")));
        withdraw = button("withdraw", 42, 120, 72, () -> send(LibraryNetwork.WITHDRAW));
        withdraw.setTooltip(Tooltip.create(tr("withdraw_help")));
        clear = addRenderableWidget(Button.builder(tr("clear_selection"), b -> {
            checked.clear();
            rebuildRows();
        }).bounds(leftPos + 284, topPos + 5, 44, 16).build());
        search.setResponder(value -> { scroll = 0; refresh(); });
        refresh();
    }

    private Button button(String key, int x, int y, int width, Runnable action) {
        return addRenderableWidget(Button.builder(tr(key), b -> action.run()).bounds(leftPos + x, topPos + y, width, 20).build());
    }
    private static MutableComponent tr(String key, Object... args) { return Component.translatable("gui.enchantment_library." + key, args); }
    private String name(LibraryNetwork.Entry entry) {
        Holder<Enchantment> holder = menu.enchantment(entry.id());
        return holder == null ? entry.id().toString() : holder.value().description().getString();
    }
    private int minimum(LibraryNetwork.Entry entry) {
        Holder<Enchantment> holder = menu.enchantment(entry.id());
        return holder == null ? 1 : Math.max(1, holder.value().getMinLevel());
    }

    private Map<Identifier, Integer> selection() {
        Map<Identifier, Integer> result = new LinkedHashMap<>();
        checked.forEach(id -> result.put(id, chosenLevels.getOrDefault(id, 1)));
        return result;
    }

    private void refresh() {
        Set<Identifier> available = new LinkedHashSet<>();
        for (LibraryNetwork.Entry entry : menu.entries()) {
            available.add(entry.id());
            int min = minimum(entry);
            chosenLevels.put(entry.id(), Mth.clamp(chosenLevels.getOrDefault(entry.id(), min), min, Math.max(min, entry.maxLevel())));
            if (entry.maxLevel() < min) checked.remove(entry.id());
        }
        // Search filtering keeps selections; an entry is removed only when its stored balance disappears.
        checked.retainAll(available);
        chosenLevels.keySet().retainAll(available);
        String query = search == null ? "" : search.getValue();
        visible = menu.entries().stream().filter(entry -> PinyinSearch.matches(name(entry), entry.id().toString(), query))
                .sorted(Comparator.comparing(this::name).thenComparing(e -> e.id().toString())).toList();
        scroll = Mth.clamp(scroll, 0, Math.max(0, visible.size() - ROWS));
        rebuildRows();
        lastRevision = menu.viewRevision();
    }

    private void setChecked(Identifier id, boolean value) {
        Checkbox checkbox = checkboxes.get(id);
        if (value && !checked.contains(id) && checked.size() >= EnchantmentBatch.MAX_SELECTIONS) {
            if (checkbox != null && checkbox.selected()) checkbox.onPress(new KeyEvent(GLFW.GLFW_KEY_SPACE, 0, 0));
            return;
        }
        if (checkbox != null && checkbox.selected() != value) { checkbox.onPress(new KeyEvent(GLFW.GLFW_KEY_SPACE, 0, 0)); return; }
        if (value) checked.add(id);
        else checked.remove(id);
    }

    private void rebuildRows() {
        if (rowWidgets.contains(getFocused())) { setFocused(null); setDragging(false); }
        draggingLevelSlider = null;
        rowWidgets.forEach(this::removeWidget);
        rowWidgets.clear();
        checkboxes.clear();
        sliders.clear();
        icons.clear();
        for (int row = 0; row < ROWS && row + scroll < visible.size(); row++) {
            LibraryNetwork.Entry entry = visible.get(row + scroll);
            int x = leftPos + LIST_X, y = topPos + LIST_Y + row * ROW_H;
            Checkbox checkbox = Checkbox.builder(Component.empty(), font).pos(x + 2, y + 4).maxWidth(17)
                    .selected(checked.contains(entry.id())).onValueChange((box, value) -> setChecked(entry.id(), value))
                    .tooltip(Tooltip.create(tr("select_help"))).build();
            checkbox.setMessage(tr("select_name", name(entry)));
            checkbox.active = entry.maxLevel() >= minimum(entry);
            checkboxes.put(entry.id(), checkbox);
            rowWidgets.add(addRenderableWidget(checkbox));
            LevelSlider slider = new LevelSlider(entry, x + 42, y + 13, 117, 10);
            sliders.put(entry.id(), slider);
            rowWidgets.add(addRenderableWidget(slider));
            updateIcon(entry.id());
        }
    }

    private void updateIcon(Identifier id) {
        ItemStack icon = new ItemStack(Items.ENCHANTED_BOOK);
        Holder<Enchantment> holder = menu.enchantment(id);
        if (holder != null) icon.enchant(holder, chosenLevels.getOrDefault(id, 1));
        icons.put(id, icon);
    }

    private void send(int action) {
        Map<Identifier, Integer> levels = action == LibraryNetwork.STORE ? Map.of() : selection();
        if (action != LibraryNetwork.STORE && levels.isEmpty()) return;
        ClientPacketDistributor.sendToServer(new LibraryNetwork.Action(menu.containerId, action, levels));
    }

    @Override protected void containerTick() {
        super.containerTick();
        if (lastRevision != menu.viewRevision()) refresh();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (lastRevision != menu.viewRevision()) refresh();
        Map<Identifier, Integer> levels = selection();
        store.active = EnchantmentHelper.hasAnyEnchantments(menu.getSlot(LibraryMenu.DEPOSIT).getItem());
        enchant.active = menu.canEnchant(levels);
        withdraw.active = menu.canWithdraw(levels);
        clear.active = !checked.isEmpty();
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int hovered = rowAt(mouseX, mouseY);
        if (hovered >= 0) {
            LibraryNetwork.Entry entry = visible.get(hovered);
            int rowY = topPos + LIST_Y + (hovered - scroll) * ROW_H;
            if (mouseX >= leftPos + LIST_X + 21 && mouseX < leftPos + LIST_X + 39) {
                graphics.setTooltipForNextFrame(font, icons.get(entry.id()), mouseX, mouseY);
            } else if (mouseY < rowY + 13 && mouseX >= leftPos + LIST_X + 41) {
                int level = chosenLevels.get(entry.id());
                BigInteger cost = level > EnchantmentLedger.MAX_BITS ? BigInteger.ZERO :
                        menu.getSlot(LibraryMenu.TARGET).getItem().is(Items.BOOK) ? EnchantmentLedger.cost(level) : menu.enchantCost(entry.id(), level);
                graphics.setComponentTooltipForNextFrame(font, List.of(Component.literal(name(entry)), Component.literal(entry.id().toString()),
                        tr("points", entry.points().toString()), tr("maximum", entry.maxLevel()), tr("cost", cost.max(BigInteger.ZERO).toString())), mouseX, mouseY);
            }
        } else if (isHovering(180, 5, 98, 16, mouseX, mouseY) && !checked.isEmpty()) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(tr("selected_count", checked.size()));
            for (LibraryNetwork.Entry entry : menu.entries()) {
                if (checked.contains(entry.id())) tooltip.add(Component.literal(name(entry) + " " + chosenLevels.get(entry.id())));
            }
            tooltip.add(tr("batch_help"));
            graphics.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
        }
        for (int i = 0; i < 3; i++) {
            var slot = menu.getSlot(i);
            if (!slot.hasItem() && isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
                graphics.setTooltipForNextFrame(font, tr(switch (i) { case 0 -> "deposit_slot"; case 1 -> "target_slot"; default -> "output_slot"; }), mouseX, mouseY);
            }
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        drawContainer(graphics, leftPos, topPos, imageWidth, imageHeight);
        for (var slot : menu.slots) drawSlot(graphics, leftPos + slot.x - 1, topPos + slot.y - 1);
        int x = leftPos + LIST_X, y = topPos + LIST_Y;
        graphics.fill(x - 1, y - 1, x + LIST_W + 1, y + ROWS * ROW_H + 1, 0xFF555555);
        graphics.fill(x, y, x + LIST_W, y + ROWS * ROW_H, 0xFF8B8B8B);
        for (int row = 0; row < ROWS && row + scroll < visible.size(); row++) {
            LibraryNetwork.Entry entry = visible.get(row + scroll);
            int rowY = y + row * ROW_H;
            boolean selected = checked.contains(entry.id()), hover = rowAt(mouseX, mouseY) == row + scroll;
            graphics.fill(x, rowY, x + LIST_W - 9, rowY + ROW_H - 1, selected ? 0xFFABB890 : hover ? 0xFFC0C0C0 : 0xFFB4B4B4);
            drawSlot(graphics, x + 21, rowY + 3);
            ItemStack icon = icons.get(entry.id());
            graphics.item(icon, x + 22, rowY + 4);
            graphics.itemDecorations(font, icon, x + 22, rowY + 4);
            String amount = compact(entry.points());
            int amountWidth = font.width(amount);
            String label = font.plainSubstrByWidth(name(entry), LIST_W - 58 - amountWidth);
            graphics.text(font, label, x + 42, rowY + 2, 0xFF303030, false);
            graphics.text(font, amount, x + LIST_W - 13 - amountWidth, rowY + 2, 0xFF404040, false);
            String level = chosenLevels.get(entry.id()) + "/" + entry.maxLevel();
            if (font.width(level) > 34) level = chosenLevels.get(entry.id()).toString();
            graphics.text(font, font.plainSubstrByWidth(level, 34), x + 163, rowY + 14, 0xFF404040, false);
        }
        if (visible.isEmpty()) graphics.centeredText(font, tr(menu.entries().isEmpty() ? "empty" : "no_results"), x + LIST_W / 2, y + 42, 0xFFE0E0E0);
        int trackX = x + LIST_W - 8;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_BACKGROUND, trackX, y, 8, ROWS * ROW_H);
        int thumbHeight = thumbHeight();
        int thumbY = y + (visible.size() <= ROWS ? 0 : (ROWS * ROW_H - thumbHeight) * scroll / (visible.size() - ROWS));
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER, trackX, thumbY, 8, thumbHeight);
    }

    private int thumbHeight() { return Math.max(12, ROWS * ROW_H * ROWS / Math.max(ROWS, visible.size())); }
    private static String compact(BigInteger value) {
        String digits = value.toString();
        return digits.length() <= 7 ? digits : digits.substring(0, 1) + "." + digits.substring(1, 3) + "e" + (digits.length() - 1);
    }

    /** The original chest texture supplies the rounded corners, bevels and slots, including resource-pack replacements. */
    private static void drawContainer(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        patch(graphics, x + 5, y + 5, width - 10, height - 10, 5, 5, 1, 1);
        patch(graphics, x, y, 5, 5, 0, 0, 5, 5);
        patch(graphics, x + width - 5, y, 5, 5, 171, 0, 5, 5);
        patch(graphics, x, y + height - 5, 5, 5, 0, 217, 5, 5);
        patch(graphics, x + width - 5, y + height - 5, 5, 5, 171, 217, 5, 5);
        patch(graphics, x + 5, y, width - 10, 5, 5, 0, 1, 5);
        patch(graphics, x + 5, y + height - 5, width - 10, 5, 5, 217, 1, 5);
        patch(graphics, x, y + 5, 5, height - 10, 0, 5, 5, 1);
        patch(graphics, x + width - 5, y + 5, 5, height - 10, 171, 5, 5, 1);
    }
    private static void patch(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int u, int v, int sourceWidth, int sourceHeight) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER, x, y, (float) u, (float) v, width, height, sourceWidth, sourceHeight, 256, 256);
    }
    private static void drawSlot(GuiGraphicsExtractor graphics, int x, int y) { graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER, x, y, 7, 17, 18, 18, 256, 256); }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, font.plainSubstrByWidth(title.getString(), 164), 8, 7, 0xFF404040, false);
        graphics.text(font, tr("selected_count", checked.size()), 180, 8, 0xFF404040, false);
        graphics.text(font, tr("deposit"), 8, 27, 0xFF404040, false);
        graphics.text(font, tr("target"), 8, 68, 0xFF404040, false);
        graphics.text(font, tr("output"), 8, 107, 0xFF404040, false);
        graphics.text(font, playerInventoryTitle, 87, 143, 0xFF404040, false);
    }

    private int rowAt(double mouseX, double mouseY) {
        double x = mouseX - leftPos - LIST_X, y = mouseY - topPos - LIST_Y;
        if (x < 0 || x >= LIST_W - 9 || y < 0 || y >= ROWS * ROW_H) return -1;
        int row = (int) y / ROW_H + scroll;
        return row < visible.size() ? row : -1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();
        if (button == 1 && search.isMouseOver(mouseX, mouseY)) { search.setValue(""); return true; }
        int row = rowAt(mouseX, mouseY);
        if (button == 0 && row >= 0) {
            LibraryNetwork.Entry entry = visible.get(row);
            if (checkboxes.get(entry.id()).isMouseOver(mouseX, mouseY) || sliders.get(entry.id()).isMouseOver(mouseX, mouseY)) {
                boolean handled = super.mouseClicked(event, doubleClick);
                LevelSlider slider = sliders.get(entry.id());
                if (handled && slider.active && slider.isMouseOver(mouseX, mouseY)) draggingLevelSlider = slider;
                return handled;
            }
            if (entry.maxLevel() >= minimum(entry)) setChecked(entry.id(), !checked.contains(entry.id()));
            setFocused(null);
            return true;
        }
        if (button == 0 && mouseX >= leftPos + LIST_X + LIST_W - 8 && mouseX < leftPos + LIST_X + LIST_W
                && mouseY >= topPos + LIST_Y && mouseY < topPos + LIST_Y + ROWS * ROW_H) {
            setFocused(null);
            draggingScrollbar = true;
            dragScrollbar(mouseY);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void setScroll(int value) {
        int updated = Mth.clamp(value, 0, Math.max(0, visible.size() - ROWS));
        if (updated != scroll) { scroll = updated; rebuildRows(); }
    }
    private void dragScrollbar(double mouseY) {
        int travel = ROWS * ROW_H - thumbHeight();
        if (travel > 0) setScroll((int) Math.round((mouseY - topPos - LIST_Y - thumbHeight() / 2.0) / travel * Math.max(0, visible.size() - ROWS)));
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        double y = event.y();
        int button = event.button();
        if (draggingScrollbar && button == 0) { dragScrollbar(y); return true; }
        // AbstractContainerScreen handles inventory drags without forwarding them to focused widgets.
        if (draggingLevelSlider != null && button == 0) {
            draggingLevelSlider.mouseDragged(event, dx, dy);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        int button = event.button();
        if (button == 0 && (draggingLevelSlider != null || draggingScrollbar)) {
            if (draggingLevelSlider != null) draggingLevelSlider.mouseReleased(event);
            draggingLevelSlider = null;
            draggingScrollbar = false;
            setDragging(false);
            return true;
        }
        return super.mouseReleased(event);
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (x >= leftPos + LIST_X && x < leftPos + LIST_X + LIST_W && y >= topPos + LIST_Y && y < topPos + LIST_Y + ROWS * ROW_H) {
            setScroll(scroll - (int) Math.signum(dy)); return true;
        }
        return super.mouseScrolled(x, y, dx, dy);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key != GLFW.GLFW_KEY_ESCAPE && search.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { setFocused(null); return true; }
            if (key == GLFW.GLFW_KEY_TAB) return super.keyPressed(event);
            search.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }

    private final class LevelSlider extends AbstractSliderButton {
        private final Identifier id;
        private final int min, max;

        LevelSlider(LibraryNetwork.Entry entry, int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), 0.0);
            id = entry.id();
            min = minimum(entry);
            max = Math.max(min, entry.maxLevel());
            active = max > min;
            snapValue();
            updateMessage();
        }
        private void snapValue() { value = max == min ? 0 : (double) (chosenLevels.get(id) - min) / (max - min); }
        @Override protected void updateMessage() { setTooltip(Tooltip.create(tr("slider_help", chosenLevels.get(id), max))); }
        @Override protected MutableComponent createNarrationMessage() { return tr("slider_help", chosenLevels.get(id), max); }
        @Override protected void applyValue() {
            chosenLevels.put(id, min + (int) Math.round(value * (max - min)));
            snapValue();
            setChecked(id, true);
            updateIcon(id);
        }
        @Override public boolean keyPressed(KeyEvent event) {
            int key = event.key();
            if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END) {
                int step = event.hasShiftDown() ? 5 : 1;
                long desired = key == GLFW.GLFW_KEY_HOME ? min : key == GLFW.GLFW_KEY_END ? max :
                        (long) chosenLevels.get(id) + (key == GLFW.GLFW_KEY_LEFT ? -step : step);
                chosenLevels.put(id, (int) Math.clamp(desired, min, max));
                snapValue();
                setChecked(id, true);
                updateIcon(id);
                updateMessage();
                return true;
            }
            return super.keyPressed(event);
        }
    }
}
