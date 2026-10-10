package net.xuwu.enchantmentlibrary;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

public final class LibraryItem extends BlockItem {
    public LibraryItem(Block block, Properties properties) { super(block, properties); }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        CompoundTag contents = BlockItem.getBlockEntityData(stack);
        tooltip.add(Component.translatable("tooltip.enchantment_library.library"));
        if (contents != null && !contents.getCompound("StoredEnchantments").isEmpty()) tooltip.add(Component.translatable("tooltip.enchantment_library.stored", contents.getCompound("StoredEnchantments").size()));
    }
}
