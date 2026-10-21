package dev.enchantingoverhaul.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Bundled tuning, deliberately not a runtime-reloadable datapack. */
public record ExtractionCosts(List<Integer> extractionXp, int purificationXp) {
    public static final String RESOURCE = "/data/enchantingoverhaul/extraction.json";
    public ExtractionCosts {
        extractionXp = List.copyOf(extractionXp);
        if (extractionXp.size() != 5 || purificationXp < 1 || purificationXp > 32767
                || extractionXp.stream().anyMatch(x -> x < 1 || x > 32767))
            throw new IllegalArgumentException("Expected five positive menu-safe XP fees");
        for (int i = 1; i < extractionXp.size(); i++)
            if (extractionXp.get(i) < extractionXp.get(i - 1)) throw new IllegalArgumentException("Decreasing XP schedule");
    }
    public int extraction(int level) {
        if (level < 1 || level > extractionXp.size()) throw new IllegalArgumentException("Unsupported extraction level");
        return extractionXp.get(level - 1);
    }
    public static ExtractionCosts loadDefault() {
        var input = ExtractionCosts.class.getResourceAsStream(RESOURCE);
        if (input == null) throw new IllegalStateException("Missing " + RESOURCE);
        try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return load(reader);
        } catch (IOException e) { throw new IllegalStateException("Cannot read " + RESOURCE, e); }
    }
    public static ExtractionCosts load(Reader reader) {
        try {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            if (integer(root.get("schemaVersion")) != 1) throw new IllegalArgumentException("Unknown schema");
            List<Integer> fees = new ArrayList<>();
            for (var fee : root.getAsJsonArray("extractionXp")) fees.add(integer(fee));
            return new ExtractionCosts(fees, integer(root.get("purificationXp")));
        } catch (RuntimeException e) { throw new IllegalArgumentException("Invalid extraction tuning", e); }
    }
    private static int integer(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Expected integer");
        return value.getAsBigDecimal().intValueExact();
    }
}
