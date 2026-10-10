package net.xuwu.enchantmentlibrary.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.xuwu.enchantmentlibrary.EnchantmentLibrary;

@EventBusSubscriber(modid = EnchantmentLibrary.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) { event.register(EnchantmentLibrary.LIBRARY_MENU.get(), LibraryScreen::new); }
}
