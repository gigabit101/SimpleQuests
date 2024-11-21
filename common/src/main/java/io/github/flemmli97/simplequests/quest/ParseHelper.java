package io.github.flemmli97.simplequests.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.JsonOps;
import io.github.flemmli97.simplequests.SimpleQuests;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParseHelper {

    private static final Pattern DATE_PATTERN = Pattern.compile("(?:(?<weeks>[0-9]{1,2})w)?" +
            "(?:(?:^|:)(?<days>[0-9])d)?" +
            "(?:(?:^|:)(?<hours>[0-9]{1,2})h)?" +
            "(?:(?:^|:)(?<minutes>[0-9]{1,2})m)?" +
            "(?:(?:^|:)(?<seconds>[0-9]{1,2})s)?");

    public static int tryParseTime(JsonObject obj, String name, int fallback) {
        JsonElement e = obj.get(name);
        if (e == null || !e.isJsonPrimitive())
            return fallback;
        if (e.getAsJsonPrimitive().isNumber())
            return e.getAsInt();
        return tryParseTime(e.getAsString(), name);
    }

    public static int tryParseTime(String time, String id) {
        Matcher matcher = DATE_PATTERN.matcher(time);
        if (!matcher.matches()) {
            throw new JsonSyntaxException("Malformed date time for " + id + ".");
        }
        int ticks = 0;
        ticks += asTicks(matcher, "weeks", 12096000);
        ticks += asTicks(matcher, "days", 1728000);
        ticks += asTicks(matcher, "hours", 72000);
        ticks += asTicks(matcher, "minutes", 1200);
        ticks += asTicks(matcher, "seconds", 20);
        return ticks;
    }

    private static int asTicks(Matcher matcher, String group, int multiplier) {
        String val = matcher.group(group);
        if (val != null) {
            try {
                return Integer.parseInt(val) * multiplier;
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    public static ItemStack icon(JsonObject obj, String name, Item fallback) {
        JsonElement element = obj.get(name);
        if (element == null)
            return new ItemStack(fallback);
        if (element.isJsonPrimitive()) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(element.getAsString())).get());
            if (stack.isEmpty())
                return new ItemStack(fallback);
            return stack;
        }
        ItemStack result = ItemStack.CODEC.parse(JsonOps.INSTANCE, element)
                .resultOrPartial(SimpleQuests.LOGGER::error).orElse(ItemStack.EMPTY);
        if (result.isEmpty())
            return new ItemStack(fallback);
        return result;
    }

    public static Optional<ItemStack> defaultChecked(ItemStack stack, Item defaultValue) {
        System.out.println(stack);
        if (stack.getCount() == 1 && stack.getComponentsPatch().isEmpty() && defaultValue != null && stack.getItem() == defaultValue)
            return Optional.empty();
        return Optional.of(stack);
    }

    public static Optional<JsonElement> writeItemStackToJson(ItemStack stack, Item defaultValue) {
        if (stack.getCount() == 1 && stack.getComponentsPatch().isEmpty())
            return defaultValue != null && stack.getItem() == defaultValue ? Optional.empty() : Optional.of(new JsonPrimitive(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
        return ItemStack.CODEC.encodeStart(JsonOps.INSTANCE, stack).resultOrPartial(SimpleQuests.LOGGER::error);
    }
}
