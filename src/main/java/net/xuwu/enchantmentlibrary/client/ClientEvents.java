package net.xuwu.enchantmentlibrary.client;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.xuwu.enchantmentlibrary.EnchantmentLibrary;

@Mod.EventBusSubscriber(modid = EnchantmentLibrary.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientEvents {
    @SubscribeEvent public static void registerScreens(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(EnchantmentLibrary.LIBRARY_MENU.get(), LibraryScreen::new));
    }
}
