package net.xuwu.enchantmentlibrary.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.math.BigInteger;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public record LibraryContents(Map<ResourceLocation, BigInteger> points) {
    private static final Codec<BigInteger> POINT_CODEC = Codec.STRING.comapFlatMap(value -> {
        try {
            BigInteger points = EnchantmentLedger.parse(value);
            return points.signum() > 0 ? DataResult.success(points) : DataResult.error(() -> "Points must be positive");
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }, BigInteger::toString);
    public static final Codec<LibraryContents> CODEC = Codec.unboundedMap(ResourceLocation.CODEC, POINT_CODEC)
            .xmap(LibraryContents::new, LibraryContents::points);
    public static final LibraryContents EMPTY = new LibraryContents(Map.of());
    public LibraryContents { points = Map.copyOf(points); }
}
