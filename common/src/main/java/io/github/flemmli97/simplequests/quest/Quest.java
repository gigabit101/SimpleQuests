package io.github.flemmli97.simplequests.quest;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import io.github.flemmli97.simplequests.SimpleQuests;
import io.github.flemmli97.simplequests.datapack.QuestEntryRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Quest implements Comparable<Quest> {

    private static final Pattern DATE_PATTERN = Pattern.compile("(?:(?<weeks>[0-9]{1,2})w)?" +
            "(?:(?:^|:)(?<days>[0-9])d)?" +
            "(?:(?:^|:)(?<hours>[0-9]{1,2})h)?" +
            "(?:(?:^|:)(?<minutes>[0-9]{1,2})m)?" +
            "(?:(?:^|:)(?<seconds>[0-9]{1,2})s)?");

    public final Map<String, QuestEntry> entries;

    public final ResourceLocation id;
    public final ResourceLocation neededParentQuest;
    public final ResourceLocation loot;

    public final int repeatDelay, repeatDaily;

    public final String questTaskString;

    public final boolean redoParent;

    public final int sortingId;

    public final ItemStack icon;

    private Quest(ResourceLocation id, String questTaskString, ResourceLocation parent, boolean redoParent, ResourceLocation loot, ItemStack icon, int repeatDelay, int repeatDaily, int sortingId, Map<String, QuestEntry> entries) {
        this.id = id;
        this.questTaskString = questTaskString;
        this.neededParentQuest = parent;
        this.redoParent = redoParent;
        this.repeatDelay = repeatDelay;
        this.repeatDaily = repeatDaily;
        this.entries = entries;
        this.loot = loot;
        this.sortingId = sortingId;
        this.icon = icon;
    }

    public MutableComponent getFormatted(MinecraftServer server, ChatFormatting... subFormatting) {
        MutableComponent main = Component.literal("").append(Component.literal(this.questTaskString).withStyle(ChatFormatting.LIGHT_PURPLE));
        for (MutableComponent tasks : this.getFormattedTasks(server)) {
            if (subFormatting != null)
                main.append("\n").append(tasks.withStyle(subFormatting));
            else
                main.append("\n").append(tasks);
        }
        return main;
    }

    public List<MutableComponent> getFormattedTasks(MinecraftServer server) {
        List<MutableComponent> list = new ArrayList<>();
        for (Map.Entry<String, QuestEntry> e : this.entries.entrySet()) {
            list.add(Component.literal(" - ").append(e.getValue().translation(server)));
        }
        return list;
    }

    public List<MutableComponent> getFormattedGuiTasks(ServerPlayer player) {
        List<MutableComponent> list = new ArrayList<>();
        for (Map.Entry<String, QuestEntry> e : this.entries.entrySet()) {
            if (!(e.getValue() instanceof QuestEntryImpls.ItemEntry ing))
                list.add(Component.literal(" - ").append(e.getValue().translation(player.getServer())));
            else {
                List<MutableComponent> wrapped = SimpleQuests.getHandler().wrapForGui(player, ing);
                boolean start = true;
                for (MutableComponent comp : wrapped) {
                    if (start) {
                        list.add(Component.literal(" - ").append(comp));
                        start = false;
                    } else
                        list.add(Component.literal("   ").append(comp));
                }
            }
        }
        return list;
    }

    public JsonObject serialize() {
        JsonObject obj = new JsonObject();
        if (this.neededParentQuest != null)
            obj.addProperty("parent_id", this.neededParentQuest.toString());
        obj.addProperty("redo_parent", this.redoParent);
        obj.addProperty("loot_table", this.loot.toString());
        obj.addProperty("repeat_delay", this.repeatDelay);
        obj.addProperty("repeat_daily", this.repeatDaily);
        obj.addProperty("task", this.questTaskString);
        JsonObject entries = new JsonObject();
        this.entries.forEach((res, entry) -> {
            JsonObject val = entry.serialize();
            val.addProperty("id", entry.getId().toString());
            entries.add(res, val);
        });
        obj.add("entries", entries);
        return obj;
    }

    public static Quest of(ResourceLocation id, JsonObject obj) {
        ImmutableMap.Builder<String, QuestEntry> builder = new ImmutableMap.Builder<>();
        JsonObject entries = GsonHelper.getAsJsonObject(obj, "entries");
        entries.entrySet().forEach(ent -> {
            if (!ent.getValue().isJsonObject())
                throw new JsonSyntaxException("Expected JsonObject for " + ent.getKey() + " but was " + ent.getValue());
            ResourceLocation entryID = new ResourceLocation(GsonHelper.getAsString(ent.getValue().getAsJsonObject(), "id"));
            builder.put(ent.getKey(), QuestEntryRegistry.deserialize(entryID, ent.getValue().getAsJsonObject()));
        });
        return new Quest(id,
                GsonHelper.getAsString(obj, "task"),
                obj.has("parent_id") && !GsonHelper.getAsString(obj, "parent_id").isEmpty() ? new ResourceLocation(GsonHelper.getAsString(obj, "parent_id")) : null,
                GsonHelper.getAsBoolean(obj, "redo_parent", false),
                new ResourceLocation(GsonHelper.getAsString(obj, "loot_table")),
                questIcon(obj, "icon", Items.PAPER),
                tryParseTime(obj, "repeat_delay", 0),
                GsonHelper.getAsInt(obj, "repeat_daily", 1),
                GsonHelper.getAsInt(obj, "sorting_id", 0),
                builder.build());
    }

    private static int tryParseTime(JsonObject obj, String name, int fallback) {
        JsonElement e = obj.get(name);
        if (e == null || !e.isJsonPrimitive())
            return fallback;
        if (e.getAsJsonPrimitive().isNumber())
            return e.getAsInt();
        String s = e.getAsString();
        Matcher matcher = DATE_PATTERN.matcher(s);
        if (!matcher.matches()) {
            throw new JsonSyntaxException("Malformed date time for " + name + ".");
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

    public static ItemStack questIcon(JsonObject obj, String name, Item fallback) {
        JsonElement element = obj.get(name);
        if (element == null)
            return new ItemStack(fallback);
        if (element.isJsonPrimitive()) {
            ItemStack stack = new ItemStack(SimpleQuests.getHandler().fromID(new ResourceLocation(element.getAsString())));
            if (stack.isEmpty())
                return new ItemStack(fallback);
            return stack;
        }
        if (element.isJsonObject())
            return ShapedRecipe.itemStackFromJson(element.getAsJsonObject());
        return new ItemStack(fallback);
    }

    @Override
    public int compareTo(@NotNull Quest quest) {
        if (this.sortingId == quest.sortingId) {
            if (this.neededParentQuest == null && quest.neededParentQuest != null)
                return -1;
            if (this.neededParentQuest != null && quest.neededParentQuest == null)
                return 1;
            return this.id.compareTo(quest.id);
        }
        return Integer.compare(this.sortingId, quest.sortingId);
    }
}
