package de.keksuccino.spiffyhud.mixin.mixins.common.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import de.keksuccino.fancymenu.customization.layer.ScreenCustomizationLayer;
import de.keksuccino.fancymenu.events.screen.RenderScreenEvent;
import de.keksuccino.fancymenu.util.event.acara.EventHandler;
import de.keksuccino.spiffyhud.customization.SpiffyGui;
import de.keksuccino.spiffyhud.customization.SpiffyOverlayScreen;
import de.keksuccino.spiffyhud.debug.AdaptiveHudCacheAccess;
import de.keksuccino.spiffyhud.debug.HudDebugOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SpiffyGui.class, remap = false)
public abstract class MixinSpiffyGuiAdaptiveCache implements AdaptiveHudCacheAccess {

    @Unique
    private static final Logger LOGGER_SPIFFY = LogManager.getLogger();

    @Unique
    private static final long STATS_INTERVAL_NANOS_SPIFFY = 1_000_000_000L;

    @Unique
    private static final double FPS_DIVISOR_SPIFFY = 3.0D;

    @Unique
    private static final int MIN_CACHED_HUD_FPS_SPIFFY = 30;

    @Unique
    private static final int MAX_CACHED_HUD_FPS_SPIFFY = 60;

    @Unique
    private static final double CACHE_DISABLE_BELOW_FPS_SPIFFY = 45.0D;

    @Unique
    private TextureTarget hudCacheTarget_Spiffy = null;

    @Unique
    private ScreenCustomizationLayer cachedLayerIdentity_Spiffy = null;

    @Unique
    private boolean hudCacheValid_Spiffy = false;

    @Unique
    private boolean hudCacheDisabled_Spiffy = false;

    @Unique
    private boolean warnedAboutCacheFailure_Spiffy = false;

    @Unique
    private boolean renderingHudCache_Spiffy = false;

    @Unique
    private long lastHudCacheRenderNanos_Spiffy = 0L;

    @Unique
    private long fpsWindowStartedNanos_Spiffy = System.nanoTime();

    @Unique
    private int fpsWindowFrames_Spiffy = 0;

    @Unique
    private double observedGameFps_Spiffy = 60.0D;

    @Unique
    private int targetHudFps_Spiffy = 30;

    @Unique
    private long statsWindowStartedNanos_Spiffy = System.nanoTime();

    @Unique
    private int windowHudRenders_Spiffy = 0;

    @Unique
    private int windowHudReuses_Spiffy = 0;

    @Unique
    private int displayedHudRenders_Spiffy = 0;

    @Unique
    private int displayedHudReuses_Spiffy = 0;

    @Shadow
    private abstract boolean shouldRenderCustomizations();

    @Shadow
    private abstract void restoreRenderDefaults(GuiGraphics graphics);

    @Shadow
    private abstract void runLayerTask(Runnable run);

