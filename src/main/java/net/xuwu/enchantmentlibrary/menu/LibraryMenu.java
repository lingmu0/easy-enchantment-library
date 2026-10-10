package net.xuwu.enchantmentlibrary.menu;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.xuwu.enchantmentlibrary.EnchantmentLibrary;
import net.xuwu.enchantmentlibrary.block.LibraryBlockEntity;
import net.xuwu.enchantmentlibrary.network.LibraryNetwork;
import net.xuwu.enchantmentlibrary.storage.EnchantmentLedger;
import net.xuwu.enchantmentlibrary.storage.EnchantmentBatch;

public final class LibraryMenu extends AbstractContainerMenu {
    public static final int DEPOSIT = 0, TARGET = 1, OUTPUT = 2;
    private final Container container;
    private final LibraryBlockEntity library;
    private final Player player;
    private long lastRevision = -1;
    private long lastLimitCheck = -20;
    private List<LibraryNetwork.Entry> entries = List.of();
    private int viewRevision;

    public LibraryMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, new SimpleContainer(3), null);
        buffer.readBlockPos();
    }

    public LibraryMenu(int id, Inventory inventory, LibraryBlockEntity library) {
        this(id, inventory, library, library);
    }

    private LibraryMenu(int id, Inventory inventory, Container container, LibraryBlockEntity library) {
        super(EnchantmentLibrary.LIBRARY_MENU.get(), id);
        this.container = container;
        this.library = library;
        this.player = inventory.player;
        addSlot(new Slot(container, DEPOSIT, 17, 42) {
            @Override public boolean mayPlace(ItemStack stack) { return hasEnchantments(stack); }
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new Slot(container, TARGET, 17, 83) {
            @Override public boolean mayPlace(ItemStack stack) { return acceptsTarget(stack); }
        });
        addSlot(new Slot(container, OUTPUT, 17, 122) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column + row * 9 + 9, 87 + column * 18, 153 + row * 18));
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 87 + column * 18, 211));
    }

    public static boolean acceptsTarget(ItemStack stack) {
        return stack.is(Items.BOOK) || (!stack.is(Items.ENCHANTED_BOOK) && stack.getMaxStackSize() == 1);
    }
    public static boolean hasEnchantments(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && (!tag.getList("Enchantments", Tag.TAG_COMPOUND).isEmpty()
                || !tag.getList("StoredEnchantments", Tag.TAG_COMPOUND).isEmpty());
    }
    private static ItemStack transmute(ItemStack input, Item item) {
        CompoundTag saved = input.save(new CompoundTag());
        saved.putString("id", ForgeRegistries.ITEMS.getKey(item).toString());
        saved.putByte("Count", (byte) 1);
        return ItemStack.of(saved);
    }

    public List<LibraryNetwork.Entry> entries() { return entries; }
    public int viewRevision() { return viewRevision; }
    public void updateEntries(List<LibraryNetwork.Entry> entries) { this.entries = List.copyOf(entries); viewRevision++; }
    public Enchantment enchantment(ResourceLocation id) { return ForgeRegistries.ENCHANTMENTS.getValue(id); }

    @Override public boolean stillValid(Player player) { return library == null || library.stillValid(player); }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (library == null || !(player instanceof ServerPlayer serverPlayer)) return;
        long tick = player.level().getGameTime();
        if (lastRevision == library.ledger().revision() && tick - lastLimitCheck < 20) return;
        List<LibraryNetwork.Entry> current = library.ledger().snapshot().entrySet().stream().map(entry -> {
            Enchantment enchantment = enchantment(entry.getKey());
            return new LibraryNetwork.Entry(entry.getKey(), entry.getValue(), enchantment == null ? 0 : enchantment.getMaxLevel());
        }).toList();
        if (lastRevision != library.ledger().revision() || !current.equals(entries)) {
            updateEntries(current);
            LibraryNetwork.sendToPlayer(serverPlayer, new LibraryNetwork.Snapshot(containerId, current));
        }
        lastRevision = library.ledger().revision();
        lastLimitCheck = tick;
    }

    public void perform(int action, Map<ResourceLocation, Integer> levels) {
        if (library == null || player.isSpectator() || !stillValid(player)) return;
        boolean success = switch (action) {
            case LibraryNetwork.STORE -> deposit();
            case LibraryNetwork.WITHDRAW -> applyBatch(levels, true);
            case LibraryNetwork.ENCHANT -> applyBatch(levels, false);
            default -> false;
        };
        if (success) {
            library.setChanged();
            broadcastChanges();
            player.level().playSound(null, library.getBlockPos(), net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, 1.0F);
        } else player.displayClientMessage(Component.translatable("message.enchantment_library.failed"), true);
    }

    private boolean canOutput(ItemStack result) {
        ItemStack output = container.getItem(OUTPUT);
        return output.isEmpty() || ItemStack.isSameItemSameTags(output, result) && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }
    private void putOutput(ItemStack result) {
        ItemStack output = container.getItem(OUTPUT);
        if (output.isEmpty()) container.setItem(OUTPUT, result);
        else { output.grow(result.getCount()); container.setChanged(); }
    }

    private boolean deposit() {
        ItemStack input = container.getItem(DEPOSIT);
        if (input.isEmpty() || !hasEnchantments(input)) return false;
        Map<ResourceLocation, BigInteger> additions = new HashMap<>();
        CompoundTag data = input.getTag();
        for (String key : List.of("Enchantments", "StoredEnchantments")) {
            ListTag enchantments = data.getList(key, Tag.TAG_COMPOUND);
            for (int index = 0; index < enchantments.size(); index++) {
                CompoundTag entry = enchantments.getCompound(index);
                ResourceLocation id = ResourceLocation.tryParse(entry.getString("id"));
                int level = entry.getInt("lvl");
                if (id == null || level < 1 || level > EnchantmentLedger.MAX_BITS) return false;
                additions.merge(id, EnchantmentLedger.cost(level), BigInteger::add);
            }
        }
        ItemStack clean = input.is(Items.ENCHANTED_BOOK) ? transmute(input, Items.BOOK) : input.copyWithCount(1);
        clean.removeTagKey("Enchantments");
        clean.removeTagKey("StoredEnchantments");
        if (!canOutput(clean) || !library.ledger().deposit(additions)) return false;
        container.removeItem(DEPOSIT, 1);
        putOutput(clean);
        return true;
    }

    private boolean validLevel(Enchantment enchantment, int level) {
        return enchantment != null && level >= enchantment.getMinLevel() && level <= enchantment.getMaxLevel();
    }

    public boolean canWithdraw(Map<ResourceLocation, Integer> levels) { return previewBatch(levels, true) != null; }
    public boolean canEnchant(Map<ResourceLocation, Integer> levels) { return previewBatch(levels, false) != null; }

    private BigInteger points(ResourceLocation id) {
        return library != null ? library.ledger().get(id) : entries.stream().filter(e -> e.id().equals(id)).map(LibraryNetwork.Entry::points).findFirst().orElse(BigInteger.ZERO);
    }

    public BigInteger enchantCost(ResourceLocation id, int level) {
        Enchantment enchantment = enchantment(id);
        if (!validLevel(enchantment, level) || level > EnchantmentLedger.MAX_BITS) return BigInteger.ZERO;
        int existing = EnchantmentHelper.getEnchantments(container.getItem(TARGET)).getOrDefault(enchantment, 0);
        if (existing > EnchantmentLedger.MAX_BITS) return BigInteger.ZERO;
        return EnchantmentLedger.cost(level).subtract(existing == 0 ? BigInteger.ZERO : EnchantmentLedger.cost(existing));
    }

    private record BatchPlan(ItemStack result, Map<ResourceLocation, BigInteger> costs) {}

    private BatchPlan previewBatch(Map<ResourceLocation, Integer> levels, boolean book) {
        ItemStack target = container.getItem(TARGET);
        if (target.isEmpty()) return null;
        if (book) {
            if (!target.is(Items.BOOK) || hasEnchantments(target)) return null;
        } else if (target.is(Items.BOOK) || target.is(Items.ENCHANTED_BOOK) || target.getCount() != 1) return null;
        Map<ResourceLocation, Integer> existing = new HashMap<>();
        Map<Enchantment, Integer> resultEnchantments = new HashMap<>(EnchantmentHelper.getEnchantments(target));
        if (!book) {
            // Do not silently discard enchantments whose supplying mod has been removed.
            for (int index = 0; index < target.getEnchantmentTags().size(); index++) {
                ResourceLocation id = EnchantmentHelper.getEnchantmentId(target.getEnchantmentTags().getCompound(index));
                if (id == null || enchantment(id) == null) return null;
            }
            for (var entry : resultEnchantments.entrySet()) {
                ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(entry.getKey());
                if (id == null) return null;
                existing.put(id, entry.getValue());
            }
        }
        var planned = EnchantmentBatch.plan(levels, existing, id -> {
            Enchantment enchantment = enchantment(id);
            return enchantment == null ? null : new EnchantmentBatch.LevelRange(enchantment.getMinLevel(), enchantment.getMaxLevel());
        }, id -> book || enchantment(id).canEnchant(target), (first, second) -> {
            if (book) return true;
            Enchantment a = enchantment(first), b = enchantment(second);
            return a != null && b != null && a.isCompatibleWith(b);
        });
        if (planned.isEmpty()) return null;
        Map<ResourceLocation, BigInteger> costs = planned.get();
        if (costs.entrySet().stream().anyMatch(entry -> points(entry.getKey()).compareTo(entry.getValue()) < 0)) return null;
        ItemStack result = book ? transmute(target, Items.ENCHANTED_BOOK) : target.copy();
        levels.forEach((id, level) -> resultEnchantments.put(enchantment(id), level));
        EnchantmentHelper.setEnchantments(resultEnchantments, result);
        for (var entry : levels.entrySet()) {
            if (!EnchantmentHelper.getEnchantments(result).getOrDefault(enchantment(entry.getKey()), 0).equals(entry.getValue())) return null;
        }
        if (book && !canOutput(result)) return null;
        return new BatchPlan(result, costs);
    }

    private boolean applyBatch(Map<ResourceLocation, Integer> levels, boolean book) {
        BatchPlan plan = previewBatch(levels, book);
        if (plan == null || !library.ledger().withdrawAll(plan.costs())) return false;
        if (book) {
            container.removeItem(TARGET, 1);
            putOutput(plan.result());
        } else container.setItem(TARGET, plan.result());
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < 3) {
            if (!moveItemStackTo(stack, 3, slots.size(), true)) return ItemStack.EMPTY;
        } else if (hasEnchantments(stack)) {
            if (!moveItemStackTo(stack, DEPOSIT, DEPOSIT + 1, false)) return ItemStack.EMPTY;
        } else if (acceptsTarget(stack)) {
            if (!moveItemStackTo(stack, TARGET, TARGET + 1, false)) return ItemStack.EMPTY;
        } else if (index < 30) {
            if (!moveItemStackTo(stack, 30, 39, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 3, 30, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (original.getCount() == stack.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}
