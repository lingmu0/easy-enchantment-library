package net.xuwu.enchantmentlibrary.client;

import java.lang.reflect.Method;
import java.util.Locale;
import net.neoforged.fml.ModList;
import net.xuwu.enchantmentlibrary.EnchantmentLibrary;

/** Optional JECh integration; no linking to client search mods on a dedicated server. */
public final class PinyinSearch {
    private static Method contains;
    private static boolean initialized;

    private static void initialize() {
        if (initialized) return;
        initialized = true;
        if (!ModList.get().isLoaded("jecharacters")) return;
        try {
            contains = Class.forName("me.towdium.jecharacters.utils.Match").getMethod("contains", String.class, CharSequence.class);
        } catch (ReflectiveOperationException | LinkageError exception) {
            EnchantmentLibrary.LOGGER.warn("JECh search integration unavailable; using text search", exception);
        }
    }

    public static boolean available() { initialize(); return contains != null; }

    public static boolean matches(String name, String id, String query) {
        initialize();
        String normalizedName = name.toLowerCase(Locale.ROOT);
        String normalizedId = id.toLowerCase(Locale.ROOT);
        for (String token : query.toLowerCase(Locale.ROOT).strip().split("\\s+")) {
            if (token.isEmpty() || normalizedName.contains(token) || normalizedId.contains(token)) continue;
            if (contains == null) return false;
            try {
                if (!Boolean.TRUE.equals(contains.invoke(null, normalizedName, token))) return false;
            } catch (ReflectiveOperationException | LinkageError exception) {
                contains = null;
                EnchantmentLibrary.LOGGER.warn("JECh matching failed; using text search", exception);
                return false;
            }
        }
        return true;
    }
}
