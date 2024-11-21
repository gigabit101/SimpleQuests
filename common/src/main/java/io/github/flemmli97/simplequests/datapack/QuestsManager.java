package io.github.flemmli97.simplequests.datapack;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import io.github.flemmli97.simplequests.SimpleQuests;
import io.github.flemmli97.simplequests.quest.QuestCategory;
import io.github.flemmli97.simplequests.quest.types.Quest;
import io.github.flemmli97.simplequests.quest.types.QuestBase;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class QuestsManager extends SimplePreparableReloadListener<QuestsManager.ResourceResult> {

    public static final String CATEGORY_LOCATION = "simplequests_categories";
    public static final String QUEST_LOCATION = "simplequests";

    private static final int PATH_SUFFIX_LENGTH = ".json".length();

    private static final Gson GSON = new GsonBuilder().create();
    public static QuestsManager INSTANCE;

    private final HolderLookup.Provider provider;
    private Map<ResourceLocation, QuestCategory> categories;
    private Map<ResourceLocation, QuestCategory> selectableCategories;
    private List<QuestCategory> categoryView;

    private Map<ResourceLocation, QuestBase> questMap;
    private Map<QuestCategory, Map<ResourceLocation, QuestBase>> quests;

    private Map<QuestCategory, Set<Quest>> dailyQuests;

    public QuestsManager(HolderLookup.Provider provider) {
        this.provider = provider;
    }

    public static QuestsManager instance() {
        return INSTANCE;
    }

    @Override
    protected @NotNull ResourceResult prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        return new ResourceResult(this.readFiles(resourceManager, CATEGORY_LOCATION),
                this.readFiles(resourceManager, QUEST_LOCATION));
    }

    private Map<ResourceLocation, JsonElement> readFiles(ResourceManager resourceManager, String directory) {
        int i = directory.length() + 1;
        Map<ResourceLocation, JsonElement> map = Maps.newHashMap();
        resourceManager.listResources(directory, file -> file.getPath().endsWith(".json")).forEach((fileRes, resource) -> {
            String path = fileRes.getPath();
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(fileRes.getNamespace(), path.substring(i, path.length() - PATH_SUFFIX_LENGTH));
            try (BufferedReader reader = resource.openAsReader()) {
                JsonElement jsonElement = GsonHelper.fromJson(GSON, reader, JsonElement.class);
                JsonElement jsonElement2 = map.put(id, jsonElement);
                if (jsonElement2 != null) {
                    throw new IllegalStateException("Duplicate data file ignored with ID " + id);
                }
            } catch (IllegalArgumentException | IOException | JsonParseException e) {
                SimpleQuests.LOGGER.error("Couldn't parse data file {} from {}", new Object[]{id, fileRes, e});
            }
        });
        return map;
    }

    @Override
    public void apply(ResourceResult result, ResourceManager resourceManager, ProfilerFiller profiler) {
        ImmutableMap.Builder<ResourceLocation, QuestCategory> categoryBuilder = new ImmutableMap.Builder<>();
        categoryBuilder.put(QuestCategory.DEFAULT_CATEGORY.id, QuestCategory.DEFAULT_CATEGORY);
        DynamicOps<JsonElement> ops = this.provider.createSerializationContext(JsonOps.INSTANCE);
        result.categories.forEach((res, el) -> {
            if (el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                if (!obj.keySet().isEmpty()) {
                    obj.addProperty("id", res.toString());
                    categoryBuilder.put(res, QuestCategory.CODEC.apply(true).parse(ops, obj).getPartialOrThrow());
                }
            }
        });
        categoryBuilder.orderEntriesByValue(QuestCategory::compareTo);
        this.categories = categoryBuilder.build();
        this.selectableCategories = this.categories.entrySet().stream().filter(e -> e.getValue().canBeSelected)
                .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
        this.categoryView = this.categories.values().stream().toList();

        Map<QuestCategory, ImmutableMap.Builder<ResourceLocation, QuestBase>> map = new HashMap<>();
        result.quests.forEach((res, el) -> {
            if (el.isJsonObject()) {
                try {
                    JsonObject obj = el.getAsJsonObject();
                    if (!obj.keySet().isEmpty()) {
                        String cat = GsonHelper.getAsString(obj, "category", "");
                        QuestCategory questCategory = QuestCategory.DEFAULT_CATEGORY;
                        if (!cat.isEmpty()) {
                            questCategory = this.categories.get(ResourceLocation.parse(cat));
                            if (questCategory == null)
                                throw new JsonSyntaxException("Quest category of " + cat + " for quest " + res + " doesn't exist!");
                        }
                        ResourceLocation questType = ResourceLocation.parse(GsonHelper.getAsString(obj, QuestBase.TYPE_ID, Quest.ID.toString()));
                        QuestBase base = QuestBaseRegistry.deserialize(ops, questType, res, questCategory, obj);
                        map.computeIfAbsent(questCategory, c -> new ImmutableMap.Builder<>())
                                .put(res, base);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        map.forEach((category, builder) -> builder.orderEntriesByValue(QuestBase::compareTo));
        this.quests = map.entrySet().stream().collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, e -> e.getValue().build()));
        this.questMap = this.quests.values().stream().flatMap(m -> m.entrySet().stream())
                .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<QuestCategory, Set<Quest>> daily = new HashMap<>();
        this.quests.forEach((cat, q) -> daily.put(cat, q.values().stream().filter(quest -> quest.isDailyQuest && quest instanceof Quest)
                .map(quest -> (Quest) quest)
                .collect(Collectors.toSet())));
        this.dailyQuests = ImmutableMap.copyOf(daily);
    }

    public Map<ResourceLocation, QuestBase> getAllQuests() {
        return this.questMap;
    }

    public Quest getActualQuests(ResourceLocation id) {
        QuestBase base = this.questMap.get(id);
        if (base instanceof Quest quest)
            return quest;
        return null;
    }

    public Map<ResourceLocation, QuestBase> getQuestsForCategoryID(ResourceLocation res) {
        QuestCategory category = this.getQuestCategory(res);
        if (category == null)
            throw new IllegalArgumentException("No such category for " + res);
        return this.getQuestsForCategory(category);
    }

    public Map<ResourceLocation, QuestBase> getQuestsForCategory(QuestCategory category) {
        return this.quests.getOrDefault(category, Map.of());
    }

    public Set<Quest> getDailyQuests() {
        return this.getDailyQuests(QuestCategory.DEFAULT_CATEGORY);
    }

    public Set<Quest> getDailyQuests(QuestCategory category) {
        return this.dailyQuests.getOrDefault(category, Set.of());
    }

    public QuestCategory getQuestCategory(ResourceLocation res) {
        if (res.equals(QuestCategory.DEFAULT_CATEGORY.id))
            return QuestCategory.DEFAULT_CATEGORY;
        return this.categories.get(res);
    }

    public Map<ResourceLocation, QuestCategory> getSelectableCategories() {
        return this.selectableCategories;
    }

    public Map<ResourceLocation, QuestCategory> getCategories() {
        return this.categories;
    }

    public List<QuestCategory> categories() {
        return this.categoryView;
    }

    public record ResourceResult(Map<ResourceLocation, JsonElement> categories, Map<ResourceLocation, JsonElement> quests) { }
}
