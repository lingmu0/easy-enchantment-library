package net.xuwu.enchantmentlibrary.block;

import java.math.BigInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.xuwu.enchantmentlibrary.EnchantmentLibrary;
import net.xuwu.enchantmentlibrary.menu.LibraryMenu;
import net.xuwu.enchantmentlibrary.storage.EnchantmentLedger;
import net.xuwu.enchantmentlibrary.storage.LibraryContents;

public final class LibraryBlockEntity extends BlockEntity implements Container, MenuProvider {
    private final NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
    private final EnchantmentLedger<Identifier> ledger = new EnchantmentLedger<>();

    public LibraryBlockEntity(BlockPos pos, BlockState state) { super(EnchantmentLibrary.LIBRARY_ENTITY.get(), pos, state); }
    public EnchantmentLedger<Identifier> ledger() { return ledger; }
    public boolean hasContents() { return !isEmpty() || !ledger.snapshot().isEmpty(); }
    @Override public Component getDisplayName() { return Component.translatable("block.enchantment_library.enchantment_library"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new LibraryMenu(id, inventory, this); }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.store("StoredEnchantments", LibraryContents.CODEC, new LibraryContents(ledger.snapshot()));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.clear();
        ContainerHelper.loadAllItems(input, items);
        ledger.replace(input.read("StoredEnchantments", LibraryContents.CODEC).orElse(LibraryContents.EMPTY).points());
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        builder.set(EnchantmentLibrary.CONTENTS.get(), new LibraryContents(ledger.snapshot()));
        builder.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter input) {
        super.applyImplicitComponents(input);
        ledger.replace(input.getOrDefault(EnchantmentLibrary.CONTENTS.get(), LibraryContents.EMPTY).points());
        input.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
    }

    @Override public void removeComponentsFromTag(ValueOutput output) { super.removeComponentsFromTag(output); output.discard("StoredEnchantments"); output.discard("Items"); }
    @Override public int getContainerSize() { return items.size(); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) { items.set(slot, stack); stack.limitSize(getMaxStackSize(stack)); setChanged(); }
    @Override public boolean stillValid(Player player) { return Container.stillValidBlockEntity(this, player); }
    @Override public void clearContent() { items.clear(); setChanged(); }
}
