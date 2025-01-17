package io.github.flemmli97.simplequests.quest.entry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.flemmli97.simplequests.CodecHelper;
import io.github.flemmli97.simplequests.SimpleQuests;
import io.github.flemmli97.simplequests.api.QuestEntry;
import io.github.flemmli97.simplequests.config.ConfigHandler;
import io.github.flemmli97.simplequests.player.PlayerData;
import io.github.flemmli97.simplequests.player.ProgressionTrackerImpl;
import io.github.flemmli97.simplequests.player.QuestProgress;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.critereon.BlockPredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.LocationPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public class QuestEntryImpls {

    public record ItemEntry(ItemPredicate predicate, int amount,
                            String description, boolean consumeItems,
                            EntityPredicate playerPredicate) implements QuestEntry {

        public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SimpleQuests.MODID, "item");

        public static final Codec<ItemEntry> CODEC = RecordCodecBuilder.create((instance) ->
                instance.group(ItemPredicate.CODEC.fieldOf("predicate").forGetter(d -> d.predicate),
                        Codec.STRING.optionalFieldOf("description").forGetter(d -> d.description.isEmpty() ? Optional.empty() : Optional.of(d.description)),
                        ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(d -> d.amount),
                        Codec.BOOL.fieldOf("consumeItems").forGetter(d -> d.consumeItems),
                        EntityPredicate.CODEC.optionalFieldOf("playerPredicate").forGetter(d -> Optional.ofNullable(d.playerPredicate))
                ).apply(instance, (pred, desc, amount, consume, player) -> new ItemEntry(pred, amount, desc.orElse(""), consume, player.orElse(null))));

        @Override
        public boolean submit(ServerPlayer player) {
            if (this.playerPredicate != null && !this.playerPredicate.matches(player, player))
                return false;
            List<ItemStack> matching = new ArrayList<>();
            int i = 0;
            for (ItemStack stack : player.getInventory().items) {
                if (this.predicate.test(stack)) {
                    if (stack.isDamageableItem()) {
                        if (stack.getDamageValue() != 0) {
                            continue;
                        }
                    }
                    //Ignore "special" items
                    if (!this.isJustRenamedItem(stack)) {
                        continue;
                    }
                    matching.add(stack);
                    i += stack.getCount();
                }
            }
            if (i < this.amount)
                return false;
            if (this.consumeItems) {
                i = this.amount;
                for (ItemStack stack : matching) {
                    if (i > stack.getCount()) {
                        int count = stack.getCount();
                        stack.setCount(0);
                        i -= count;
                    } else {
                        stack.shrink(i);
                        break;
                    }
                }
            }
            return true;
        }

        private boolean isJustRenamedItem(ItemStack stack) {
            return stack.getComponentsPatch()
                    .entrySet().stream()
                    .noneMatch(e -> e.getKey() != DataComponents.CUSTOM_NAME);
        }

        @Override
        public ResourceLocation getId() {
            return ID;
        }

        @Override
        public MutableComponent translation(ServerPlayer player) {
            Function<String, String> key = s -> !this.description.isEmpty() ? this.description : ConfigHandler.LANG.get(player, this.getId().toString() + s);
            List<MutableComponent> formattedItems = itemComponents(this.predicate);
            if (formattedItems.isEmpty())
                return Component.translatable(key.apply(".empty"));
            if (formattedItems.size() == 1) {
                return Component.translatable(key.apply(".single" + (this.consumeItems ? "" : ".keep")), formattedItems.get(0).withStyle(ChatFormatting.AQUA), this.amount);
            }
            MutableComponent items = null;
            for (MutableComponent c : formattedItems) {
                if (items == null)
                    items = Component.literal("[").append(c);
                else
                    items.append(Component.literal(", ")).append(c);
            }
            items.append("]");
            return Component.translatable(key.apply(".multi" + (this.consumeItems ? "" : ".keep")), items.withStyle(ChatFormatting.AQUA), this.amount);
        }

        public static List<MutableComponent> itemComponents(ItemPredicate predicate) {
            List<MutableComponent> formattedItems = new ArrayList<>();
            predicate.items().ifPresent(n -> n.forEach(h -> formattedItems.add(Component.translatable(h.value().getDescriptionId()))));
            return formattedItems;
        }
    }

    public record KillEntry(EntityPredicate predicate, int amount, String description,
                            EntityPredicate playerPredicate) implements QuestEntry {

        public static final Codec<KillEntry> CODEC = RecordCodecBuilder.create((instance) ->
                instance.group(EntityPredicate.CODEC.fieldOf("predicate").forGetter(d -> d.predicate),
                        ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(d -> d.amount),
                        Codec.STRING.optionalFieldOf("description").forGetter(d -> d.description.isEmpty() ? Optional.empty() : Optional.of(d.description)),
                        EntityPredicate.CODEC.optionalFieldOf("playerPredicate").forGetter(d -> Optional.ofNullable(d.playerPredicate))
                ).apply(instance, (pred, amount, desc, player) -> new KillEntry(pred, amount, desc.orElse(""), player.orElse(null))));

        public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SimpleQuests.MODID, "entity");

        @Override
        public boolean submit(ServerPlayer player) {
            return false;
        }

        @Override
        public ResourceLocation getId() {
            return ID;
        }

        @Override
        public MutableComponent translation(ServerPlayer player) {
            return this.predicate.entityType().map(t -> t.types().unwrapKey().map(key ->
                                    Component.translatable(!this.description.isEmpty() ? this.description :
                                            ConfigHandler.LANG.get(player, this.getId().toString() + ".tag"), Component.literal("#" + key.location()).withStyle(ChatFormatting.AQUA), this.amount))
                            .orElse(
                                    Component.translatable(!this.description.isEmpty() ? this.description :
                                            ConfigHandler.LANG.get(player, this.getId().toString()), fromTypes(t.types()).withStyle(ChatFormatting.AQUA), this.amount)))
                    .orElse(Component.literal("MISSINGNO"));
        }

        private static MutableComponent fromTypes(HolderSet<EntityType<?>> set) {
            MutableComponent comp = Component.literal("");
            boolean first = true;
            for (Holder<EntityType<?>> h : set) {
                if (first)
                    first = false;
                else
                    comp.append(", ");
                comp.append(Component.translatable(h.value().getDescriptionId()));
            }
            ;
            return comp;
        }

        public boolean check(ServerPlayer player, Entity entity) {
            return (this.playerPredicate == null || this.playerPredicate.matches(player, player))
                    && this.predicate.matches(player, entity);
        }

        @Nullable
        @Override
        public MutableComponent progress(ServerPlayer player, QuestProgress progress, String id) {
            return progress.progressComponent(player, ProgressionTrackerImpl.KillTracker.KEY, id);
        }
    }

    public record XPEntry(int amount,
                          EntityPredicate playerPredicate) implements QuestEntry {

        public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SimpleQuests.MODID, "xp");
        public static final Codec<XPEntry> CODEC = RecordCodecBuilder.create((instance) ->
                instance.group(ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(d -> d.amount),
                                EntityPredicate.CODEC.optionalFieldOf("playerPredicate").forGetter(d -> Optional.ofNullable(d.playerPredicate)))
                        .apply(instance, (amount, pred) -> new XPEntry(amount, pred.orElse(null))));

        @Override
        public boolean submit(ServerPlayer player) {
            if (this.playerPredicate != null && !this.playerPredicate.matches(player, player))
                return false;
            if (player.experienceLevel >= this.amount) {
                player.giveExperienceLevels(-this.amount);
                return true;
            }
            return false;
        }

        @Override
        public ResourceLocation getId() {
            return ID;
        }

        @Override
        public MutableComponent translation(ServerPlayer player) {
            return Component.translatable(ConfigHandler.LANG.get(player, this.getId().toString()), this.amount);
        }
    }

    public record AdvancementEntry(ResourceLocation advancement, boolean reset,
                                   EntityPredicate playerPredicate) implements QuestEntry {

        public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SimpleQuests.MODID, "advancement");
        public static final Codec<AdvancementEntry> CODEC = RecordCodecBuilder.create((instance) ->
                instance.group(ResourceLocation.CODEC.fieldOf("advancement").forGetter(d -> d.advancement),
                        Codec.BOOL.fieldOf("reset").forGetter(d -> d.reset),
                        EntityPredicate.CODEC.optionalFieldOf("playerPredicate").forGetter(d -> Optional.ofNullable(d.playerPredicate))
                ).apply(instance, (advancement, reset, pred) -> new AdvancementEntry(advancement, reset, pred.orElse(null))));

        @Override
        public boolean submit(ServerPlayer player) {
            if (this.playerPredicate != null && !this.playerPredicate.matches(player, player))
                return false;
            AdvancementHolder adv = player.getServer().getAdvancements().get(this.advancement);
            boolean ret = adv != null && (player.getAdvancements().getOrStartProgress(adv).isDone());
            if (ret && this.reset) {
                AdvancementProgress prog = player.getAdvancements().getOrStartProgress(adv);
                prog.getCompletedCriteria().forEach(s -> player.getAdvancements().revoke(adv, s));
            }
            return ret;
        }

        @Override
        public ResourceLocation getId() {
            return ID;
        }

        @Override
        public MutableComponent translation(ServerPlayer player) {
            AdvancementHolder advancement = player.getServer().getAdvancements().get(this.advancement());
            Component adv;
            if (advancement == null)
                adv = Component.translatable(ConfigHandler.LANG.get(player, "simplequests.missing.advancement"), this.advancement());
            else
                adv = Advancement.name(advancement);
            return Component.translatable(ConfigHandler.LANG.get(player, this.getId().toString()), adv);
        }
    }

    public record PositionEntry(BlockPos pos, int minDist, String description,
                                EntityPredicate playerPredicate) implements QuestEntry {

        public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SimpleQuests.MODID, "position");
        public static final Codec<PositionEntry> CODEC = RecordCodecBuilder.create((instance) ->
                instance.group(CodecHelper.BLOCK_POS_CODEC.fieldOf("pos").forGetter(d -> d.pos),
                        ExtraCodecs.NON_NEGATIVE_INT.fieldOf("minDist").forGetter(d -> d.minDist),
                        Codec.STRING.optionalFieldOf("description").forGetter(d -> d.description.isEmpty() ? Optional.empty() : Optional.of(d.description)),
                        EntityPredicate.CODEC.optionalFieldOf("playerPredicate").forGetter(d -> Optional.ofNullable(d.playerPredicate))
                ).apply(instance, (pred, amount, desc, player) -> new PositionEntry(pred, amount, desc.orElse(""), player.orElse(null))));

        @Override
        public boolean submit(ServerPlayer player) {
            return false;
        }

        @Override
        public ResourceLocation getId() {
            return ID;
        }

        @Override
        public MutableComponent translation(ServerPlayer player) {
            return Component.translatable(!this.description.isEmpty() ? this.description : ConfigHandler.LANG.get(player, this.getId().toString()), this.pos.getX(), this.pos.getY(), this.pos.getZ());
        }

        @Override
        public Predicate<PlayerData> tickable() {
            return d -> {
                ServerPlayer p = d.getPlayer();
                if (this.playerPredicate != null && !this.playerPredicate.matches(p, p))
                    return false;
                return p.tickCount % 20 == 0 && p.blockPosition().distSqr(this.pos) < this.minDist * this.minDist;
            };
        }
    }

    /**
     * Quest entry to check if a player matches a given location.
     *
     * @param location    The LocationPredicate to check
     * @param description Parsing a location predicate is way too complicated. Its easier instead to have the datapack maker provide a description instead
     */
    public record LocationEntry(LocationPredicate location, String description,
                                EntityPredicate playerPredicate) implements QuestEntry {

        public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SimpleQuests.MODID, "location");

        public static final Codec<LocationEntry> CODEC = RecordCodecBuilder.create((instance) ->
                instance.group(LocationPredicate.CODEC.fieldOf("predicate").forGetter(d -> d.location),
                        Codec.STRING.fieldOf("description").forGetter(d -> d.description),
                        EntityPredicate.CODEC.optionalFieldOf("playerPredicate").forGetter(d -> Optional.ofNullable(d.playerPredicate))
                ).apply(instance, (pred, desc, player) -> new LocationEntry(pred, desc, player.orElse(null))));

        @Override
        public boolean submit(ServerPlayer player) {
            return false;
        }

        @Override
        public ResourceLocation getId() {
            return ID;
        }

        @Override
        public MutableComponent translation(ServerPlayer player) {
            return Component.translatable(this.description);
        }

        @Override
        public Predicate<PlayerData> tickable() {
            return d -> {
                ServerPlayer p = d.getPlayer();
                if (this.playerPredicate != null && !this.playerPredicate.matches(p, p))
                    return false;
                return p.tickCount % 20 == 0 && this.location.matches(p.serverLevel(), p.getX(), p.getY(), p.getZ());
            };
        }
    }

    /**
     * Quest entry to check if a player interacts with an entity.
     *
     * @param description Parsing the predicates is way too complicated. Its easier instead to have the datapack maker provide a description instead
     */
    public record EntityInteractEntry(ItemPredicate heldItem, EntityPredicate entityPredicate, int amount,
                                      boolean consume, String description, String heldDescription,
                                      String entityDescription,
                                      EntityPredicate playerPredicate) implements QuestEntry {

        public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SimpleQuests.MODID, "entity_interact");
        public static final Codec<EntityInteractEntry> CODEC = RecordCodecBuilder.create((instance) ->
                instance.group(Codec.STRING.fieldOf("description").forGetter(d -> d.description),
                        Codec.STRING.optionalFieldOf("heldDescription").forGetter(d -> d.heldDescription.isEmpty() ? Optional.empty() : Optional.of(d.heldDescription)),
                        Codec.STRING.optionalFieldOf("entityDescription").forGetter(d -> d.entityDescription.isEmpty() ? Optional.empty() : Optional.of(d.entityDescription)),

                        ItemPredicate.CODEC.optionalFieldOf("item").forGetter(d -> Optional.ofNullable(d.heldItem)),
                        EntityPredicate.CODEC.optionalFieldOf("predicate").forGetter(d -> Optional.ofNullable(d.entityPredicate)),
                        ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(d -> d.amount),
                        Codec.BOOL.fieldOf("consume").forGetter(d -> d.consume),
                        EntityPredicate.CODEC.optionalFieldOf("playerPredicate").forGetter(d -> Optional.ofNullable(d.playerPredicate))
                ).apply(instance, (desc, heldDesc, entityDesc, item, pred, amount, consume, player) ->
                        new EntityInteractEntry(item.orElse(null), pred.orElse(null), amount, consume, desc, heldDesc.orElse(""), entityDesc.orElse(""), player.orElse(null))));

        public EntityInteractEntry(ItemPredicate heldItem, EntityPredicate entityPredicate, int amount, boolean consume, String description) {
            this(heldItem, entityPredicate, amount, consume, description, "", "", null);
        }

        @Override
        public boolean submit(ServerPlayer player) {
            return false;
        }

        @Override
        public ResourceLocation getId() {
            return ID;
        }

        @Override
        public MutableComponent translation(ServerPlayer player) {
            return Component.translatable(this.description, Component.translatable(this.heldDescription), Component.translatable(this.entityDescription), this.amount);
        }

        @Nullable
        @Override
        public MutableComponent progress(ServerPlayer player, QuestProgress progress, String id) {
            return progress.progressComponent(player, ProgressionTrackerImpl.EntityTracker.KEY, id);
        }

        public boolean check(ServerPlayer player, Entity entity) {
            if (this.playerPredicate != null && !this.playerPredicate.matches(player, player))
                return false;
            boolean b = (this.heldItem == null || this.heldItem.test(player.getMainHandItem())) &&
                    (this.entityPredicate == null || this.entityPredicate.matches(player, entity));
            if (b && this.consume && !player.isCreative()) {
                player.getMainHandItem().shrink(1);
            }
            return b;
        }
    }

    /**
     * Quest entry to check when a player interacts with a block
     *
     * @param use If the player should use (right click) or break the block
     */
    public record BlockInteractEntry(ItemPredicate heldItem, BlockPredicate blockPredicate, int amount, boolean use,
                                     boolean consumeItem, boolean allowDupes, String description,
                                     String heldDescription,
                                     String blockDescription,
                                     EntityPredicate playerPredicate) implements QuestEntry {

        public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SimpleQuests.MODID, "block_interact");
        public static final Codec<BlockInteractEntry> CODEC = RecordCodecBuilder.create((instance) ->
                instance.group(Codec.STRING.optionalFieldOf("heldDescription").forGetter(d -> d.heldDescription.isEmpty() ? Optional.empty() : Optional.of(d.heldDescription)),
                        Codec.STRING.optionalFieldOf("blockDescription").forGetter(d -> d.blockDescription.isEmpty() ? Optional.empty() : Optional.of(d.blockDescription)),

                        BlockPredicate.CODEC.optionalFieldOf("block").forGetter(d -> Optional.ofNullable(d.blockPredicate)),
                        Codec.BOOL.fieldOf("consumeItem").forGetter(d -> d.consumeItem),
                        Codec.STRING.fieldOf("description").forGetter(d -> d.description),

                        EntityPredicate.CODEC.optionalFieldOf("playerPredicate").forGetter(d -> Optional.ofNullable(d.playerPredicate)),
                        ItemPredicate.CODEC.optionalFieldOf("item").forGetter(d -> Optional.ofNullable(d.heldItem)),

                        ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(d -> d.amount),
                        Codec.BOOL.fieldOf("use").forGetter(d -> d.use),
                        Codec.BOOL.optionalFieldOf("allowDupes").forGetter(d -> d.allowDupes ? Optional.of(true) : Optional.empty())
                ).apply(instance, (heldDesc, blockDescription, block, consume, desc, player, item, amount, use, allowDupes) -> {
                    ItemPredicate itemPredicate = item.orElse(null);
                    BlockPredicate blockPredicate = block.orElse(null);
                    if (itemPredicate == null && blockPredicate == null)
                        throw new IllegalStateException("Either item or block has to be defined");
                    return new BlockInteractEntry(itemPredicate, blockPredicate, amount, use, consume, allowDupes.orElse(false), desc, heldDesc.orElse(""), blockDescription.orElse(""), player.orElse(null));
                }));

        public BlockInteractEntry(ItemPredicate heldItem, BlockPredicate blockPredicate, int amount, boolean use,
                                  boolean consumeItem, String description) {
            this(heldItem, blockPredicate, amount, use, false, consumeItem, description, "", "", null);
        }

        @Override
        public boolean submit(ServerPlayer player) {
            return false;
        }

        @Override
        public ResourceLocation getId() {
            return ID;
        }

        @Override
        public MutableComponent translation(ServerPlayer player) {
            return Component.translatable(this.description, Component.translatable(this.heldDescription), Component.translatable(this.blockDescription), this.amount);
        }

        @Nullable
        @Override
        public MutableComponent progress(ServerPlayer player, QuestProgress progress, String id) {
            return progress.progressComponent(player, ProgressionTrackerImpl.BlockTracker.KEY, id);
        }

        public boolean check(ServerPlayer player, BlockPos pos, boolean use) {
            if (this.playerPredicate != null && !this.playerPredicate.matches(player, player))
                return false;
            if (use != this.use)
                return false;
            boolean b = (this.heldItem == null || this.heldItem.test(player.getMainHandItem())) &&
                    (this.blockPredicate == null || this.blockPredicate.matches(player.serverLevel(), pos));
            if (b && this.consumeItem && !player.isCreative()) {
                player.getMainHandItem().shrink(1);
            }
            return b;
        }
    }

    /**
     * Quest entry to check for when a player crafts something
     */
    public record CraftingEntry(ItemPredicate item, EntityPredicate playerPredicate, int amount,
                                String description, String heldDescription,
                                String entityDescription) implements QuestEntry {

        public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SimpleQuests.MODID, "crafting");
        public static final Codec<CraftingEntry> CODEC = RecordCodecBuilder.create((instance) ->
                instance.group(Codec.STRING.fieldOf("description").forGetter(d -> d.description),
                        Codec.STRING.optionalFieldOf("heldDescription").forGetter(d -> d.heldDescription.isEmpty() ? Optional.empty() : Optional.of(d.heldDescription)),
                        Codec.STRING.optionalFieldOf("entityDescription").forGetter(d -> d.entityDescription.isEmpty() ? Optional.empty() : Optional.of(d.entityDescription)),

                        ItemPredicate.CODEC.fieldOf("item").forGetter(d -> d.item),
                        EntityPredicate.CODEC.optionalFieldOf("playerPredicate").forGetter(d -> Optional.ofNullable(d.playerPredicate)),
                        ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(d -> d.amount)
                ).apply(instance, (desc, heldDesc, entityDesc, item, pred, amount) -> new CraftingEntry(item, pred.orElse(null), amount, desc, heldDesc.orElse(""), entityDesc.orElse(""))));

        public CraftingEntry(ItemPredicate item, EntityPredicate playerPredicate, int amount, String description) {
            this(item, playerPredicate, amount, description, "", "");
        }

        @Override
        public boolean submit(ServerPlayer player) {
            return false;
        }

        @Override
        public ResourceLocation getId() {
            return ID;
        }

        @Override
        public MutableComponent translation(ServerPlayer player) {
            return Component.translatable(this.description, Component.translatable(this.heldDescription), Component.translatable(this.entityDescription), this.amount);
        }

        @Nullable
        @Override
        public MutableComponent progress(ServerPlayer player, QuestProgress progress, String id) {
            return progress.progressComponent(player, ProgressionTrackerImpl.CraftingTracker.KEY, id);
        }

        public boolean check(ServerPlayer player, ItemStack stack) {
            return this.item.test(stack) && (this.playerPredicate == null || this.playerPredicate.matches(player, player));
        }
    }

    public record FishingEntry(ItemPredicate item, EntityPredicate playerPredicate, int amount,
                               String description, String heldDescription,
                               String entityDescription) implements QuestEntry {

        public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SimpleQuests.MODID, "fishing");
        public static final Codec<FishingEntry> CODEC = RecordCodecBuilder.create((instance) ->
                instance.group(Codec.STRING.fieldOf("description").forGetter(d -> d.description),
                        Codec.STRING.optionalFieldOf("heldDescription").forGetter(d -> d.heldDescription.isEmpty() ? Optional.empty() : Optional.of(d.heldDescription)),
                        Codec.STRING.optionalFieldOf("entityDescription").forGetter(d -> d.entityDescription.isEmpty() ? Optional.empty() : Optional.of(d.entityDescription)),

                        ItemPredicate.CODEC.fieldOf("item").forGetter(d -> d.item),
                        EntityPredicate.CODEC.optionalFieldOf("playerPredicate").forGetter(d -> Optional.ofNullable(d.playerPredicate)),
                        ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(d -> d.amount)
                ).apply(instance, (desc, heldDesc, entityDesc, item, pred, amount) -> new FishingEntry(item, pred.orElse(null), amount, desc, heldDesc.orElse(""), entityDesc.orElse(""))));

        public FishingEntry(ItemPredicate item, EntityPredicate playerPredicate, int amount, String description) {
            this(item, playerPredicate, amount, description, "", "");
        }

        @Override
        public boolean submit(ServerPlayer player) {
            return false;
        }

        @Override
        public ResourceLocation getId() {
            return ID;
        }

        @Override
        public MutableComponent translation(ServerPlayer player) {
            return Component.translatable(this.description, Component.translatable(this.heldDescription), Component.translatable(this.entityDescription), this.amount);
        }

        @Nullable
        @Override
        public MutableComponent progress(ServerPlayer player, QuestProgress progress, String id) {
            return progress.progressComponent(player, ProgressionTrackerImpl.FishingTracker.KEY, id);
        }

        public boolean check(ServerPlayer player, ItemStack stack) {
            return this.item.test(stack) && (this.playerPredicate == null || this.playerPredicate.matches(player, player));
        }
    }
}
