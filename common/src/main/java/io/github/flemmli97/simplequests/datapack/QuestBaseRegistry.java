package io.github.flemmli97.simplequests.datapack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import io.github.flemmli97.simplequests.quest.QuestCategory;
import io.github.flemmli97.simplequests.quest.types.CompositeQuest;
import io.github.flemmli97.simplequests.quest.types.Quest;
import io.github.flemmli97.simplequests.quest.types.QuestBase;
import io.github.flemmli97.simplequests.quest.types.SequentialQuest;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public class QuestBaseRegistry {

    private static final Map<ResourceLocation, QuestCodec> MAP = new HashMap<>();

    public static final BiFunction<Boolean, Boolean, Codec<QuestBase>> CODEC = Util.memoize((id, full) ->
            ResourceLocation.CODEC.dispatch(QuestBase.TYPE_ID, QuestBase::getTypeId, type -> MAP.get(type).apply(id, full)));

    public static void register() {
        registerSerializer(Quest.ID, Quest.CODEC::apply);
        registerSerializer(CompositeQuest.ID, CompositeQuest.CODEC::apply);
        registerSerializer(SequentialQuest.ID, SequentialQuest.CODEC::apply);
    }

    /**
     * Register a deserializer for a {@link QuestBase}
     */
    public static synchronized void registerSerializer(ResourceLocation id, QuestCodec codec) {
        if (MAP.containsKey(id))
            throw new IllegalStateException("Deserializer for " + id + " already registered");
        MAP.put(id, codec);
    }

    public static QuestBase deserialize(DynamicOps<JsonElement> ops, ResourceLocation type, ResourceLocation res, QuestCategory category, JsonObject obj) {
        QuestCodec d = MAP.get(type);
        if (d != null) {
            obj.addProperty("id", res.toString());
            obj.addProperty("category", category.id.toString());
            return CODEC.apply(false, false).parse(ops, obj).getOrThrow();
        }
        throw new IllegalStateException("Missing entry for key " + type);
    }

    public interface QuestCodec {

        MapCodec<? extends QuestBase> apply(boolean withId, boolean full);

    }
}
