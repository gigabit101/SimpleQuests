package io.github.flemmli97.simplequests.forge;

import io.github.flemmli97.simplequests.QuestCommand;
import io.github.flemmli97.simplequests.SimpleQuests;
import io.github.flemmli97.simplequests.config.ConfigHandler;
import io.github.flemmli97.simplequests.datapack.ProgressionTrackerRegistry;
import io.github.flemmli97.simplequests.datapack.QuestBaseRegistry;
import io.github.flemmli97.simplequests.datapack.QuestEntryRegistry;
import io.github.flemmli97.simplequests.datapack.QuestsManager;
import io.github.flemmli97.simplequests.forge.client.ForgeClientHandler;
import io.github.flemmli97.simplequests.network.C2SNotify;
import io.github.flemmli97.simplequests.player.PlayerData;
import io.github.flemmli97.simplequests.quest.QuestNumberProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.providers.number.LootNumberProviderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(value = SimpleQuests.MODID)
public class SimpleQuestForge {

    private static final DeferredRegister<LootNumberProviderType> NUMBER_PROVIDERS = DeferredRegister.create(BuiltInRegistries.LOOT_NUMBER_PROVIDER_TYPE, SimpleQuests.MODID);
    public static final DeferredHolder<LootNumberProviderType, LootNumberProviderType> CONTEXT_MULTIPLIER = NUMBER_PROVIDERS.register("context_multiplier", () -> new LootNumberProviderType(QuestNumberProvider.ContextMultiplierNumberProvider.CODEC));

    public SimpleQuestForge(IEventBus modBus) {
        SimpleQuests.updateLoaderImpl(new LoaderImpl());
        modBus.addListener(SimpleQuestForge::registerPackets);
        NUMBER_PROVIDERS.register(modBus);
        NeoForge.EVENT_BUS.addListener(SimpleQuestForge::addReload);
        NeoForge.EVENT_BUS.addListener(SimpleQuestForge::command);
        NeoForge.EVENT_BUS.addListener(SimpleQuestForge::kill);
        NeoForge.EVENT_BUS.addListener(SimpleQuestForge::interactSpecific);
        NeoForge.EVENT_BUS.addListener(SimpleQuestForge::interactBlock);
        NeoForge.EVENT_BUS.addListener(SimpleQuestForge::breakBlock);
        if (FMLEnvironment.dist == Dist.CLIENT)
            NeoForge.EVENT_BUS.addListener(ForgeClientHandler::login);
        QuestBaseRegistry.register();
        QuestEntryRegistry.register();
        ProgressionTrackerRegistry.register();
        ConfigHandler.init();
        SimpleQuests.FTB_RANKS = ModList.get().isLoaded("ftbranks");
    }

    public static void registerPackets(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(SimpleQuests.MODID);
        registrar.playToServer(C2SNotify.TYPE, C2SNotify.STREAM_CODEC, (pkt, ctx) -> ctx.enqueueWork(() -> C2SNotify.handle(pkt, (ServerPlayer) ctx.player())));
    }

    public static void addReload(AddReloadListenerEvent event) {
        event.addListener(QuestsManager.INSTANCE = new QuestsManager(event.getRegistryAccess()));
    }

    public static void command(RegisterCommandsEvent event) {
        QuestCommand.register(event.getDispatcher());
    }

    public static void kill(LivingDeathEvent event) {
        if (event.getEntity().getKillCredit() instanceof ServerPlayer player) {
            PlayerData.get(player).onKill(event.getEntity());
        }
    }

    public static void interactSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer)
            SimpleQuests.onInteractEntity(serverPlayer, event.getTarget(), event.getHand());
    }

    public static void interactBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer && event.getUseBlock() != TriState.FALSE)
            PlayerData.get(serverPlayer).onBlockInteract(event.getPos(), true);
    }

    public static void breakBlock(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer serverPlayer)
            PlayerData.get(serverPlayer).onBlockInteract(event.getPos(), false);
    }
}
