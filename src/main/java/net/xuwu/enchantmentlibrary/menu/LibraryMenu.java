package net.xuwu.enchantmentlibrary.menu;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
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
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.network.PacketDistributor;
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

    public LibraryMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
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
            @Override public boolean mayPlace(ItemStack stack) { return EnchantmentHelper.hasAnyEnchantments(stack); }
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
        return stack.is(Items.BOOK) || (!stack.is(Items.ENCHANTED_BOOK) && stack.getMaxStackSize() == 1 && EnchantmentHelper.canStoreEnchantments(stack));
    }

    public List<LibraryNetwork.Entry> entries() { return entries; }
    public int viewRevision() { return viewRevision; }
    public void updateEntries(List<LibraryNetwork.Entry> entries) { this.entries = List.copyOf(entries); viewRevision++; }
    public Holder.Reference<Enchantment> enchantment(Identifier id) {
        return player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .get(ResourceKey.create(Registries.ENCHANTMENT, id)).orElse(null);
    }

    @Override public boolean stillValid(Player player) { return library == null || library.stillValid(player); }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (library == null || !(player instanceof ServerPlayer serverPlayer)) return;
        long tick = player.level().getGameTime();
        if (lastRevision == library.ledger().revision() && tick - lastLimitCheck < 20) return;
        List<LibraryNetwork.Entry> current = library.ledger().snapshot().entrySet().stream().map(entry -> {
            Holder<Enchantment> enchantment = enchantment(entry.getKey());
            return new LibraryNetwork.Entry(entry.getKey(), entry.getValue(), enchantment == null ? 0 : enchantment.value().getMaxLevel());
        }).toList();
        if (lastRevision != library.ledger().revision() || !current.equals(entries)) {
            updateEntries(current);
            PacketDistributor.sendToPlayer(serverPlayer, new LibraryNetwork.Snapshot(containerId, current));
        }
        lastRevision = library.ledger().revision();
        lastLimitCheck = tick;
    }

    public void perform(int action, Map<Identifier, Integer> levels) {
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
        } else player.sendOverlayMessage(Component.translatable("message.enchantment_library.failed"));
    }

    private boolean canOutput(ItemStack result) {
        ItemStack output = container.getItem(OUTPUT);
        return output.isEmpty() || ItemStack.isSameItemSameComponents(output, result) && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }
    private void putOutput(ItemStack result) {
        ItemStack output = container.getItem(OUTPUT);
        if (output.isEmpty()) container.setItem(OUTPUT, result);
        else { output.grow(result.getCount()); container.setChanged(); }
    }

    private boolean deposit() {
        ItemStack input = container.getItem(DEPOSIT);
        if (input.isEmpty() || !EnchantmentHelper.hasAnyEnchantments(input)) return false;
        Map<Identifier, BigInteger> additions = new HashMap<>();
        for (ItemEnchantments enchantments : List.of(input.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY),
                input.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY))) {
            for (var entry : enchantments.entrySet()) {
                if (entry.getIntValue() < 1 || entry.getIntValue() > EnchantmentLedger.MAX_BITS || entry.getKey().unwrapKey().isEmpty()) return false;
                additions.merge(entry.getKey().unwrapKey().orElseThrow().identifier(), EnchantmentLedger.cost(entry.getIntValue()), BigInteger::add);
            }
        }
        ItemStack clean = input.is(Items.ENCHANTED_BOOK) ? input.transmuteCopy(Items.BOOK, 1) : input.copyWithCount(1);
        clean.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        clean.remove(DataComponents.STORED_ENCHANTMENTS);
        if (!canOutput(clean) || !library.ledger().deposit(additions)) return false;
        container.removeItem(DEPOSIT, 1);
        putOutput(clean);
        return true;
    }

    private boolean validLevel(Holder<Enchantment> enchantment, int level) {
        return enchantment != null && level >= enchantment.value().getMinLevel() && level <= enchantment.value().getMaxLevel();
    }

    public boolean canWithdraw(Map<Identifier, Integer> levels) { return previewBatch(levels, true) != null; }
    public boolean canEnchant(Map<Identifier, Integer> levels) { return previewBatch(levels, false) != null; }

    private BigInteger points(Identifier id) {
        return library != null ? library.ledger().get(id) : entries.stream().filter(e -> e.id().equals(id)).map(LibraryNetwork.Entry::points).findFirst().orElse(BigInteger.ZERO);
    }

    public BigInteger enchantCost(Identifier id, int level) {
        Holder<Enchantment> enchantment = enchantment(id);
        if (!validLevel(enchantment, level) || level > EnchantmentLedger.MAX_BITS) return BigInteger.ZERO;
        int existing = EnchantmentHelper.getEnchantmentsForCrafting(container.getItem(TARGET)).getLevel(enchantment);
        if (existing > EnchantmentLedger.MAX_BITS) return BigInteger.ZERO;
        return EnchantmentLedger.cost(level).subtract(existing == 0 ? BigInteger.ZERO : EnchantmentLedger.cost(existing));
    }

    private record BatchPlan(ItemStack result, Map<Identifier, BigInteger> costs) {}

    private BatchPlan previewBatch(Map<Identifier, Integer> levels, boolean book) {
        ItemStack target = container.getItem(TARGET);
        if (target.isEmpty()) return null;
        if (book) {
            if (!target.is(Items.BOOK) || EnchantmentHelper.hasAnyEnchantments(target)) return null;
        } else if (target.is(Items.BOOK) || target.is(Items.ENCHANTED_BOOK) || target.getCount() != 1) return null;
        Map<Identifier, Integer> existing = new HashMap<>();
        if (!book) {
            for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(target).entrySet()) {
                if (entry.getKey().unwrapKey().isEmpty()) return null;
                existing.put(entry.getKey().unwrapKey().orElseThrow().identifier(), entry.getIntValue());
            }
        }
        var planned = EnchantmentBatch.plan(levels, existing, id -> {
            Holder<Enchantment> holder = enchantment(id);
            return holder == null ? null : new EnchantmentBatch.LevelRange(holder.value().getMinLevel(), holder.value().getMaxLevel());
        }, id -> book || target.supportsEnchantment(enchantment(id)), (first, second) -> {
            if (book) return true;
            Holder<Enchantment> a = enchantment(first), b = enchantment(second);
            return a != null && b != null && Enchantment.areCompatible(a, b);
        });
        if (planned.isEmpty()) return null;
        Map<Identifier, BigInteger> costs = planned.get();
        if (costs.entrySet().stream().anyMatch(entry -> points(entry.getKey()).compareTo(entry.getValue()) < 0)) return null;
        ItemStack result = book ? target.transmuteCopy(Items.ENCHANTED_BOOK, 1) : target.copy();
        for (var entry : levels.entrySet()) {
            Holder<Enchantment> holder = enchantment(entry.getKey());
            result.enchant(holder, entry.getValue());
            if (EnchantmentHelper.getEnchantmentsForCrafting(result).getLevel(holder) != entry.getValue()) return null;
        }
        if (book && !canOutput(result)) return null;
        return new BatchPlan(result, costs);
    }

    private boolean applyBatch(Map<Identifier, Integer> levels, boolean book) {
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
        } else if (EnchantmentHelper.hasAnyEnchantments(stack)) {
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