    /**
     * @reason Render the expensive FancyMenu HUD into a transparent texture at an adaptive
     * 30-60 FPS cadence, then composite that cached texture on every game frame.
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void renderAdaptiveHudCache_Spiffy(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partial,
            CallbackInfo info
    ) {
        if (this.hudCacheDisabled_Spiffy || !this.shouldRenderCustomizations()) return;

        long renderStartedAt = System.nanoTime();
        long now = renderStartedAt;
        this.observeGameFrame_Spiffy(now);

        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        ScreenCustomizationLayer currentLayer = SpiffyGui.INSTANCE.getCustomizationLayer();
        if (currentLayer != this.cachedLayerIdentity_Spiffy) {
            this.cachedLayerIdentity_Spiffy = currentLayer;
            this.hudCacheValid_Spiffy = false;
        }

        try {
            this.ensureCacheTarget_Spiffy(mainTarget);
            if (this.shouldRefreshHudCache_Spiffy(now)) {
                this.renderHudIntoCache_Spiffy(graphics, mouseX, mouseY, partial, mainTarget);
                this.lastHudCacheRenderNanos_Spiffy = now;
                this.hudCacheValid_Spiffy = true;
                this.windowHudRenders_Spiffy++;
            } else {
                this.windowHudReuses_Spiffy++;
            }

            this.compositeCachedHud_Spiffy(graphics);
            this.updateDisplayedStats_Spiffy(now);

            HudDebugOverlay.recordRender(System.nanoTime() - renderStartedAt);
            HudDebugOverlay.render(graphics);
            info.cancel();
        } catch (Throwable throwable) {
            this.disableCacheAfterFailure_Spiffy(mainTarget, throwable);
        }
    }

    @Unique
    private void observeGameFrame_Spiffy(long now) {
        this.fpsWindowFrames_Spiffy++;
        long elapsed = now - this.fpsWindowStartedNanos_Spiffy;
        if (elapsed < STATS_INTERVAL_NANOS_SPIFFY) return;

        double measuredFps = this.fpsWindowFrames_Spiffy * 1_000_000_000.0D / Math.max(1L, elapsed);
        this.observedGameFps_Spiffy = this.observedGameFps_Spiffy * 0.35D + measuredFps * 0.65D;
        this.targetHudFps_Spiffy = this.calculateTargetHudFps_Spiffy();
        this.fpsWindowFrames_Spiffy = 0;
        this.fpsWindowStartedNanos_Spiffy = now;
    }

    @Unique
    private int calculateTargetHudFps_Spiffy() {
        if (this.observedGameFps_Spiffy <= CACHE_DISABLE_BELOW_FPS_SPIFFY) {
            return Math.max(1, (int) Math.round(this.observedGameFps_Spiffy));
        }
        int divided = (int) Math.round(this.observedGameFps_Spiffy / FPS_DIVISOR_SPIFFY);
        return Math.max(MIN_CACHED_HUD_FPS_SPIFFY, Math.min(MAX_CACHED_HUD_FPS_SPIFFY, divided));
    }

    @Unique
    private boolean shouldRefreshHudCache_Spiffy(long now) {
        if (!this.hudCacheValid_Spiffy) return true;
        if (this.observedGameFps_Spiffy <= CACHE_DISABLE_BELOW_FPS_SPIFFY) return true;

        long intervalNanos = 1_000_000_000L / Math.max(1, this.targetHudFps_Spiffy);
        return now - this.lastHudCacheRenderNanos_Spiffy >= intervalNanos;
    }

    @Unique
    private void ensureCacheTarget_Spiffy(RenderTarget mainTarget) {
        if (this.hudCacheTarget_Spiffy == null) {
            this.hudCacheTarget_Spiffy = new TextureTarget(
                    mainTarget.width,
                    mainTarget.height,
                    true,
                    Minecraft.ON_OSX
            );
            this.hudCacheTarget_Spiffy.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            this.hudCacheValid_Spiffy = false;
            return;
        }

        if (this.hudCacheTarget_Spiffy.width != mainTarget.width
                || this.hudCacheTarget_Spiffy.height != mainTarget.height) {
            this.hudCacheTarget_Spiffy.resize(mainTarget.width, mainTarget.height, Minecraft.ON_OSX);
            this.hudCacheTarget_Spiffy.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            this.hudCacheValid_Spiffy = false;
        }
    }

    @Unique
    private void renderHudIntoCache_Spiffy(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partial,
            RenderTarget mainTarget
    ) {
        graphics.flush();
        this.hudCacheTarget_Spiffy.bindWrite(true);
        this.hudCacheTarget_Spiffy.clear(Minecraft.ON_OSX);
        this.hudCacheTarget_Spiffy.bindWrite(true);

        this.renderingHudCache_Spiffy = true;
        try {
            this.runLayerTask(() -> this.renderHudLayer_Spiffy(graphics, mouseX, mouseY, partial));
            graphics.flush();
        } finally {
            this.renderingHudCache_Spiffy = false;
            mainTarget.bindWrite(true);
            this.restoreRenderDefaults(graphics);
        }
    }

    @Unique
    private void renderHudLayer_Spiffy(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        SpiffyOverlayScreen overlay = SpiffyGui.INSTANCE.getOverlayScreen();
        this.restoreRenderDefaults(graphics);
        this.setPremultipliedTargetBlend_Spiffy();

        EventHandler.INSTANCE.postEvent(new RenderScreenEvent.Pre(overlay, graphics, mouseX, mouseY, partial));
        overlay.render(graphics, mouseX, mouseY, partial);
        this.restoreRenderDefaults(graphics);
        EventHandler.INSTANCE.postEvent(new RenderScreenEvent.Post(overlay, graphics, mouseX, mouseY, partial));
        this.restoreRenderDefaults(graphics);
    }

    @Unique
    private void compositeCachedHud_Spiffy(GuiGraphics graphics) {
        graphics.flush();

        Minecraft minecraft = Minecraft.getInstance();
        float width = minecraft.getWindow().getGuiScaledWidth();
        float height = minecraft.getWindow().getGuiScaledHeight();
        Matrix4f matrix = graphics.pose().last().pose();

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        );
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, this.hudCacheTarget_Spiffy.getColorTextureId());

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(matrix, 0.0F, height, 0.0F).uv(0.0F, 0.0F).endVertex();
        builder.vertex(matrix, width, height, 0.0F).uv(1.0F, 0.0F).endVertex();
        builder.vertex(matrix, width, 0.0F, 0.0F).uv(1.0F, 1.0F).endVertex();
        builder.vertex(matrix, 0.0F, 0.0F, 0.0F).uv(0.0F, 1.0F).endVertex();
        BufferUploader.drawWithShader(builder.end());

        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Unique
    private void setPremultipliedTargetBlend_Spiffy() {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        );
    }

    @Unique
    private void updateDisplayedStats_Spiffy(long now) {
        long elapsed = now - this.statsWindowStartedNanos_Spiffy;
        if (elapsed < STATS_INTERVAL_NANOS_SPIFFY) return;

        this.displayedHudRenders_Spiffy = this.windowHudRenders_Spiffy;
        this.displayedHudReuses_Spiffy = this.windowHudReuses_Spiffy;
        this.windowHudRenders_Spiffy = 0;
        this.windowHudReuses_Spiffy = 0;
        this.statsWindowStartedNanos_Spiffy = now;
    }

    @Unique
    private void disableCacheAfterFailure_Spiffy(RenderTarget mainTarget, Throwable throwable) {
        this.renderingHudCache_Spiffy = false;
        this.hudCacheValid_Spiffy = false;
        this.hudCacheDisabled_Spiffy = true;
        mainTarget.bindWrite(true);

        if (this.hudCacheTarget_Spiffy != null) {
            try {
                this.hudCacheTarget_Spiffy.destroyBuffers();
            } catch (Throwable ignored) {
            }
            this.hudCacheTarget_Spiffy = null;
        }

        if (!this.warnedAboutCacheFailure_Spiffy) {
            this.warnedAboutCacheFailure_Spiffy = true;
            LOGGER_SPIFFY.warn("[SPIFFY HUD] Adaptive HUD cache failed; falling back to normal rendering.", throwable);
        }
    }

    @Override
    public boolean isRenderingHudCache_Spiffy() {
        return this.renderingHudCache_Spiffy;
    }

    @Override
    public int getTargetHudFps_Spiffy() {
        return this.targetHudFps_Spiffy;
    }

    @Override
    public int getCachedHudRenders_Spiffy() {
        return this.displayedHudRenders_Spiffy;
    }

    @Override
    public int getCachedHudReuses_Spiffy() {
        return this.displayedHudReuses_Spiffy;
    }
}
