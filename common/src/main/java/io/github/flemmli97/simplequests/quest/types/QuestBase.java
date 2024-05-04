package io.github.flemmli97.simplequests.quest.types;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.flemmli97.simplequests.CodecHelper;
import io.github.flemmli97.simplequests.api.QuestEntry;
import io.github.flemmli97.simplequests.datapack.QuestsManager;
import io.github.flemmli97.simplequests.quest.ParseHelper;
import io.github.flemmli97.simplequests.quest.QuestCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class QuestBase implements Comparable<QuestBase> {

    public static final String TYPE_ID = "type";

    public final ResourceLocation id;
    public final QuestCategory category;
    public final List<ResourceLocation> neededParentQuests;

    public final int repeatDelay, repeatDaily;

    protected final String questTaskString;
    protected final List<String> questTaskDesc;

    public final boolean redoParent, needsUnlock, isDailyQuest;

    public final int sortingId;

    private final ItemStack icon;

    String repeatDelayString;

    protected final EntityPredicate unlockCondition;

    public final Visibility visibility;

    public QuestBase(ResourceLocation id, QuestCategory category, String questTaskString, List<String> questTaskDesc, List<ResourceLocation> parents, boolean redoParent, boolean needsUnlock,
                     ItemStack icon, int repeatDelay, int repeatDaily, int sortingId,
                     boolean isDailyQuest, EntityPredicate unlockCondition, Visibility visibility) {
        this.id = id;
        this.category = category == null ? QuestCategory.DEFAULT_CATEGORY : category;
        this.questTaskString = questTaskString;
        this.questTaskDesc = questTaskDesc;
        this.neededParentQuests = parents;
        this.redoParent = redoParent;
        this.needsUnlock = needsUnlock;
        this.repeatDelay = repeatDelay;
        this.repeatDaily = repeatDaily;
        this.sortingId = sortingId;
        this.icon = icon;
        this.isDailyQuest = isDailyQuest;
        this.unlockCondition = unlockCondition;
        this.visibility = visibility;
    }

    public static List<MutableComponent> getFormattedTasks(ServerPlayer player, Map<String, QuestEntry> resolvedTasks) {
        List<MutableComponent> list = new ArrayList<>();
        for (Map.Entry<String, QuestEntry> e : resolvedTasks.entrySet()) {
            list.add(Component.literal(" - ").append(e.getValue().translation(player)));
        }
        return list;
    }

    public static void runCommand(ServerPlayer player, String command) {
        if (!command.isEmpty())
            player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4), command);
    }

    public static <T extends QuestBase, R, B extends BuilderBase<T, B>> MapCodec<T> buildCodec(RecordCodecBuilder<T, R> codec, boolean withId, boolean full, QuestBuildFactory<R, B> fact) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.BOOL.optionalFieldOf("daily_quest").forGetter(q -> q.isDailyQuest || full ? Optional.of(q.isDailyQuest) : Optional.empty()),
                        Codec.STRING.optionalFieldOf("visibility")
                                .xmap(opt -> opt.map(Visibility::valueOf).orElse(Visibility.DEFAULT), vis -> vis != Visibility.DEFAULT || full ? Optional.of(vis.toString()) : Optional.empty()).forGetter(q -> q.visibility),
                        codec,

                        CodecHelper.ITEM_STACK_CODEC.optionalFieldOf("icon").forGetter(q -> ParseHelper.defaultChecked(q.getIcon(), full ? null : Items.PAPER)),
                        Codec.either(Codec.INT, Codec.STRING).optionalFieldOf("repeat_delay")
                                .forGetter(q -> {
                                    if (q.repeatDelayString != null)
                                        return Optional.of(Either.right(q.repeatDelayString));
                                    return q.repeatDelay != 0 || full ? Optional.of(Either.left(q.repeatDelay)) : Optional.empty();
                                }),
                        Codec.INT.optionalFieldOf("repeat_daily").forGetter(q -> q.repeatDaily != 0 || full ? Optional.of(q.repeatDaily) : Optional.empty()),
                        Codec.INT.optionalFieldOf("sorting_id").forGetter(q -> q.sortingId != 0 || full ? Optional.of(q.sortingId) : Optional.empty()),

                        CodecHelper.listOrInline(ResourceLocation.CODEC).optionalFieldOf("parent_id").forGetter(q -> q.neededParentQuests.isEmpty() || full ? Optional.of(q.neededParentQuests) : Optional.empty()),
                        Codec.BOOL.optionalFieldOf("redo_parent").forGetter(q -> q.redoParent || full ? Optional.of(q.redoParent) : Optional.empty()),
                        Codec.BOOL.optionalFieldOf("need_unlock").forGetter(q -> q.needsUnlock || full ? Optional.of(q.needsUnlock) : Optional.empty()),
                        EntityPredicate.CODEC.optionalFieldOf("unlock_condition").forGetter(q -> Optional.ofNullable(q.unlockCondition)),

                        ResourceLocation.CODEC.optionalFieldOf("id").forGetter(q -> withId ? Optional.of(q.id) : Optional.empty()),
                        ResourceLocation.CODEC.optionalFieldOf("category").forGetter(q -> q.category != QuestCategory.DEFAULT_CATEGORY ? Optional.of(q.category.id) : Optional.empty()),
                        Codec.STRING.fieldOf("task").forGetter(q -> q.questTaskString),
                        CodecHelper.listOrInline(Codec.STRING).optionalFieldOf("description").forGetter(q -> q.questTaskDesc.isEmpty() || full ? Optional.of(q.questTaskDesc) : Optional.empty())
                ).apply(instance, (isDaily, visibility, r, icon, repeatDelay, daily, sort, parent, redo_parent, unlock, unlockCondition, id, cat, task, desc) -> {
                    B builder = fact.create(id.orElseThrow(), task, r);
                    builder.withCategory(cat.map(c -> QuestsManager.instance().getQuestCategory(c)).orElse(QuestCategory.DEFAULT_CATEGORY));
                    desc.orElse(List.of())
                            .forEach(builder::addDescription);
                    parent.orElse(List.of())
                            .forEach(builder::addParent);
                    if (redo_parent.orElse(false))
                        builder.setRedoParent();
                    if (unlock.orElse(false))
                        builder.needsUnlocking();
                    builder.withIcon(icon.orElse(new ItemStack(Items.PAPER)));
                    repeatDelay.ifPresent(d -> {
                        d.ifLeft(builder::setRepeatDelay);
                        d.ifRight(builder::setRepeatDelay);
                    });
                    daily.ifPresent(builder::setMaxDaily);
                    sort.ifPresent(builder::withSortingNum);
                    if (isDaily.orElse(false))
                        builder.setDailyQuest();
                    unlockCondition.ifPresent(builder::withUnlockCondition);
                    builder.setVisibility(visibility);
                    return builder.build();
                })
        );
    }

    public abstract ResourceLocation getTypeId();

    public boolean isUnlocked(ServerPlayer player) {
        return this.unlockCondition == null || this.unlockCondition.matches(player, player);
    }

    public final MutableComponent getTask(ServerPlayer player) {
        return this.getTask(player, -1);
    }

    /**
     * The quest task to do. Delegates to subquests if possible
     *
     * @param idx If -1 should return itself
     */
    public MutableComponent getTask(ServerPlayer player, int idx) {
        QuestBase resolved = this.resolveToQuest(player, idx);
        if (resolved == null)
            return Component.translatable(this.questTaskString);
        return resolved.getTask(player);
    }

    public final List<MutableComponent> getDescription(ServerPlayer player) {
        return this.getDescription(player, -1);
    }

    /**
     * The quest description. Delegates to subquests if possible
     *
     * @param idx If -1 should return itself
     */
    public List<MutableComponent> getDescription(ServerPlayer player, int idx) {
        QuestBase resolved = this.resolveToQuest(player, idx);
        if (resolved == null)
            return this.questTaskDesc.stream().map(s -> Component.translatable(s).withStyle(ChatFormatting.DARK_GREEN)).collect(Collectors.toList());
        return resolved.getDescription(player);
    }

    /**
     * The formatted quest with the given tasks. Delegates to subquests if possible
     *
     * @param idx If -1 should return itself
     */
    public MutableComponent getFormattedWith(ServerPlayer player, int idx, Map<String, QuestEntry> resolvedTasks, ChatFormatting... subFormatting) {
        QuestBase resolved = this.resolveToQuest(player, idx);
        if (resolved != null)
            return this.getFormattedWith(player, -1, resolvedTasks, subFormatting);
        MutableComponent main = this.getTask(player, idx).withStyle(ChatFormatting.LIGHT_PURPLE);
        for (MutableComponent tasks : getFormattedTasks(player, resolvedTasks)) {
            if (subFormatting != null)
                main.append("\n").append(tasks.withStyle(subFormatting));
            else
                main.append("\n").append(tasks);
        }
        return main;
    }

    public List<MutableComponent> getFormattedGuiTasks(ServerPlayer player) {
        return List.of();
    }

    public ItemStack getIcon() {
        return this.icon.copy();
    }

    /**
     * For datageneration
     */
    protected void setDelayString(String repeatDelayString) {
        this.repeatDelayString = repeatDelayString;
    }

    /**
     * The trigger required to complete this quest
     */
    public String submissionTrigger(ServerPlayer player, int idx) {
        return "";
    }

    @Nullable
    public abstract QuestBase resolveToQuest(ServerPlayer player, int idx);

    public abstract ResourceKey<LootTable> getLoot();

    public void onComplete(ServerPlayer player) {
        ResourceKey<LootTable> lootID = this.getLoot();
        if (lootID != null) {
            LootTable lootTable = player.getServer().reloadableRegistries().getLootTable(lootID);
            CriteriaTriggers.GENERATE_LOOT.trigger(player, lootID);
            LootParams params = new LootParams.Builder(player.serverLevel())
                    .withParameter(LootContextParams.ORIGIN, player.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, player.damageSources().magic())
                    .withParameter(LootContextParams.THIS_ENTITY, player)
                    .withLuck(player.getLuck())
                    .create(LootContextParamSets.ENTITY);
            List<ItemStack> loot = lootTable.getRandomItems(params);
            loot.forEach(stack -> {
                boolean bl = player.getInventory().add(stack);
                if (!bl || !stack.isEmpty()) {
                    ItemEntity itemEntity = player.drop(stack, false);
                    if (itemEntity != null) {
                        itemEntity.setNoPickUpDelay();
                        itemEntity.setThrower(player);
                    }
                }
            });
        }
    }

    public void onReset(ServerPlayer player) {
    }

    public abstract Map<String, QuestEntry> resolveTasks(ServerPlayer player, int questIndex);

    public boolean isDynamic() {
        return false;
    }

    @Override
    public String toString() {
        return String.format("[Quest:%s]", this.id);
    }

    @Override
    public int compareTo(@NotNull QuestBase quest) {
        if (this.sortingId == quest.sortingId) {
            if (this.neededParentQuests.isEmpty() && !quest.neededParentQuests.isEmpty())
                return -1;
            if (!this.neededParentQuests.isEmpty() && quest.neededParentQuests.isEmpty())
                return 1;
            return this.id.compareTo(quest.id);
        }
        return Integer.compare(this.sortingId, quest.sortingId);
    }

    public static abstract class BuilderBase<Q extends QuestBase, T extends BuilderBase<Q, T>> {

        protected final ResourceLocation id;
        protected QuestCategory category = QuestCategory.DEFAULT_CATEGORY;
        protected final List<ResourceLocation> neededParentQuests = new ArrayList<>();

        protected int repeatDelay, repeatDaily;
        protected String repeatDelayString;

        protected final String questTaskString;
        protected final List<String> questDesc = new ArrayList<>();

        protected boolean redoParent, needsUnlock, isDailyQuest;

        protected int sortingId;

        protected EntityPredicate unlockCondition = null;

        protected ItemStack icon = new ItemStack(Items.PAPER);

        protected Visibility visibility = Visibility.DEFAULT;

        protected BuilderBase(ResourceLocation id, String task) {
            this.id = id;
            this.questTaskString = task;
        }

        public T addDescription(String desc) {
            this.questDesc.add(desc);
            return this.asThis();
        }

        public T withCategory(QuestCategory category) {
            this.category = category;
            return this.asThis();
        }

        public T addParent(ResourceLocation parent) {
            this.neededParentQuests.add(parent);
            return this.asThis();
        }

        public T setRedoParent() {
            this.redoParent = true;
            return this.asThis();
        }

        public T needsUnlocking() {
            this.needsUnlock = true;
            return this.asThis();
        }

        public T withIcon(ItemStack stack) {
            this.icon = stack;
            return this.asThis();
        }

        public T setRepeatDelay(int delay) {
            this.repeatDelay = delay;
            return this.asThis();
        }

        public T setRepeatDelay(String delay) {
            this.repeatDelayString = delay;
            this.repeatDelay = ParseHelper.tryParseTime(this.repeatDelayString, this.repeatDelayString);
            return this.asThis();
        }

        public T setMaxDaily(int max) {
            this.repeatDaily = max;
            return this.asThis();
        }

        public T withSortingNum(int num) {
            this.sortingId = num;
            return this.asThis();
        }

        public T setDailyQuest() {
            this.isDailyQuest = true;
            return this.asThis();
        }

        public T withUnlockCondition(EntityPredicate unlockCondition) {
            this.unlockCondition = unlockCondition;
            return this.asThis();
        }

        public T setVisibility(Visibility visibility) {
            this.visibility = visibility;
            return this.asThis();
        }

        protected abstract T asThis();

        public abstract Q build();
    }

    public interface QuestBuildFactory<R, B> {

        B create(ResourceLocation id, String task, R data);
    }

    public enum Visibility {
        DEFAULT,
        ALWAYS,
        NEVER
    }
}
