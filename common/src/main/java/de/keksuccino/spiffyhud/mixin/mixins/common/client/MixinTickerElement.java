package de.keksuccino.spiffyhud.mixin.mixins.common.client;

import de.keksuccino.fancymenu.customization.element.elements.ticker.TickerElement;
import de.keksuccino.spiffyhud.customization.SpiffyGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TickerElement.class, remap = false)
public abstract class MixinTickerElement {

    @Unique
    private static final long MIN_TICK_INTERVAL_NANOS_SPIFFY = 50_000_000L;

    @Unique
    private long lastTickerTickNanos_Spiffy = 0L;

    /**
     * @reason FancyMenu drives synchronous tickers from the render loop. HUD data only
     * needs Minecraft's 20 TPS cadence, so avoid re-running action placeholders at FPS speed.
     */
    @Inject(method = "tickerElementTick", at = @At("HEAD"), cancellable = true)
    private void cancel_excessiveTickerTicks_Spiffy(CallbackInfo info) {
        if (!SpiffyGui.INSTANCE.isRenderingHudContext()) return;

        long now = System.nanoTime();
        if (this.lastTickerTickNanos_Spiffy != 0L
                && now - this.lastTickerTickNanos_Spiffy < MIN_TICK_INTERVAL_NANOS_SPIFFY) {
            info.cancel();
            return;
        }
        this.lastTickerTickNanos_Spiffy = now;
    }
}
