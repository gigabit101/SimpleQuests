package io.github.flemmli97.simplequests.quest.types;

import com.mojang.serialization.MapCodec;
import io.github.flemmli97.simplequests.SimpleQuests;
import io.github.flemmli97.simplequests.api.QuestEntry;
import io.github.flemmli97.simplequests.datapack.QuestsManager;
import io.github.flemmli97.simplequests.quest.QuestCategory;
import net.minecraft.Util;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class CompositeQuest extends QuestBase {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SimpleQuests.MODID, "composite_quest");

    public static final BiFunction<Boolean, Boolean, MapCodec<CompositeQuest>> CODEC = Util.memoize((withId, full) ->
            QuestBase.buildCodec(ResourceLocation.CODEC.listOf().fieldOf("quests")
                    .forGetter(q -> q.compositeQuests), withId, full, (id, task, quests) -> {
                Builder builder = new Builder(id, task);
                quests.forEach(builder::addQuest);
                return builder;
            }));

    private final List<ResourceLocation> compositeQuests;

    protected CompositeQuest(ResourceLocation id, QuestCategory category, String questTaskString, List<String> questTaskDesc, List<ResourceLocation> parents, boolean redoParent, boolean needsUnlock,
                             ItemStack icon, int repeatDelay, int repeatDaily, int sortingId, boolean isDailyQuest, EntityPredicate unlockCondition,
                             List<ResourceLocation> compositeQuests, Visibility visibility) {
        super(id, category, questTaskString, questTaskDesc, parents, redoParent, needsUnlock, icon, repeatDelay, repeatDaily, sortingId, isDailyQuest, unlockCondition, visibility);
        this.compositeQuests = compositeQuests;
    }

    @Override
    public ResourceLocation getTypeId() {
        return ID;
    }

    public List<ResourceLocation> getCompositeQuests() {
        return this.compositeQuests;
    }

    @Override
    @Nullable
    public QuestBase resolveToQuest(ServerPlayer player, int idx) {
        if (idx < 0 || idx >= this.compositeQuests.size())
            return null;
        return QuestsManager.instance().getAllQuests().get(this.compositeQuests.get(idx));
    }

    @Override
    public ResourceKey<LootTable> getLoot() {
        return null;
    }

    @Override
    public String submissionTrigger(ServerPlayer player, int idx) {
        if (idx < 0 || idx >= this.compositeQuests.size())
            return super.submissionTrigger(player, idx);
        QuestBase base = QuestsManager.instance().getAllQuests().get(this.compositeQuests.get(idx));
        return base.submissionTrigger(player, idx);
    }

    @Override
    public Map<String, QuestEntry> resolveTasks(ServerPlayer player, int idx) {
        if (idx < 0 || idx >= this.compositeQuests.size())
            return Map.of();
        QuestBase base = QuestsManager.instance().getAllQuests().get(this.compositeQuests.get(idx));
        return base.resolveTasks(player, 0);
    }

    public static class Builder extends BuilderBase<CompositeQuest, Builder> {

        protected final List<ResourceLocation> compositeQuests = new ArrayList<>();

        public Builder(ResourceLocation id, String task) {
            super(id, task);
        }

        @Override
        protected Builder asThis() {
            return this;
        }

        public Builder addQuest(ResourceLocation quest) {
            this.compositeQuests.add(quest);
            return this;
        }

        @Override
        public CompositeQuest build() {
            CompositeQuest quest = new CompositeQuest(this.id, this.category, this.questTaskString, this.questDesc, this.neededParentQuests, this.redoParent, this.needsUnlock,
                    this.icon, this.repeatDelay, this.repeatDaily, this.sortingId, this.isDailyQuest,
                    this.unlockCondition, this.compositeQuests, this.visibility);
            quest.setDelayString(this.repeatDelayString);
            return quest;
        }
    }
}
