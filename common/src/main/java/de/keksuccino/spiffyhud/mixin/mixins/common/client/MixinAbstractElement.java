package de.keksuccino.spiffyhud.mixin.mixins.common.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import de.keksuccino.fancymenu.customization.element.AbstractElement;
import de.keksuccino.spiffyhud.customization.SpiffyGui;
import de.keksuccino.spiffyhud.debug.AdaptiveHudCacheAccess;
import de.keksuccino.spiffyhud.debug.HudElementProfiler;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractElement.class, remap = false)
public abstract class MixinAbstractElement {

    @Unique
    private long spiffyHud$profileStartedAt = 0L;

    @Unique
    private boolean spiffyHud$profileThisRender = false;

    @Inject(method = "renderInternal", at = @At("HEAD"))
    private void startElementProfile_Spiffy(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partial,
            CallbackInfo info
    ) {
        if (SpiffyGui.INSTANCE instanceof AdaptiveHudCacheAccess cache
                && cache.isRenderingHudCache_Spiffy()) {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
            );
        }

        this.spiffyHud$profileThisRender = HudElementProfiler.isActive()
                && SpiffyGui.INSTANCE.isRenderingHudContext();
        if (this.spiffyHud$profileThisRender) {
            this.spiffyHud$profileStartedAt = System.nanoTime();
        }
    }

    @Inject(method = "renderInternal", at = @At("RETURN"))
    private void finishElementProfile_Spiffy(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partial,
            CallbackInfo info
    ) {
        if (!this.spiffyHud$profileThisRender) return;

        long elapsedNanos = System.nanoTime() - this.spiffyHud$profileStartedAt;
        this.spiffyHud$profileThisRender = false;
        HudElementProfiler.recordElement((AbstractElement) (Object) this, elapsedNanos);
    }
}
