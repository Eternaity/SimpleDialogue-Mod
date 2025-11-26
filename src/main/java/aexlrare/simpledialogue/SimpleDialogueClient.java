package aexlrare.simpledialogue;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class SimpleDialogueClient implements ClientModInitializer {

    private static final boolean[] keyStates = new boolean[9];

    @Override
    public void onInitializeClient() {
        // 打印一条日志，证明客户端代码加载了
        System.out.println("SimpleDialogueClient 已初始化！正在监听 Alt+数字键...");

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            long handle = client.getWindow().getHandle();
            boolean isAltDown = InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_LEFT_ALT) ||
                    InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_RIGHT_ALT);

            for (int i = 0; i < 9; i++) {
                int keyCode = GLFW.GLFW_KEY_1 + i;
                boolean isPressedNow = InputUtil.isKeyPressed(handle, keyCode);

                if (isPressedNow && !keyStates[i]) {
                    if (isAltDown) {
                        System.out.println("[SD Client] 检测到按键: Alt + " + (i + 1)); // 调试日志
                        ClientPlayNetworking.send(new SimpleDialogue.OptionSelectPayload(i));
                    }
                }
                keyStates[i] = isPressedNow;
            }
        });
    }
}
