package de.keksuccino.spiffyhud.mixin.mixins.common.client;

import com.mojang.blaze3d.systems.RenderSystem;
import de.keksuccino.fancymenu.customization.element.elements.image.ImageElement;
import de.keksuccino.fancymenu.util.rendering.DrawableColor;
import de.keksuccino.fancymenu.util.resource.resources.texture.ITexture;
import de.keksuccino.spiffyhud.customization.SpiffyGui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ImageElement.class, remap = false)
public abstract class MixinImageElement {

    @Unique
    private static final long MIN_IMAGE_UPDATE_INTERVAL_NANOS_SPIFFY = 50_000_000L;

    @Unique
    private long lastImageTintUpdateNanos_Spiffy = 0L;

    @Unique
    private long lastRoundingUpdateNanos_Spiffy = 0L;

    @Unique
    private long cachedTextureAtNanos_Spiffy = 0L;

    @Unique
    private boolean textureCacheInitialized_Spiffy = false;

    @Unique
    private ITexture cachedTexture_Spiffy = null;

    @Shadow
    protected DrawableColor currentImageTint;

    @Shadow
    protected float resolvedRoundingRadiusTopLeft;

    @Shadow
    protected float resolvedRoundingRadiusTopRight;

    @Shadow
    protected float resolvedRoundingRadiusBottomRight;

    @Shadow
    protected float resolvedRoundingRadiusBottomLeft;

    @Shadow
    protected abstract void tickImageTint();

    @Shadow
    protected abstract void tickRoundingRadius();

    /**
     * @reason Tint placeholders do not need to be re-evaluated hundreds of times per second.
     */
    @Inject(method = "tickImageTint", at = @At("HEAD"), cancellable = true)
    private void cancel_excessiveTintUpdates_Spiffy(CallbackInfo info) {
        if (!SpiffyGui.INSTANCE.isRenderingHudContext()) return;

        long now = System.nanoTime();
        if (this.lastImageTintUpdateNanos_Spiffy != 0L
                && now - this.lastImageTintUpdateNanos_Spiffy < MIN_IMAGE_UPDATE_INTERVAL_NANOS_SPIFFY) {
            info.cancel();
            return;
        }
        this.lastImageTintUpdateNanos_Spiffy = now;
    }

    /**
     * @reason Corner radius properties are normally static and are safe to refresh at 20 TPS.
     */
    @Inject(method = "tickRoundingRadius", at = @At("HEAD"), cancellable = true)
    private void cancel_excessiveRoundingUpdates_Spiffy(CallbackInfo info) {
        if (!SpiffyGui.INSTANCE.isRenderingHudContext()) return;

        long now = System.nanoTime();
        if (this.lastRoundingUpdateNanos_Spiffy != 0L
                && now - this.lastRoundingUpdateNanos_Spiffy < MIN_IMAGE_UPDATE_INTERVAL_NANOS_SPIFFY) {
            info.cancel();
            return;
        }
        this.lastRoundingUpdateNanos_Spiffy = now;
    }

    @Inject(method = "getTextureResource", at = @At("HEAD"), cancellable = true)
    private void useCachedTexture_Spiffy(CallbackInfoReturnable<ITexture> info) {
        if (!SpiffyGui.INSTANCE.isRenderingHudContext() || !this.textureCacheInitialized_Spiffy) return;

        long now = System.nanoTime();
        if (now - this.cachedTextureAtNanos_Spiffy < MIN_IMAGE_UPDATE_INTERVAL_NANOS_SPIFFY) {
            info.setReturnValue(this.cachedTexture_Spiffy);
        }
    }

    @Inject(method = "getTextureResource", at = @At("RETURN"))
    private void cacheTexture_Spiffy(CallbackInfoReturnable<ITexture> info) {
        if (!SpiffyGui.INSTANCE.isRenderingHudContext()) return;

        this.cachedTexture_Spiffy = info.getReturnValue();
        this.cachedTextureAtNanos_Spiffy = System.nanoTime();
        this.textureCacheInitialized_Spiffy = true;
    }

    @Inject(method = "onOpenScreen", at = @At("HEAD"))
    private void invalidateImageCache_Spiffy(CallbackInfo info) {
        this.lastImageTintUpdateNanos_Spiffy = 0L;
        this.lastRoundingUpdateNanos_Spiffy = 0L;
        this.cachedTextureAtNanos_Spiffy = 0L;
        this.textureCacheInitialized_Spiffy = false;
        this.cachedTexture_Spiffy = null;
    }

    /**
     * @reason FancyMenu uses its smooth rounded-rectangle renderer even when all four
     * radii are zero. Plain HUD images can use Minecraft's much cheaper textured blit.
     */
    @Inject(
            method = {"render", "method_25394"},
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void renderPlainImageFastPath_Spiffy(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partial,
            CallbackInfo info
    ) {
        if (!SpiffyGui.INSTANCE.isRenderingHudContext()) return;

        ImageElement image = (ImageElement) (Object) this;
        if (!image.shouldRender() || image.repeat.getBoolean() || image.nineSlice.getBoolean()) return;

        this.tickImageTint();
        this.tickRoundingRadius();
        if (this.currentImageTint == null || !this.hasSquareCorners_Spiffy()) return;

        ITexture texture = image.getTextureResource();
        if (texture == null || !texture.isReady() || texture.getResourceLocation() == null) return;

        int width = image.getAbsoluteWidth();
        int height = image.getAbsoluteHeight();
        if (width <= 0 || height <= 0) return;

        int textureWidth = Math.max(1, texture.getWidth());
        int textureHeight = Math.max(1, texture.getHeight());

        RenderSystem.enableBlend();
        this.currentImageTint.setAsShaderColor(graphics, Mth.clamp(image.opacity, 0.0F, 1.0F));
        graphics.blit(
                texture.getResourceLocation(),
                image.getAbsoluteX(),
                image.getAbsoluteY(),
                0.0F,
                0.0F,
                width,
                height,
                textureWidth,
                textureHeight
        );
        this.currentImageTint.resetShaderColor(graphics);
        RenderSystem.disableBlend();
        info.cancel();
    }

    @Unique
    private boolean hasSquareCorners_Spiffy() {
        return this.resolvedRoundingRadiusTopLeft == 0.0F
                && this.resolvedRoundingRadiusTopRight == 0.0F
                && this.resolvedRoundingRadiusBottomRight == 0.0F
                && this.resolvedRoundingRadiusBottomLeft == 0.0F;
    }
}
