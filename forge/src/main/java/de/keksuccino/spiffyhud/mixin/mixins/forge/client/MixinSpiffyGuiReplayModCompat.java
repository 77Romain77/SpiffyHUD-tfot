package de.keksuccino.spiffyhud.mixin.mixins.forge.client;

import de.keksuccino.spiffyhud.customization.SpiffyGui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Keeps SpiffyHUD out of ReplayMod's replay editor/playback view without
 * introducing a hard dependency on ReplayMod.
 */
@Mixin(SpiffyGui.class)
public abstract class MixinSpiffyGuiReplayModCompat {

    @Unique
    private static final String SPIFFYHUD$REPLAY_MOD_CLASS = "com.replaymod.replay.ReplayModReplay";

    @Unique
    private static volatile boolean spiffyhud$replayLookupDone = false;

    @Unique
    private static Field spiffyhud$replayInstanceField;

    @Unique
    private static Method spiffyhud$getReplayHandlerMethod;

    @Unique
    private static Object spiffyhud$replayInstance;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void spiffyhud$hideDuringReplay(GuiGraphics graphics, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        if (spiffyhud$isReplayActive()) {
            ci.cancel();
        }
    }

    @Unique
    private static boolean spiffyhud$isReplayActive() {
        spiffyhud$resolveReplayMod();
        if (spiffyhud$replayInstanceField == null || spiffyhud$getReplayHandlerMethod == null) {
            return false;
        }

        try {
            if (spiffyhud$replayInstance == null) {
                spiffyhud$replayInstance = spiffyhud$replayInstanceField.get(null);
            }
            return spiffyhud$replayInstance != null
                    && spiffyhud$getReplayHandlerMethod.invoke(spiffyhud$replayInstance) != null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // ReplayMod is optional. Any incompatibility must leave SpiffyHUD unchanged.
            return false;
        }
    }

    @Unique
    private static void spiffyhud$resolveReplayMod() {
        if (spiffyhud$replayLookupDone) {
            return;
        }

        synchronized (MixinSpiffyGuiReplayModCompat.class) {
            if (spiffyhud$replayLookupDone) {
                return;
            }

            try {
                Class<?> replayModReplay = Class.forName(
                        SPIFFYHUD$REPLAY_MOD_CLASS,
                        false,
                        MixinSpiffyGuiReplayModCompat.class.getClassLoader()
                );
                spiffyhud$replayInstanceField = replayModReplay.getField("instance");
                spiffyhud$getReplayHandlerMethod = replayModReplay.getMethod("getReplayHandler");
            } catch (ReflectiveOperationException | LinkageError ignored) {
                spiffyhud$replayInstanceField = null;
                spiffyhud$getReplayHandlerMethod = null;
            } finally {
                spiffyhud$replayLookupDone = true;
            }
        }
    }
}
