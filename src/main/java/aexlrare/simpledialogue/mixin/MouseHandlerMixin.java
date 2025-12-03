package aexlrare.simpledialogue.mixin;

import aexlrare.simpledialogue.SimpleDialogueClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseHandlerMixin {

    @Inject(method = "onMouseScroll", at = @At(value = "HEAD"), cancellable = true)
    private void onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        // --- 调试日志：如果控制台没出现这句话，说明 Mixin 没加载成功 ---
        // System.out.println("Mixin 捕获到滚轮事件: " + vertical);

        if (vertical != 0) {
            // 调用 Client 端的逻辑
            boolean handled = SimpleDialogueClient.onScroll(vertical);

            if (handled) {
                // System.out.println("对话框已处理滚轮，取消原版事件");
                ci.cancel(); // 阻止原版快捷栏切换
            }
        }
    }
}
