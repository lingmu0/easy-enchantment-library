package net.xuwu.enchantmentlibrary.network;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.xuwu.enchantmentlibrary.EnchantmentLibrary;
import net.xuwu.enchantmentlibrary.menu.LibraryMenu;
import net.xuwu.enchantmentlibrary.storage.EnchantmentBatch;
import net.xuwu.enchantmentlibrary.storage.EnchantmentLedger;

public final class LibraryNetwork {
    public static final int STORE = 0, WITHDRAW = 1, ENCHANT = 2;
    private static final String PROTOCOL = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(EnchantmentLibrary.id("library"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    public record Entry(ResourceLocation id, BigInteger points, int maxLevel) {}
    public record Action(int containerId, int action, Map<ResourceLocation, Integer> levels) {
        public Action {
            levels = Map.copyOf(levels);
            if (levels.size() > EnchantmentBatch.MAX_SELECTIONS) throw new IllegalArgumentException("Too many selected enchantments");
        }
        public static Action decode(FriendlyByteBuf buf) {
            int containerId = buf.readVarInt(), action = buf.readVarInt(), count = buf.readVarInt();
            if (count < 0 || count > EnchantmentBatch.MAX_SELECTIONS) throw new IllegalArgumentException("Invalid selection size");
            Map<ResourceLocation, Integer> levels = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                if (levels.put(buf.readResourceLocation(), buf.readVarInt()) != null) throw new IllegalArgumentException("Duplicate selection");
            }
            return new Action(containerId, action, levels);
        }
        public static void encode(Action value, FriendlyByteBuf buf) {
            buf.writeVarInt(value.containerId); buf.writeVarInt(value.action); buf.writeVarInt(value.levels.size());
            value.levels.forEach((id, level) -> { buf.writeResourceLocation(id); buf.writeVarInt(level); });
        }
        public static void handle(Action value, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null && player.containerMenu instanceof LibraryMenu menu && menu.containerId == value.containerId) {
                    menu.perform(value.action, value.levels);
                }
            });
            context.setPacketHandled(true);
        }
    }
    public record Snapshot(int containerId, List<Entry> entries) {
        public Snapshot { entries = List.copyOf(entries); }
        public static Snapshot decode(FriendlyByteBuf buf) {
            int containerId = buf.readVarInt(), count = buf.readVarInt();
            if (count < 0 || count > 16384) throw new IllegalArgumentException("Invalid library size");
            List<Entry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                entries.add(new Entry(buf.readResourceLocation(), EnchantmentLedger.parse(buf.readUtf(EnchantmentLedger.MAX_DIGITS)), buf.readVarInt()));
            }
            return new Snapshot(containerId, entries);
        }
        public static void encode(Snapshot value, FriendlyByteBuf buf) {
            buf.writeVarInt(value.containerId); buf.writeVarInt(value.entries.size());
            for (Entry entry : value.entries) {
                buf.writeResourceLocation(entry.id()); buf.writeUtf(entry.points().toString(), EnchantmentLedger.MAX_DIGITS); buf.writeVarInt(entry.maxLevel());
            }
        }
        public static void handle(Snapshot value, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> net.xuwu.enchantmentlibrary.client.LibraryClientPackets.accept(value)));
            context.setPacketHandled(true);
        }
    }
    public static void register() {
        CHANNEL.registerMessage(0, Action.class, Action::encode, Action::decode, Action::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Snapshot.class, Snapshot::encode, Snapshot::decode, Snapshot::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void sendToServer(Action action) { CHANNEL.sendToServer(action); }
    public static void sendToPlayer(ServerPlayer player, Snapshot snapshot) { CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), snapshot); }
}
