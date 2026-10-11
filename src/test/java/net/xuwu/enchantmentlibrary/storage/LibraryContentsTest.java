package net.xuwu.enchantmentlibrary.storage;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;

/** Exercises the real Minecraft component codec without starting a client or server. */
public final class LibraryContentsTest {
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
    public static void main(String[] args) {
        Identifier id = Identifier.parse("example:modded_enchantment");
        Map<Identifier, BigInteger> values = new HashMap<>();
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
        var problems = new ProblemReporter.Collector();
        var registries = HolderLookup.Provider.create(Stream.empty());
        var output = TagValueOutput.createWithoutContext(problems);
        output.store("StoredEnchantments", LibraryContents.CODEC, contents);
        var saved = output.buildResult();
        var input = TagValueInput.create(problems, registries, saved);
        check(input.read("StoredEnchantments", LibraryContents.CODEC).orElseThrow().equals(contents), "26.1.2 ValueInput/ValueOutput round trip");
        // The 1.21.1 block entity wrote the same ID -> decimal string compound.
        var legacy = new CompoundTag();
        var legacyPoints = new CompoundTag();
        legacyPoints.putString(id.toString(), EnchantmentLedger.cost(100).toString());
        legacy.put("StoredEnchantments", legacyPoints);
        check(TagValueInput.create(problems, registries, legacy).read("StoredEnchantments", LibraryContents.CODEC)
                .orElseThrow().equals(contents), "Read the previous block entity storage format");
        check(TagValueInput.create(problems, registries, new CompoundTag()).read("StoredEnchantments", LibraryContents.CODEC)
                .orElse(LibraryContents.EMPTY).equals(LibraryContents.EMPTY), "Missing storage starts empty");
        output.discard("StoredEnchantments");
        check(!output.buildResult().contains("StoredEnchantments"), "Implicit component storage removed from copied block NBT");
        check(problems.isEmpty(), "No serialization problems: " + problems.getReport());
        System.out.println("Passed component codec checks: immutable snapshots, JSON/NBT round trips, malformed data, absent mods, ValueInput/ValueOutput and legacy block storage.");
    }
}
