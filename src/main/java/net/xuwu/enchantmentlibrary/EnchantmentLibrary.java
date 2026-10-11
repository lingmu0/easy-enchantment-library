package net.xuwu.enchantmentlibrary;

import com.mojang.logging.LogUtils;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.xuwu.enchantmentlibrary.block.LibraryBlock;
import net.xuwu.enchantmentlibrary.block.LibraryBlockEntity;
import net.xuwu.enchantmentlibrary.menu.LibraryMenu;
import net.xuwu.enchantmentlibrary.network.LibraryNetwork;
import net.xuwu.enchantmentlibrary.storage.LibraryContents;
import org.slf4j.Logger;

@Mod(EnchantmentLibrary.MOD_ID)
public final class EnchantmentLibrary {
    public static final String MOD_ID = "enchantment_library";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MOD_ID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MOD_ID);

    public static final DeferredBlock<LibraryBlock> LIBRARY = BLOCKS.registerBlock("enchantment_library", LibraryBlock::new,
            properties -> properties.mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(2.5F));
    public static final DeferredItem<BlockItem> LIBRARY_ITEM = ITEMS.registerItem("enchantment_library",
            properties -> new LibraryItem(LIBRARY.get(), properties), properties -> properties.stacksTo(1).useBlockDescriptionPrefix());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LibraryBlockEntity>> LIBRARY_ENTITY =
            BLOCK_ENTITIES.register("enchantment_library", () -> new BlockEntityType<>(LibraryBlockEntity::new, LIBRARY.get()));
    public static final DeferredHolder<MenuType<?>, MenuType<LibraryMenu>> LIBRARY_MENU = MENUS.register("enchantment_library",
            () -> IMenuTypeExtension.create(LibraryMenu::new));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LibraryContents>> CONTENTS =
            COMPONENTS.register("stored_enchantments", () -> DataComponentType.<LibraryContents>builder().persistent(LibraryContents.CODEC).build());

    public EnchantmentLibrary(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        COMPONENTS.register(bus);
        bus.addListener(LibraryNetwork::register);
        bus.addListener(EnchantmentLibrary::creativeTab);
    }

    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }
    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) event.accept(LIBRARY_ITEM);
    }
}
