package app.revanced.integrations.youtube.patches.swipe;

import android.view.View;

import java.lang.ref.WeakReference;

import app.revanced.integrations.youtube.settings.Settings;

@SuppressWarnings("unused")
public class SwipeControlsPatch {
    private static WeakReference<View> fullscreenEngagementOverlayViewRef = new WeakReference<>(null);

    /** @noinspection deprecation*/
    public static boolean disableHDRAutoBrightness() {
        return Settings.DISABLE_HDR_AUTO_BRIGHTNESS.get();
    }

    public static boolean enableWatchPanelGestures() {
        return Settings.ENABLE_WATCH_PANEL_GESTURES.get();
    }

    /**
     * Injection point.
     *
     * @param fullscreenEngagementOverlayView R.layout.fullscreen_engagement_overlay
     */
    public static void setFullscreenEngagementOverlayView(View fullscreenEngagementOverlayView) {
        fullscreenEngagementOverlayViewRef = new WeakReference<>(fullscreenEngagementOverlayView);
    }

    public static boolean isEngagementOverlayVisible() {
        final View engagementOverlayView = fullscreenEngagementOverlayViewRef.get();
        return engagementOverlayView != null && engagementOverlayView.getVisibility() == View.VISIBLE;
    }

}
