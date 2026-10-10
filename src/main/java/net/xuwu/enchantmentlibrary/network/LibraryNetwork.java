package net.xuwu.enchantmentlibrary.network;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.xuwu.enchantmentlibrary.EnchantmentLibrary;
import net.xuwu.enchantmentlibrary.menu.LibraryMenu;
import net.xuwu.enchantmentlibrary.storage.EnchantmentLedger;
import net.xuwu.enchantmentlibrary.storage.EnchantmentBatch;

public final class LibraryNetwork {
    public static final int STORE = 0, WITHDRAW = 1, ENCHANT = 2;

    public record Entry(ResourceLocation id, BigInteger points, int maxLevel) {}

    public record Action(int containerId, int action, Map<ResourceLocation, Integer> levels) implements CustomPacketPayload {
        public Action {
            levels = Map.copyOf(levels);
            if (levels.size() > EnchantmentBatch.MAX_SELECTIONS) throw new IllegalArgumentException("Too many selected enchantments");
        }
        public static final Type<Action> TYPE = new Type<>(EnchantmentLibrary.id("action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = new StreamCodec<>() {
            @Override public Action decode(RegistryFriendlyByteBuf buf) {
                int containerId = buf.readVarInt();
                int action = buf.readVarInt();
                int count = buf.readVarInt();
                if (count < 0 || count > EnchantmentBatch.MAX_SELECTIONS) throw new IllegalArgumentException("Invalid selection size");
                Map<ResourceLocation, Integer> levels = new LinkedHashMap<>();
                for (int i = 0; i < count; i++) {
                    ResourceLocation id = buf.readResourceLocation();
                    if (levels.put(id, buf.readVarInt()) != null) throw new IllegalArgumentException("Duplicate enchantment selection");
                }
                return new Action(containerId, action, levels);
            }
            @Override public void encode(RegistryFriendlyByteBuf buf, Action value) {
                buf.writeVarInt(value.containerId); buf.writeVarInt(value.action); buf.writeVarInt(value.levels.size());
                value.levels.forEach((id, level) -> { buf.writeResourceLocation(id); buf.writeVarInt(level); });
            }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Snapshot(int containerId, List<Entry> entries) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(EnchantmentLibrary.id("snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = new StreamCodec<>() {
            @Override public Snapshot decode(RegistryFriendlyByteBuf buf) {
                int containerId = buf.readVarInt();
                int size = buf.readVarInt();
                if (size < 0 || size > 16384) throw new IllegalArgumentException("Invalid library size");
                List<Entry> entries = new ArrayList<>(size);
                for (int i = 0; i < size; i++) entries.add(new Entry(buf.readResourceLocation(), EnchantmentLedger.parse(buf.readUtf(EnchantmentLedger.MAX_DIGITS)), buf.readVarInt()));
                return new Snapshot(containerId, List.copyOf(entries));
            }
            @Override public void encode(RegistryFriendlyByteBuf buf, Snapshot value) {
                buf.writeVarInt(value.containerId); buf.writeVarInt(value.entries.size());
                for (Entry entry : value.entries) {
                    buf.writeResourceLocation(entry.id()); buf.writeUtf(entry.points().toString(), EnchantmentLedger.MAX_DIGITS); buf.writeVarInt(entry.maxLevel());
                }
            }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToServer(Action.TYPE, Action.CODEC, (payload, context) -> {
            if (context.player().containerMenu instanceof LibraryMenu menu && menu.containerId == payload.containerId()) {
                menu.perform(payload.action(), payload.levels());
            }
        });
        registrar.playToClient(Snapshot.TYPE, Snapshot.CODEC, (payload, context) -> {
            if (context.player().containerMenu instanceof LibraryMenu menu && menu.containerId == payload.containerId()) menu.updateEntries(payload.entries());
        });
    }
}
