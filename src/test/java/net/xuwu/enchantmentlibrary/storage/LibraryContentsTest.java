package net.xuwu.enchantmentlibrary.storage;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;

/** Exercises the real Minecraft component codec without starting a client or server. */
public final class LibraryContentsTest {
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
    public static void main(String[] args) {
        ResourceLocation id = ResourceLocation.parse("example:modded_enchantment");
        Map<ResourceLocation, BigInteger> values = new HashMap<>();
        values.put(id, EnchantmentLedger.cost(100));
        LibraryContents contents = new LibraryContents(values);
        values.clear();
        check(!contents.points().isEmpty(), "Component holds an immutable snapshot");
        var json = LibraryContents.CODEC.encodeStart(JsonOps.INSTANCE, contents).getOrThrow();
        check(LibraryContents.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().equals(contents), "JSON codec round trip");
        var nbt = LibraryContents.CODEC.encodeStart(NbtOps.INSTANCE, contents).getOrThrow();
        check(LibraryContents.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow().equals(contents), "NBT codec round trip");
        check(LibraryContents.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"minecraft:sharpness\":\"-1\"}")).error().isPresent(), "Negative points rejected");
        check(LibraryContents.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"minecraft:sharpness\":\"0\"}")).error().isPresent(), "Zero entries rejected");
        check(LibraryContents.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"NOT AN ID\":\"1\"}")).error().isPresent(), "Invalid registry identifiers rejected");
        check(LibraryContents.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"missing_mod:old_enchantment\":\"8\"}")).getOrThrow().points().size() == 1,
                "Preserve points for a temporarily absent enchantment");
        check(LibraryContents.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow().equals(LibraryContents.EMPTY), "Empty library round trip");
        System.out.println("Passed component codec checks: immutable snapshots, JSON/NBT round trips, malformed data, absent mods.");
    }
}
