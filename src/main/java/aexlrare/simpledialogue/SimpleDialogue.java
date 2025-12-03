package aexlrare.simpledialogue;

import aexlrare.simpledialogue.item.DialogueWand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class SimpleDialogue implements ModInitializer {

    public static final String MOD_ID = "simpledialogue";
    public static final Item WAND = new DialogueWand(new Item.Settings().maxCount(1));

    public static final CustomPayload.Id<OptionSelectPayload> SELECT_PACKET_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "select_option"));
    public static final CustomPayload.Id<ModeSwitchPayload> MODE_PACKET_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "switch_mode"));
    public static final CustomPayload.Id<DialogueStatePayload> DIALOGUE_STATE_PACKET_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "dialogue_state"));

    public record OptionSelectPayload(int index) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, OptionSelectPayload> CODEC = PacketCodec.tuple(PacketCodecs.INTEGER, OptionSelectPayload::index, OptionSelectPayload::new);
        @Override public Id<? extends CustomPayload> getId() { return SELECT_PACKET_ID; }
    }

    public record ModeSwitchPayload(boolean next) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, ModeSwitchPayload> CODEC = PacketCodec.tuple(PacketCodecs.BOOL, ModeSwitchPayload::next, ModeSwitchPayload::new);
        @Override public Id<? extends CustomPayload> getId() { return MODE_PACKET_ID; }
    }

    // 新增 qteTimeout 字段
    public record DialogueStatePayload(String text, List<String> options, int qteTimeout) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, DialogueStatePayload> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, DialogueStatePayload::text,
                PacketCodecs.collection(ArrayList::new, PacketCodecs.STRING), DialogueStatePayload::options,
                PacketCodecs.INTEGER, DialogueStatePayload::qteTimeout,
                DialogueStatePayload::new
        );
        @Override public Id<? extends CustomPayload> getId() { return DIALOGUE_STATE_PACKET_ID; }
    }

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(SELECT_PACKET_ID, OptionSelectPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MODE_PACKET_ID, ModeSwitchPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DIALOGUE_STATE_PACKET_ID, DialogueStatePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SELECT_PACKET_ID, (payload, context) -> {
            context.server().execute(() -> DialogueManager.handleShortcut(context.player(), payload.index()));
        });

        ServerPlayNetworking.registerGlobalReceiver(MODE_PACKET_ID, (payload, context) -> {
            context.server().execute(() -> DialogueWand.cycleMode(context.player(), payload.next()));
        });

        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "wand"), WAND);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(content -> content.add(WAND));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> SimpleDialogueCommand.register(dispatcher));

        DialogueManager.init();
        RegionManager.init();
        BlockBindManager.init();

        // QTE 超时检测
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            RegionManager.onTick(server);
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                DialogueManager.checkQteTimeout(player);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            RegionManager.onPlayerDisconnect(handler.player.getUuid());
            DialogueManager.clearPlayerSession(handler.player);
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (!world.isClient && player.getMainHandStack().getItem() == WAND) {
                DialogueWand.onLeftClickBlock(player, pos);
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient && hand == Hand.MAIN_HAND) {
                if (player.getMainHandStack().getItem() == WAND) return ActionResult.PASS;

                BlockPos pos = hitResult.getBlockPos();
                String dialogueId = BlockBindManager.getDialogue(pos);
                if (dialogueId != null) {
                    DialogueManager.startDialogue((ServerPlayerEntity) player, dialogueId);
                    return ActionResult.SUCCESS;
                }
            }
            return ActionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient || hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;

            ItemStack heldItem = player.getMainHandStack();

            if (heldItem.getItem() == WAND) {
                DialogueWand.onUseEntity(player, entity);
                player.swingHand(hand, true);
                return ActionResult.SUCCESS;
            }

            String boundDialogueId = DialogueManager.getDialogueId(entity.getUuid());
            if (boundDialogueId != null) {
                DialogueManager.startDialogue(serverPlayer, boundDialogueId);
                player.swingHand(hand, true);
                return ActionResult.SUCCESS;
            }

            return ActionResult.PASS;
        });
    }
}