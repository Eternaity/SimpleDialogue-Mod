package aexlrare.simpledialogue;

import aexlrare.simpledialogue.item.DialogueWand;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class SimpleDialogueClient implements ClientModInitializer {

    private static KeyBinding modeUpKey;
    private static KeyBinding modeDownKey;

    private static boolean isHudOpen = false;
    private static String fullDialogueText = "";
    private static int typewriterIndex = 0;
    private static long lastTypewriterTick = 0;
    private static final int TYPEWRITER_DELAY_MS = 50; // 打字机速度

    private static List<OrderedText> wrappedDialogueLines = new ArrayList<>();
    private static List<String> currentOptions = new ArrayList<>();
    private static int selectedOptionIndex = 0;

    private static int qteTimeout = 0;
    private static long qteStartTime = 0;

    private static final int MAX_TEXT_WIDTH = 280;
    private static final float HUD_SCALE = 0.75f;

    private static BlockPos clientPos1;
    private static BlockPos clientPos2;
    private static DialogueWand.Mode clientMode = DialogueWand.Mode.REGION;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(SimpleDialogue.DIALOGUE_STATE_PACKET_ID, (payload, context) -> {
            context.client().execute(() -> {
                String rawText = payload.text();
                List<String> rawOptions = payload.options();
                int timeout = payload.qteTimeout();

                if (rawText == null || rawText.isEmpty()) {
                    isHudOpen = false;
                    fullDialogueText = "";
                    typewriterIndex = 0;
                    wrappedDialogueLines.clear();
                    currentOptions.clear();
                    qteTimeout = 0;
                } else {
                    isHudOpen = true;
                    fullDialogueText = rawText;
                    typewriterIndex = 0;
                    lastTypewriterTick = System.currentTimeMillis();
                    currentOptions = rawOptions;
                    selectedOptionIndex = 0;
                    qteTimeout = timeout;
                    qteStartTime = System.currentTimeMillis();
                    wrappedDialogueLines.clear();
                }
            });
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (isHudOpen && MinecraftClient.getInstance().currentScreen == null) {
                updateTypewriter();
                renderDialogueHud(drawContext);
            }
        });

        modeUpKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.simpledialogue.mode_up", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UP, "category.simpledialogue"));
        modeDownKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.simpledialogue.mode_down", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_DOWN, "category.simpledialogue"));

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            if (isHudOpen && client.currentScreen == null) {
                while (client.options.swapHandsKey.wasPressed()) {
                    if (!currentOptions.isEmpty()) {
                        ClientPlayNetworking.send(new SimpleDialogue.OptionSelectPayload(selectedOptionIndex));
                    }
                }
            }

            if (client.player.getMainHandStack().getItem() == SimpleDialogue.WAND) {
                while (modeUpKey.wasPressed()) {
                    ClientPlayNetworking.send(new SimpleDialogue.ModeSwitchPayload(true));
                    toggleClientMode();
                }
                while (modeDownKey.wasPressed()) {
                    ClientPlayNetworking.send(new SimpleDialogue.ModeSwitchPayload(false));
                    toggleClientMode();
                }
            }
        });

        registerWandRendering();
    }

    // 打字机逻辑
    private static void updateTypewriter() {
        if (typewriterIndex >= fullDialogueText.length()) return;

        long now = System.currentTimeMillis();
        if (now - lastTypewriterTick >= TYPEWRITER_DELAY_MS) {
            typewriterIndex++;
            lastTypewriterTick = now;

            String currentText = fullDialogueText.substring(0, typewriterIndex);
            TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
            wrappedDialogueLines = renderer.wrapLines(StringVisitable.plain(currentText), MAX_TEXT_WIDTH);
        }
    }

    public static boolean onScroll(double amount) {
        if (!isHudOpen || currentOptions.isEmpty()) return false;
        if (amount > 0) cycleOption(-1);
        else if (amount < 0) cycleOption(1);
        return true;
    }

    private static void cycleOption(int direction) {
        if (currentOptions.isEmpty()) return;
        selectedOptionIndex += direction;
        if (selectedOptionIndex >= currentOptions.size()) selectedOptionIndex = 0;
        else if (selectedOptionIndex < 0) selectedOptionIndex = currentOptions.size() - 1;
    }

    private void renderDialogueHud(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer font = client.textRenderer;

        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();

        int lineHeight = font.fontHeight + 3;
        int footerHeight = 15;
        int padding = 15;
        int progressBarHeight = (qteTimeout > 0) ? 10 : 0;

        int contentHeight = (wrappedDialogueLines.size() * lineHeight) + (currentOptions.size() * 14) + (padding * 2) + footerHeight + progressBarHeight;
        int contentWidth = MAX_TEXT_WIDTH + (padding * 2);

        MatrixStack matrices = context.getMatrices();
        matrices.push();

        int originX = screenWidth / 2;
        int originY = (screenHeight / 2) + 30;

        if (originY + (contentHeight * HUD_SCALE) > screenHeight - 10) {
            originY = (int) (screenHeight - (contentHeight * HUD_SCALE) - 10);
        }

        matrices.translate(originX, originY, 0);
        matrices.scale(HUD_SCALE, HUD_SCALE, 1.0f);
        matrices.translate(-contentWidth / 2.0f, 0, 0);

        int currentY = padding;
        int centerX = contentWidth / 2;

        for (OrderedText line : wrappedDialogueLines) {
            int lineWidth = font.getWidth(line);
            context.drawText(font, line, centerX - (lineWidth / 2), currentY, 0xFFFFFF, false);
            currentY += lineHeight;
        }

        currentY += 8;

        for (int i = 0; i < currentOptions.size(); i++) {
            String optText = currentOptions.get(i);
            String fullText;
            int color;

            if (i == selectedOptionIndex) {
                fullText = "▶ " + optText + " ◀";
                color = 0xFFFF55;
            } else {
                fullText = optText;
                color = 0xAAAAAA;
            }

            int optWidth = font.getWidth(fullText);
            context.drawText(font, fullText, centerX - (optWidth / 2), currentY, color, false);
            currentY += 14;
        }

        currentY += 8;

        // QTE进度条：100%-70%绿色，70%-30%黄色，30%-0%红色，渐变过渡
        if (qteTimeout > 0) {
            long elapsed = System.currentTimeMillis() - qteStartTime;
            float progress = 1.0f - Math.min(1.0f, (float) elapsed / (qteTimeout * 1000));

            int barWidth = contentWidth - (padding * 2);
            int barX = padding;
            int barY = currentY;

            // 背景（无阴影）
            context.fill(barX, barY, barX + barWidth, barY + 6, 0x80000000);

            // 根据进度计算颜色
            int color;
            if (progress > 0.7f) {
                // 100%-70%：绿色
                color = 0xFF00FF00;
            } else if (progress > 0.3f) {
                // 70%-30%：绿色到黄色渐变
                float t = (progress - 0.3f) / 0.4f;
                int r = (int) (255 * (1 - t));
                int g = 255;
                color = 0xFF000000 | (r << 16) | (g << 8);
            } else {
                // 30%-0%：黄色到红色渐变
                float t = progress / 0.3f;
                int g = (int) (255 * t);
                color = 0xFF000000 | (255 << 16) | (g << 8);
            }

            int filledWidth = (int) (barWidth * progress);
            context.fill(barX, barY, barX + filledWidth, barY + 6, color);

            currentY += 10;
        }

        String hint = "§7[滚轮] 选择  [F] 确认";
        int hintWidth = font.getWidth(hint);
        context.drawText(font, hint, centerX - (hintWidth / 2), currentY, 0x555555, false);

        matrices.pop();
    }

    private void registerWandRendering() {
        AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> {
            if (world.isClient && player.getMainHandStack().getItem() == SimpleDialogue.WAND) clientPos1 = pos;
            return ActionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient && hand == Hand.MAIN_HAND && player.getMainHandStack().getItem() == SimpleDialogue.WAND) {
                if (clientMode == DialogueWand.Mode.REGION) { clientPos2 = hitResult.getBlockPos(); }
                else { clientPos1 = hitResult.getBlockPos(); clientPos2 = null; }
            }
            return ActionResult.PASS;
        });

        WorldRenderEvents.LAST.register(context -> {
            PlayerEntity player = MinecraftClient.getInstance().player;
            if (player == null || player.getMainHandStack().getItem() != SimpleDialogue.WAND) return;
            float r = 1.0f, g = 0.4f, b = 0.85f;

            if (clientMode == DialogueWand.Mode.REGION) {
                if (clientPos1 != null && clientPos2 != null) renderSelectionBox(context, clientPos1, clientPos2, r, g, b);
                else if (clientPos1 != null) renderBlockBox(context, clientPos1, r, g, b);
            } else {
                if (clientPos1 != null) renderBlockBox(context, clientPos1, r, g, b);
            }
        });
    }

    private void toggleClientMode() {
        if (clientMode == DialogueWand.Mode.REGION) {
            clientMode = DialogueWand.Mode.BLOCK;
            clientPos2 = null;
        } else {
            clientMode = DialogueWand.Mode.REGION;
        }
    }

    private void renderSelectionBox(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context, BlockPos pos1, BlockPos pos2, float r, float g, float b) {
        double minX = Math.min(pos1.getX(), pos2.getX());
        double minY = Math.min(pos1.getY(), pos2.getY());
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxX = Math.max(pos1.getX(), pos2.getX()) + 1;
        double maxY = Math.max(pos1.getY(), pos2.getY()) + 1;
        double maxZ = Math.max(pos1.getZ(), pos2.getZ()) + 1;
        drawBox(context, new Box(minX, minY, minZ, maxX, maxY, maxZ), r, g, b);
    }

    private void renderBlockBox(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context, BlockPos pos, float r, float g, float b) {
        drawBox(context, new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX()+1, pos.getY()+1, pos.getZ()+1), r, g, b);
    }

    private void drawBox(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context, Box box, float r, float g, float b) {
        Vec3d c = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        matrices.push();
        matrices.translate(-c.x, -c.y, -c.z);
        VertexConsumer consumer = context.consumers().getBuffer(RenderLayer.getLines());

        MatrixStack.Entry entry = matrices.peek();

        float x1 = (float)box.minX, y1 = (float)box.minY, z1 = (float)box.minZ;
        float x2 = (float)box.maxX, y2 = (float)box.maxY, z2 = (float)box.maxZ;

        drawLine(consumer, entry, x1, y1, z1, x2, y1, z1, r, g, b);
        drawLine(consumer, entry, x1, y2, z1, x2, y2, z1, r, g, b);
        drawLine(consumer, entry, x1, y1, z2, x2, y1, z2, r, g, b);
        drawLine(consumer, entry, x1, y2, z2, x2, y2, z2, r, g, b);
        drawLine(consumer, entry, x1, y1, z1, x1, y2, z1, r, g, b);
        drawLine(consumer, entry, x2, y1, z1, x2, y2, z1, r, g, b);
        drawLine(consumer, entry, x1, y1, z2, x1, y2, z2, r, g, b);
        drawLine(consumer, entry, x2, y1, z2, x2, y2, z2, r, g, b);
        drawLine(consumer, entry, x1, y1, z1, x1, y1, z2, r, g, b);
        drawLine(consumer, entry, x2, y1, z1, x2, y1, z2, r, g, b);
        drawLine(consumer, entry, x1, y2, z1, x1, y2, z2, r, g, b);
        drawLine(consumer, entry, x2, y2, z1, x2, y2, z2, r, g, b);
        matrices.pop();
    }

    private void drawLine(VertexConsumer c, MatrixStack.Entry entry, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b) {
        c.vertex(entry.getPositionMatrix(), x1, y1, z1).color(r, g, b, 1.0F).normal(entry, 0.0F, 1.0F, 0.0F);
        c.vertex(entry.getPositionMatrix(), x2, y2, z2).color(r, g, b, 1.0F).normal(entry, 0.0F, 1.0F, 0.0F);
    }
}