package net.xuwu.enchantmentlibrary;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.xuwu.enchantmentlibrary.block.LibraryBlock;
import net.xuwu.enchantmentlibrary.block.LibraryBlockEntity;
import net.xuwu.enchantmentlibrary.menu.LibraryMenu;
import net.xuwu.enchantmentlibrary.network.LibraryNetwork;
import org.slf4j.Logger;

@Mod(EnchantmentLibrary.MOD_ID)
public final class EnchantmentLibrary {
    public static final String MOD_ID = "enchantment_library";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MOD_ID);
    public static final RegistryObject<LibraryBlock> LIBRARY = BLOCKS.register("enchantment_library",
            () -> new LibraryBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(2.5F)));
    public static final RegistryObject<BlockItem> LIBRARY_ITEM = ITEMS.register("enchantment_library",
            () -> new LibraryItem(LIBRARY.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<BlockEntityType<LibraryBlockEntity>> LIBRARY_ENTITY = BLOCK_ENTITIES.register("enchantment_library",
            () -> BlockEntityType.Builder.of(LibraryBlockEntity::new, LIBRARY.get()).build(null));
    public static final RegistryObject<MenuType<LibraryMenu>> LIBRARY_MENU = MENUS.register("enchantment_library",
            () -> IForgeMenuType.create(LibraryMenu::new));

    public EnchantmentLibrary() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        LibraryNetwork.register();
        bus.addListener(EnchantmentLibrary::creativeTab);
    }
    public static ResourceLocation id(String path) { return new ResourceLocation(MOD_ID, path); }
    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) event.accept(LIBRARY_ITEM);
    }
}
