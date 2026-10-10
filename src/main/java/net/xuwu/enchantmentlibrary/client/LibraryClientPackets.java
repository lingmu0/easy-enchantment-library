package net.xuwu.enchantmentlibrary.client;

import net.minecraft.client.Minecraft;
import net.xuwu.enchantmentlibrary.menu.LibraryMenu;
import net.xuwu.enchantmentlibrary.network.LibraryNetwork;

/** Kept outside the common network class so dedicated servers never load Minecraft client types. */
public final class LibraryClientPackets {
    public static void accept(LibraryNetwork.Snapshot snapshot) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof LibraryMenu menu && menu.containerId == snapshot.containerId()) {
            menu.updateEntries(snapshot.entries());
        }
    }
}
