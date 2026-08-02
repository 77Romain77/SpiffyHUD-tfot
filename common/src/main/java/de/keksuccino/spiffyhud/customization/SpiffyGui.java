package de.keksuccino.spiffyhud.customization;

import com.mojang.blaze3d.systems.RenderSystem;
import de.keksuccino.fancymenu.customization.ScreenCustomization;
import de.keksuccino.fancymenu.customization.layer.ScreenCustomizationLayer;
import de.keksuccino.fancymenu.customization.layer.ScreenCustomizationLayerHandler;
import de.keksuccino.fancymenu.customization.layout.editor.LayoutEditorScreen;
import de.keksuccino.fancymenu.events.screen.*;
import de.keksuccino.fancymenu.util.event.acara.EventHandler;
import de.keksuccino.fancymenu.util.rendering.RenderingUtils;
import de.keksuccino.spiffyhud.util.profiling.SpiffyProfiler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SpiffyGui implements Renderable {

    public static final SpiffyGui INSTANCE = new SpiffyGui();

    private static final Logger LOGGER = LogManager.getLogger();

    private static boolean initialized = false;
    private static SpiffyOverlayScreen spiffyOverlayScreen = new SpiffyOverlayScreen(false);

    private boolean renderingHudContext = false;
    private ScreenCustomizationLayer cachedLayer;

    private SpiffyGui() {

        if (!initialized) {
            //TODO init stuff here if needed
            initialized = true;
        }

        this.setNewOverlayScreen();
        this.initOverlayScreen(false);
        this.tick();

    }


    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partial) {

        long profilerStart = SpiffyProfiler.beginRenderSample();
        try {
            if (!this.shouldRenderCustomizations()) return;

            this.runLayerTask(() -> {

                this.restoreRenderDefaults(graphics);

                EventHandler.INSTANCE.postEvent(new RenderScreenEvent.Pre(spiffyOverlayScreen, graphics, mouseX, mouseY, partial));
                spiffyOverlayScreen.render(graphics, mouseX, mouseY, partial);
                this.restoreRenderDefaults(graphics);
                EventHandler.INSTANCE.postEvent(new RenderScreenEvent.Post(spiffyOverlayScreen, graphics, mouseX, mouseY, partial));

                this.restoreRenderDefaults(graphics);

            });
        } finally {
            SpiffyProfiler.endRenderSample(profilerStart);
            SpiffyProfiler.render(graphics);
        }

    }

    private void restoreRenderDefaults(@NotNull GuiGraphics graphics) {
        RenderingUtils.resetShaderColor(graphics);
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
    }

    private boolean shouldRenderCustomizations() {
        if (Minecraft.getInstance().screen instanceof LayoutEditorScreen) return false;
        return (spiffyOverlayScreen != null) && (this.getLayer() != null);
    }

    private boolean shouldTickCustomizations() {
        if (Minecraft.getInstance().screen instanceof LayoutEditorScreen) return false;
        return (spiffyOverlayScreen != null) && (this.getLayer() != null);
    }

    @Nullable
    private ScreenCustomizationLayer getLayer() {
        if (spiffyOverlayScreen == null) return null;

        if (this.cachedLayer == null) {
            this.cachedLayer = ScreenCustomizationLayerHandler.getLayerOfScreen(spiffyOverlayScreen);
            if (this.cachedLayer != null) {
                this.cachedLayer.loadEarly = true;
            }
        }

        return this.cachedLayer;
    }

    private void invalidateLayerCache() {
        this.cachedLayer = null;
    }

    @NotNull
    public SpiffyOverlayScreen getOverlayScreen() {
        return spiffyOverlayScreen;
    }

    public boolean isRenderingHudContext() {
        return this.renderingHudContext;
    }

    public void onResize() {
        try {
            this.invalidateLayerCache();
            this.initOverlayScreen(true);
            this.getLayer();
        } catch (Exception ex) {
            LOGGER.error("[SPIFFY HUD] Failed to resize SpiffyGui!", ex);
        }
    }

    public void tick() {
        SpiffyProfiler.tickShortcut();
        long profilerStart = SpiffyProfiler.beginTickSample();

        try {
            if (Shared.reInitHudLayouts) {
                Shared.reInitHudLayouts = false;
                this.invalidateLayerCache();
                this.initOverlayScreen(true);
                this.getLayer();
            }

            if (this.shouldTickCustomizations()) {
                this.runLayerTask(() -> {
                    EventHandler.INSTANCE.postEvent(new ScreenTickEvent.Pre(spiffyOverlayScreen));
                    spiffyOverlayScreen.tick();
                    EventHandler.INSTANCE.postEvent(new ScreenTickEvent.Post(spiffyOverlayScreen));
                });
            }
        } catch (Exception ex) {
            LOGGER.error("[SPIFFY HUD] Failed to tick SpiffyGui!", ex);
        } finally {
            SpiffyProfiler.endTickSample(profilerStart);
        }
    }

    private void setNewOverlayScreen() {
        spiffyOverlayScreen = new SpiffyOverlayScreen(false);
        this.invalidateLayerCache();
        ScreenCustomizationLayerHandler.registerScreen(spiffyOverlayScreen);
        this.getLayer(); //dummy call to let the method set loadEarly to true
    }

    private void initOverlayScreen(boolean resize) {
        this.runLayerTask(() -> {
            try {
                double cachedScale = Minecraft.getInstance().getWindow().getGuiScale();
                if (!resize) {
                    EventHandler.INSTANCE.postEvent(new OpenScreenEvent(spiffyOverlayScreen));
                }
                spiffyOverlayScreen.width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
                spiffyOverlayScreen.height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
                EventHandler.INSTANCE.postEvent(new InitOrResizeScreenStartingEvent(spiffyOverlayScreen, resize ? InitOrResizeScreenEvent.InitializationPhase.RESIZE : InitOrResizeScreenEvent.InitializationPhase.INIT));
                EventHandler.INSTANCE.postEvent(new InitOrResizeScreenEvent.Pre(spiffyOverlayScreen, resize ? InitOrResizeScreenEvent.InitializationPhase.RESIZE : InitOrResizeScreenEvent.InitializationPhase.INIT));
                spiffyOverlayScreen.init(Minecraft.getInstance(), spiffyOverlayScreen.width, spiffyOverlayScreen.height);
                EventHandler.INSTANCE.postEvent(new InitOrResizeScreenEvent.Post(spiffyOverlayScreen, resize ? InitOrResizeScreenEvent.InitializationPhase.RESIZE : InitOrResizeScreenEvent.InitializationPhase.INIT));
                EventHandler.INSTANCE.postEvent(new InitOrResizeScreenCompletedEvent(spiffyOverlayScreen, resize ? InitOrResizeScreenEvent.InitializationPhase.RESIZE : InitOrResizeScreenEvent.InitializationPhase.INIT));
                if (!resize) {
                    EventHandler.INSTANCE.postEvent(new OpenScreenPostInitEvent(spiffyOverlayScreen));
                }
                //This is to ignore scale changes applied by Spiffy layouts (because scaling not supported)
                Minecraft.getInstance().getWindow().setGuiScale(cachedScale);
            } catch (Exception ex) {
                LOGGER.error("[SPIFFY HUD] Failed to initialize SpiffyOverlayScreen!", ex);
            }
        });
    }

    private void runLayerTask(@NotNull Runnable run) {
        boolean customizationEnabled = ScreenCustomization.isScreenCustomizationEnabled();
        Screen current = Minecraft.getInstance().screen;
        boolean swappedScreen = false;

        try {
            ScreenCustomization.setScreenCustomizationEnabled(true);

            if (!(current instanceof SpiffyOverlayScreen)) {
                Minecraft.getInstance().screen = spiffyOverlayScreen;
                this.renderingHudContext = true;
                swappedScreen = true;
                run.run();
            }
        } catch (Exception ex) {
            LOGGER.error("[SPIFFY HUD] Failed to run layer task!", ex);
        } finally {
            if (swappedScreen) {
                this.renderingHudContext = false;
                Minecraft.getInstance().screen = current;
            }
            ScreenCustomization.setScreenCustomizationEnabled(customizationEnabled);
        }
    }

}
