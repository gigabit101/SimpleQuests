package io.github.flemmli97.simplequests.gui;

import io.github.flemmli97.simplequests.SimpleQuests;
import io.github.flemmli97.simplequests.config.ConfigHandler;
import io.github.flemmli97.simplequests.datapack.QuestsManager;
import io.github.flemmli97.simplequests.gui.inv.SeparateInv;
import io.github.flemmli97.simplequests.player.PlayerData;
import io.github.flemmli97.simplequests.quest.Quest;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class QuestGui extends ServerOnlyScreenHandler<Object> {

    private int page, maxPages;
    private List<ResourceLocation> quests;
    private Player player;

    protected QuestGui(int syncId, Inventory playerInventory) {
        super(syncId, playerInventory, 6, null);
        this.player = playerInventory.player;
    }

    public static void openGui(Player player) {
        MenuProvider fac = new MenuProvider() {
            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
                return new QuestGui(syncId, inv);
            }

            @Override
            public Component getDisplayName() {
                return new TextComponent(ConfigHandler.lang.get("simplequests.gui.main"));
            }
        };
        player.openMenu(fac);
    }

    private static ItemStack ofQuest(Quest quest, ServerPlayer player) {
        PlayerData.AcceptType type = PlayerData.get(player).canAcceptQuest(quest);
        ItemStack stack = new ItemStack(type == PlayerData.AcceptType.ACCEPT ? Items.PAPER : Items.BOOK);
        stack.setHoverName(new TextComponent(quest.questTaskString).setStyle(Style.EMPTY.withItalic(false).applyFormat(ChatFormatting.GOLD)));
        ListTag lore = new ListTag();
        for (MutableComponent comp : quest.getFormattedGuiTasks(player))
            lore.add(StringTag.valueOf(Component.Serializer.toJson(comp.setStyle(comp.getStyle().withItalic(false)))));
        stack.getOrCreateTagElement("display").put("Lore", lore);
        stack.getOrCreateTagElement("SimpleQuests").putString("Quest", quest.id.toString());
        return stack;
    }

    public static ItemStack emptyFiller() {
        ItemStack stack = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        stack.setHoverName(new TextComponent(""));
        return stack;
    }

    private static void playSongToPlayer(ServerPlayer player, SoundEvent event, float vol, float pitch) {
        player.connection.send(
                new ClientboundSoundPacket(event, SoundSource.PLAYERS, player.position().x, player.position().y, player.position().z, vol, pitch));
    }

    @Override
    protected void fillInventoryWith(Player player, SeparateInv inv, Object additionalData) {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;
        Map<ResourceLocation, Quest> questMap = QuestsManager.instance().getQuests();
        this.quests = new ArrayList<>(questMap.keySet());
        this.quests.removeIf(res -> {
            PlayerData.AcceptType type = PlayerData.get(serverPlayer).canAcceptQuest(questMap.get(res));
            return type == PlayerData.AcceptType.REQUIREMENTS || type == PlayerData.AcceptType.ONETIME;
        });
        this.maxPages = (this.quests.size() - 1) / 28;
        int id = 0;
        for (int i = 0; i < 54; i++) {
            if (i == 8 && this.quests.size() > 12) {
                ItemStack close = new ItemStack(Items.ARROW);
                close.setHoverName(new TextComponent(ConfigHandler.lang.get("simplequests.gui.next")).setStyle(Style.EMPTY.withItalic(false).applyFormat(ChatFormatting.WHITE)));
                inv.updateStack(i, close);
            } else if (i < 9 || i > 44 || i % 9 == 0 || i % 9 == 8)
                inv.updateStack(i, emptyFiller());
            else if (i % 9 == 1 || i % 9 == 4 || i % 9 == 7) {
                if (id < this.quests.size()) {
                    ItemStack stack = ofQuest(questMap.get(this.quests.get(id)), serverPlayer);
                    if(!stack.isEmpty()) {
                        inv.updateStack(i, ofQuest(questMap.get(this.quests.get(id)), serverPlayer));
                        id++;
                    }
                }
            }
        }
    }

    private void flipPage() {
        if (!(this.player instanceof ServerPlayer serverPlayer))
            return;
        Map<ResourceLocation, Quest> questMap = QuestsManager.instance().getQuests();
        int id = this.page * 12;
        for (int i = 0; i < 54; i++) {
            if (i == 0) {
                ItemStack stack = emptyFiller();
                if (this.page < this.maxPages) {
                    stack = new ItemStack(Items.ARROW);
                    stack.setHoverName(new TextComponent(ConfigHandler.lang.get("simplequests.gui.previous")).setStyle(Style.EMPTY.withItalic(false).applyFormat(ChatFormatting.WHITE)));
                }
                this.slots.get(i).set(stack);
            } else if (i == 8) {
                ItemStack stack = emptyFiller();
                if (this.page < this.maxPages) {
                    stack = new ItemStack(Items.ARROW);
                    stack.setHoverName(new TextComponent(ConfigHandler.lang.get("simplequests.gui.next")).setStyle(Style.EMPTY.withItalic(false).applyFormat(ChatFormatting.WHITE)));
                }
                this.slots.get(i).set(stack);
            } else if (i < 9 || i > 44 || i % 9 == 0 || i % 9 == 8)
                this.slots.get(i).set(emptyFiller());
            else if (i % 9 == 1 || i % 9 == 4 || i % 9 == 7) {
                if (id < this.quests.size()) {
                    this.slots.get(i).set(ofQuest(questMap.get(this.quests.get(id)), serverPlayer));
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
            QuestGui.playSongToPlayer(player, SoundEvents.UI_BUTTON_CLICK, 1, 1f);
            return true;
        }
        if (index == 8) {
            this.page++;
            this.flipPage();
            QuestGui.playSongToPlayer(player, SoundEvents.UI_BUTTON_CLICK, 1, 1f);
            return true;
        }
        ItemStack stack = slot.getItem();
        if (!stack.hasTag())
            return false;
        CompoundTag tag = stack.getTag().getCompound("SimpleQuests");
        if (!tag.contains("Quest"))
            return false;
        ResourceLocation id = new ResourceLocation(tag.getString("Quest"));
        Quest quest = QuestsManager.instance().getQuests().get(id);
        if (quest == null) {
            SimpleQuests.logger.error("No such quest " + id);
            return false;
        }
        ConfirmScreenHandler.openConfirmScreen(player, b -> {
            if (b) {
                PlayerData.get(player).acceptQuest(quest);
                player.closeContainer();
                playSongToPlayer(player, SoundEvents.NOTE_BLOCK_PLING, 1, 1.2f);
            } else {
                player.closeContainer();
                player.getServer().execute(() -> QuestGui.openGui(player));
                playSongToPlayer(player, SoundEvents.VILLAGER_NO, 1, 1f);
            }
        });
        return true;
    }

    @Override
    protected boolean isRightSlot(int slot) {
        return (this.page > 0 && slot == 0) || (this.page < this.maxPages && slot == 8) || (slot < 45 && slot > 8 && (slot % 9 == 1 || slot % 9 == 4 || slot % 9 == 7));
    }
}