package de.keksuccino.spiffyhud.debug;

public interface AdaptiveHudCacheAccess {

    boolean isRenderingHudCache_Spiffy();

    int getTargetHudFps_Spiffy();

    int getCachedHudRenders_Spiffy();

    int getCachedHudReuses_Spiffy();
}
