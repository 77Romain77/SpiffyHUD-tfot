package de.keksuccino.spiffyhud.mixin.mixins.common.client;

import de.keksuccino.fancymenu.customization.element.elements.text.v2.TextElement;
import de.keksuccino.spiffyhud.customization.SpiffyGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TextElement.class, remap = false)
public abstract class MixinTextElement {

    @Unique
    private static final long MIN_TEXT_UPDATE_INTERVAL_NANOS_SPIFFY = 50_000_000L;

    @Unique
    private long lastTextUpdateNanos_Spiffy = 0L;

    /**
     * @reason Markdown state and dynamic text properties were synchronized every rendered
     * frame. Keep drawing every frame, but refresh their cached state at Minecraft tick speed.
     */
    @Inject(method = "renderTick", at = @At("HEAD"), cancellable = true)
    private void cancel_excessiveTextUpdates_Spiffy(CallbackInfo info) {
        if (!SpiffyGui.INSTANCE.isRenderingHudContext()) return;

        long now = System.nanoTime();
        if (this.lastTextUpdateNanos_Spiffy != 0L
                && now - this.lastTextUpdateNanos_Spiffy < MIN_TEXT_UPDATE_INTERVAL_NANOS_SPIFFY) {
            info.cancel();
            return;
        }
        this.lastTextUpdateNanos_Spiffy = now;
    }
}
