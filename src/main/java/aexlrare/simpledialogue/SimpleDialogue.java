package aexlrare.simpledialogue;

import aexlrare.simpledialogue.item.DialogueWand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

public class SimpleDialogue implements ModInitializer {

    public static final String MOD_ID = "simpledialogue";
    public static final Item WAND = new DialogueWand(new Item.Settings().maxCount(1));

    // 定义网络包 ID 和 结构
    public static final CustomPayload.Id<OptionSelectPayload> SELECT_PACKET_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "select_option"));

    public record OptionSelectPayload(int index) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, OptionSelectPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.INTEGER, OptionSelectPayload::index,
                OptionSelectPayload::new
        );
        @Override
        public Id<? extends CustomPayload> getId() { return SELECT_PACKET_ID; }
    }

    @Override
    public void onInitialize() {
        // === 注册网络包 ===
        PayloadTypeRegistry.playC2S().register(SELECT_PACKET_ID, OptionSelectPayload.CODEC);

        // 监听客户端发来的按键包
        ServerPlayNetworking.registerGlobalReceiver(SELECT_PACKET_ID, (payload, context) -> {
            context.server().execute(() -> {
                // 调用管理器处理快捷键选择
                DialogueManager.handleShortcut(context.player(), payload.index());
            });
        });

        // === 常规注册 ===
        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "wand"), WAND);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(content -> content.add(WAND));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            SimpleDialogueCommand.register(dispatcher);
        });

        DialogueManager.init();
        RegionManager.init();

        ServerTickEvents.END_SERVER_TICK.register(RegionManager::onTick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            RegionManager.onPlayerDisconnect(handler.player.getUuid());
            DialogueManager.clearPlayerSession(handler.player.getUuid()); // 清理对话状态
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient || hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;

            ItemStack heldItem = player.getMainHandStack();
            String boundDialogueId = DialogueManager.getDialogueId(entity.getUuid());

            if (heldItem.getItem() == WAND) {
                String uuidStr = entity.getUuidAsString();
                Text uuidText = Text.literal("§b§n" + uuidStr)
                        .setStyle(Style.EMPTY
                                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, uuidStr))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("点击复制 UUID")))
                        );

                if (boundDialogueId != null) {
                    player.sendMessage(Text.literal("§6[SD Debug] §fEntity: ").append(uuidText), false);
                    player.sendMessage(Text.literal("§7Status: §aBound -> [" + boundDialogueId + "]"), false);
                    DialogueManager.startDialogue(serverPlayer, boundDialogueId);
                } else {
                    player.sendMessage(Text.literal("§6[SD Debug] §fEntity: ").append(uuidText), false);
                    player.sendMessage(Text.literal("§7Status: §cNot Bound"), false);
                }
                return ActionResult.SUCCESS;
            }

            if (boundDialogueId != null) {
                DialogueManager.startDialogue(serverPlayer, boundDialogueId);
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
    }
}
