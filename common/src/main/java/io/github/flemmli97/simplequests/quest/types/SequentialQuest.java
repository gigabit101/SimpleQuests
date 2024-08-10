package io.github.flemmli97.simplequests.quest.types;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.flemmli97.simplequests.SimpleQuests;
import io.github.flemmli97.simplequests.api.QuestEntry;
import io.github.flemmli97.simplequests.datapack.QuestsManager;
import io.github.flemmli97.simplequests.quest.QuestCategory;
import net.minecraft.Util;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

public class SequentialQuest extends QuestBase {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SimpleQuests.MODID, "sequential_quest");

    public static final BiFunction<Boolean, Boolean, MapCodec<SequentialQuest>> CODEC = Util.memoize((withId, full) ->
            QuestBase.buildCodec(SequentialQuest.QuestData.CODEC
                    .forGetter(q -> new SequentialQuest.QuestData(q.quests, q.loot.location(),
                            q.command.isEmpty() || full ? Optional.of(q.command) : Optional.empty())), withId, full, (id, task, data) -> {
                SequentialQuest.Builder builder = new SequentialQuest.Builder(id, task, data.loot);
                data.quests.forEach(builder::addQuest);
                data.command.ifPresent(builder::withCommand);
                return builder;
            }));

    private final List<ResourceLocation> quests;

    private final ResourceKey<LootTable> loot;
    private final String command;

    protected SequentialQuest(ResourceLocation id, QuestCategory category, String questTaskString, List<String> questTaskDesc, List<ResourceLocation> parents, boolean redoParent, boolean needsUnlock,
                              ItemStack icon, int repeatDelay, int repeatDaily, int sortingId, boolean isDailyQuest, EntityPredicate unlockCondition,
                              List<ResourceLocation> compositeQuests, ResourceLocation loot, String command, Visibility visibility) {
        super(id, category, questTaskString, questTaskDesc, parents, redoParent, needsUnlock, icon, repeatDelay, repeatDaily, sortingId, isDailyQuest, unlockCondition, visibility);
        this.quests = compositeQuests;
        this.loot = ResourceKey.create(Registries.LOOT_TABLE, loot);
        this.command = command;
    }

    @Override
    public ResourceLocation getTypeId() {
        return ID;
    }

    public List<ResourceLocation> getQuests() {
        return this.quests;
    }

    @Override
    public String submissionTrigger(ServerPlayer player, int idx) {
        if (idx < 0 || idx >= this.quests.size())
            return super.submissionTrigger(player, idx);
        QuestBase base = QuestsManager.instance().getAllQuests().get(this.quests.get(idx));
        return base.submissionTrigger(player, idx);
    }

    @Override
    @Nullable
    public QuestBase resolveToQuest(ServerPlayer player, int idx) {
        if (idx < 0 || idx >= this.quests.size())
            return null;
        return QuestsManager.instance().getAllQuests().get(this.quests.get(idx));
    }

    @Override
    public ResourceKey<LootTable> getLoot() {
        return this.loot;
    }

    @Override
    public void onComplete(ServerPlayer player) {
        super.onComplete(player);
        QuestBase.runCommand(player, this.command);
    }

    @Override
    public Map<String, QuestEntry> resolveTasks(ServerPlayer player, int idx) {
        if (idx < 0 || idx >= this.quests.size())
            return Map.of();
        QuestBase base = QuestsManager.instance().getAllQuests().get(this.quests.get(idx));
        return base.resolveTasks(player, 0);
    }

    public static class Builder extends BuilderBase<SequentialQuest, Builder> {

        protected final List<ResourceLocation> compositeQuests = new ArrayList<>();
        protected final ResourceLocation loot;
        protected String command = "";

        public Builder(ResourceLocation id, String task, ResourceLocation loot) {
            super(id, task);
            this.loot = loot;
        }

        @Override
        protected Builder asThis() {
            return this;
        }

        public Builder addQuest(ResourceLocation quest) {
            this.compositeQuests.add(quest);
            return this;
        }

        public Builder withCommand(String command) {
            this.command = command;
            return this;
        }

        @Override
        public SequentialQuest build() {
            SequentialQuest quest = new SequentialQuest(this.id, this.category, this.questTaskString, this.questDesc, this.neededParentQuests, this.redoParent, this.needsUnlock,
                    this.icon, this.repeatDelay, this.repeatDaily, this.sortingId, this.isDailyQuest,
                    this.unlockCondition, this.compositeQuests, this.loot, this.command, this.visibility);
            quest.setDelayString(this.repeatDelayString);
            return quest;
        }
    }

    private record QuestData(List<ResourceLocation> quests, ResourceLocation loot, Optional<String> command) {
        static final MapCodec<SequentialQuest.QuestData> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                        ResourceLocation.CODEC.listOf().fieldOf("quests").forGetter(d -> d.quests),
                        ResourceLocation.CODEC.fieldOf("loot_table").forGetter(d -> d.loot),
                        Codec.STRING.optionalFieldOf("command").forGetter(d -> d.command)
                ).apply(inst, SequentialQuest.QuestData::new)
        );
    }
}
