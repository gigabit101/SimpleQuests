package io.github.flemmli97.simplequests.datapack;

import com.google.gson.JsonObject;
import io.github.flemmli97.simplequests.api.QuestEntry;
import io.github.flemmli97.simplequests.quest.FuzzyQuestEntryImpls;
import io.github.flemmli97.simplequests.quest.QuestEntryImpls;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public class QuestEntryRegistry {

    private static final Map<ResourceLocation, QuestEntry.Deserializer<?>> MAP = new HashMap<>();

    public static void register() {
        registerSerializer(QuestEntryImpls.ItemEntry.ID, QuestEntryImpls.ItemEntry::fromJson);
        registerSerializer(QuestEntryImpls.XPEntry.ID, QuestEntryImpls.XPEntry::fromJson);
        registerSerializer(QuestEntryImpls.AdvancementEntry.ID, QuestEntryImpls.AdvancementEntry::fromJson);
        registerSerializer(QuestEntryImpls.KillEntry.ID, QuestEntryImpls.KillEntry::fromJson);
        registerSerializer(QuestEntryImpls.PositionEntry.ID, QuestEntryImpls.PositionEntry::fromJson);
        registerSerializer(QuestEntryImpls.LocationEntry.ID, QuestEntryImpls.LocationEntry::fromJson);
        registerSerializer(QuestEntryImpls.EntityInteractEntry.ID, QuestEntryImpls.EntityInteractEntry::fromJson);
        registerSerializer(QuestEntryImpls.BlockInteractEntry.ID, QuestEntryImpls.BlockInteractEntry::fromJson);
        registerSerializer(QuestEntryImpls.CraftingEntry.ID, QuestEntryImpls.CraftingEntry::fromJson);

        registerSerializer(FuzzyQuestEntryImpls.FuzzyItemEntry.ID, FuzzyQuestEntryImpls.FuzzyItemEntry::fromJson);
    }

    /**
     * Register a deserializer for a {@link QuestEntry}
     */
    public static synchronized void registerSerializer(ResourceLocation id, QuestEntry.Deserializer<?> deserializer) {
        if (MAP.containsKey(id))
            throw new IllegalStateException("Deserializer for " + id + " already registered");
        MAP.put(id, deserializer);
    }

    public static QuestEntry deserialize(ResourceLocation res, JsonObject obj) {
        QuestEntry.Deserializer<?> d = MAP.get(res);
        if (d != null)
            return d.deserialize(obj);
        throw new IllegalStateException("Missing entry for key " + res);
    }
}
