package io.github.flemmli97.simplequests.gui;

import io.github.flemmli97.simplequests.SimpleQuests;
import io.github.flemmli97.simplequests.config.ConfigHandler;
import io.github.flemmli97.simplequests.gui.inv.SeparateInv;
import io.github.flemmli97.simplequests.player.PlayerData;
import io.github.flemmli97.simplequests.player.QuestProgress;
import io.github.flemmli97.simplequests.quest.types.QuestBase;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class CurrentQuestGui extends ServerOnlyScreenHandler<Object> {

    public static int QUEST_PER_PAGE = 12;

    private int page, maxPages;
    private final ServerPlayer player;

    protected CurrentQuestGui(int syncId, Inventory playerInventory) {
        super(syncId, playerInventory, 6, null);
        if (playerInventory.player instanceof ServerPlayer)
            this.player = (ServerPlayer) playerInventory.player;
        else
            throw new IllegalStateException("This is a server side container");
    }

    public static void openGui(ServerPlayer player) {
        MenuProvider fac = new MenuProvider() {
            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
                return new CurrentQuestGui(syncId, inv);
            }

            @Override
            public Component getDisplayName() {
                return Component.translatable(ConfigHandler.LANG.get(player, "simplequests.gui.quest.current"));
            }
        };
        player.openMenu(fac);
    }

    private ItemStack ofQuest(QuestProgress progress, ServerPlayer player) {
        QuestBase quest = progress.getQuest();
        ItemStack stack = quest.getIcon();
        stack.set(DataComponents.CUSTOM_NAME, progress.getTask(player).setStyle(Style.EMPTY.withItalic(false).applyFormat(ChatFormatting.GOLD)));
        List<Component> lore = new ArrayList<>();
        progress.getDescription(player).forEach(c -> lore.add(c.setStyle(c.getStyle().withItalic(false))));
        List<String> finished = progress.finishedTasks();
        progress.getQuestEntries().entrySet().stream()
                .filter(e -> !finished.contains(e.getKey()))
                .forEach(e -> {
                    MutableComponent comp = e.getValue().progress(player, progress, e.getKey());
                    MutableComponent translation = e.getValue().translation(player).withStyle(Style.EMPTY.withItalic(false).applyFormat(ChatFormatting.YELLOW));
                    if (comp == null)
                        lore.add(translation);
                    else
                        lore.add(Component.translatable(ConfigHandler.LANG.get(player, "simplequest.quest.progress"), translation, comp).setStyle(comp.getStyle().withItalic(false)));
                });
        stack.set(DataComponents.LORE, new ItemLore(lore));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.putString(QuestGui.STACK_NBT_ID, quest.id.toString()));
        return stack;
    }

    public static ItemStack emptyFiller() {
        ItemStack stack = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(""));
        return stack;
    }

    public static void playSongToPlayer(ServerPlayer player, Holder<SoundEvent> event, float vol, float pitch) {
        player.connection.send(
                new ClientboundSoundPacket(event, SoundSource.PLAYERS, player.position().x, player.position().y, player.position().z, vol, pitch, player.getRandom().nextLong()));
    }

    public static void playSongToPlayer(ServerPlayer player, SoundEvent event, float vol, float pitch) {
        player.connection.send(
                new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(event), SoundSource.PLAYERS, player.position().x, player.position().y, player.position().z, vol, pitch, player.getRandom().nextLong()));
    }

    @Override
    protected void fillInventoryWith(Player player, SeparateInv inv, Object data) {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;
        List<QuestProgress> questMap = PlayerData.get(serverPlayer).getCurrentQuest()
                .stream().sorted(Comparator.comparing(QuestProgress::getQuest)).toList();
        this.maxPages = (questMap.size() - 1) / QUEST_PER_PAGE;
        int page = 0;
        int id = 0;
        for (int i = 0; i < 54; i++) {
            if (i == 0) {
                ItemStack stack = emptyFiller();
                inv.updateStack(i, stack);
            } else if (i == 8 && page < this.maxPages) {
                ItemStack close = new ItemStack(Items.ARROW);
                close.set(DataComponents.CUSTOM_NAME, Component.translatable(ConfigHandler.LANG.get(serverPlayer, "simplequests.gui.next")).setStyle(Style.EMPTY.withItalic(false).applyFormat(ChatFormatting.WHITE)));
                inv.updateStack(i, close);
            } else if (i < 9 || i > 44 || i % 9 == 0 || i % 9 == 8)
                inv.updateStack(i, emptyFiller());
            else if (i % 9 == 1 || i % 9 == 4 || i % 9 == 7) {
                if (id < questMap.size()) {
                    ItemStack stack = this.ofQuest(questMap.get(id), serverPlayer);
                    if (!stack.isEmpty()) {
                        inv.updateStack(i, this.ofQuest(questMap.get(id), serverPlayer));
                        id++;
                    }
                }
            }
        }
    }

    private void flipPage() {
        List<QuestProgress> questMap = PlayerData.get(this.player).getCurrentQuest()
                .stream().sorted(Comparator.comparing(QuestProgress::getQuest)).toList();
        int id = this.page * QUEST_PER_PAGE;
        for (int i = 0; i < 54; i++) {
            if (i == 0) {
                ItemStack stack = emptyFiller();
                if (this.page > 0) {
                    stack = new ItemStack(Items.ARROW);
                    stack.set(DataComponents.CUSTOM_NAME, Component.translatable(ConfigHandler.LANG.get(this.player, "simplequests.gui.previous")).setStyle(Style.EMPTY.withItalic(false).applyFormat(ChatFormatting.WHITE)));
                }
                this.slots.get(i).set(stack);
            } else if (i == 8) {
                ItemStack stack = emptyFiller();
                if (this.page < this.maxPages) {
                    stack = new ItemStack(Items.ARROW);
                    stack.set(DataComponents.CUSTOM_NAME, Component.translatable(ConfigHandler.LANG.get(this.player, "simplequests.gui.next")).setStyle(Style.EMPTY.withItalic(false).applyFormat(ChatFormatting.WHITE)));
                }
                this.slots.get(i).set(stack);
            } else if (i < 9 || i > 44 || i % 9 == 0 || i % 9 == 8)
                this.slots.get(i).set(emptyFiller());
            else if (i % 9 == 1 || i % 9 == 4 || i % 9 == 7) {
                if (id < questMap.size()) {
                    this.slots.get(i).set(this.ofQuest(questMap.get(id), this.player));
                    id++;
                } else
                    this.slots.get(i).set(ItemStack.EMPTY);
            }
        }
        this.broadcastChanges();
    }

    @Override
    protected boolean handleSlotClicked(ServerPlayer player, int index, Slot slot, int clickType) {
        if (index == 0) {
            this.page--;
            this.flipPage();
            CurrentQuestGui.playSongToPlayer(player, SoundEvents.UI_BUTTON_CLICK, 1, 1f);
            return true;
        }
        if (index == 8) {
            this.page++;
            this.flipPage();
            CurrentQuestGui.playSongToPlayer(player, SoundEvents.UI_BUTTON_CLICK, 1, 1f);
            return true;
        }
        ItemStack stack = slot.getItem();
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null)
            return false;
        if (stack.getItem() == Items.BOOK) {
            playSongToPlayer(player, SoundEvents.VILLAGER_NO, 1, 1f);
            return false;
        }
        Optional<ResourceLocation> opt = customData.read(ResourceLocation.CODEC.fieldOf(QuestGui.STACK_NBT_ID)).result();
        if (opt.isEmpty())
            return false;
        ResourceLocation id = opt.get();
        Optional<QuestProgress> questOpt = PlayerData.get(this.player).getCurrentQuest().stream().filter(p -> p.getQuest().id.equals(id)).findFirst();
        if (questOpt.isEmpty()) {
            SimpleQuests.LOGGER.error("No such quest " + id);
            return false;
        }
        QuestBase quest = questOpt.get().getQuest();
        ConfirmScreenHandler.openConfirmScreen(player, b -> {
            if (b) {
                player.closeContainer();
                PlayerData.get(player).reset(quest.id, true);
                playSongToPlayer(player, SoundEvents.ANVIL_FALL, 1, 1.2f);
            } else {
                player.closeContainer();
                player.getServer().execute(() -> CurrentQuestGui.openGui(player));
                playSongToPlayer(player, SoundEvents.VILLAGER_NO, 1, 1f);
            }
        }, "simplequests.gui.reset");
        return true;
    }

    @Override
    protected boolean isRightSlot(int slot) {
        return (this.page > 0 && slot == 0) || (this.page < this.maxPages && slot == 8) || (slot < 45 && slot > 8 && (slot % 9 == 1 || slot % 9 == 4 || slot % 9 == 7));
    }
}
