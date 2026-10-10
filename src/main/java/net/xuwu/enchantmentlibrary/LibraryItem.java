package net.xuwu.enchantmentlibrary;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.xuwu.enchantmentlibrary.storage.LibraryContents;

public final class LibraryItem extends BlockItem {
    public LibraryItem(Block block, Properties properties) { super(block, properties); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        LibraryContents contents = stack.getOrDefault(EnchantmentLibrary.CONTENTS.get(), LibraryContents.EMPTY);
        tooltip.add(Component.translatable("tooltip.enchantment_library.library"));
        if (!contents.points().isEmpty()) tooltip.add(Component.translatable("tooltip.enchantment_library.stored", contents.points().size()));
    }
}
