// android-ide/android/java/dev/androidide/IDEActivity.java
//
// Primary IDE Activity — replaces android.app.NativeActivity in AndroidManifest.xml.
//
// ── Architecture ─────────────────────────────────────────────────────────────
//
//   IDEActivity extends NativeActivity so Slint's android-activity 0.6 backend
//   continues to work unchanged. NativeActivity renders the Slint IDE chrome
//   (app bar, sidebar, tab bar, status bar) onto the window's native Surface,
//   which is always the BOTTOM layer in Android's compositing stack.
//
//   On top of that native Surface, this class adds a transparent FrameLayout
//   containing two WebViews:
//
//     mEditorWebView   Monaco Editor — visible whenever a file is open
//     mPreviewWebView  Preview panel — hidden by default; shown via showPreview()
//
//   Both WebViews sit inside mEditorPreviewContainer (horizontal LinearLayout).
//   When only the editor is shown, it takes 100% width (weight=1, other GONE).
//   When the preview is shown alongside, both split 50/50 (weight=1 each).
//
// ── Edit + Preview split ─────────────────────────────────────────────────────
//
//   Rust calls android::show_preview(url) via JNI to make the preview panel
//   visible. The user then sees Monaco on the left and the preview on the right
//   simultaneously — satisfying the "edit and preview at the same time" goal.
//   Rust calls android::hide_preview() to restore the full-width editor.
//
// ── WebView registration ─────────────────────────────────────────────────────
//
//   Registration is PULL-based (Rust pulls, not Java pushes):
//     1. IDEActivity.onCreate() → super.onCreate() loads the .so → returns
//     2. setupEditorOverlay() creates the WebView — no JNI call needed
//     3. android_main() (native thread, starts in onStart() AFTER onCreate())
//        → calls android_ide_editor::webview::android::init_webview_from_activity()
//        → that function calls IDEActivity.getInstance().getEditorWebView() via JNI
//        → stores GlobalRef in WEBVIEW_SENDER
//        → run_ui() can now use send_to_editor() ✓
//
//   This ordering is guaranteed by the Android Activity lifecycle:
//   onStart() (which triggers the native thread) is always called after
//   onCreate() returns. The WebView therefore exists before android_main runs.
//
// ── Overlay bounds ────────────────────────────────────────────────────────────
//
//   Default bounds match the Slint main.slint layout values:
//     top    = APP_BAR_HEIGHT + TAB_BAR_HEIGHT + SEPARATOR = 84dp
//     left   = 0 (portrait/narrow) or SIDEBAR_WIDTH + SEPARATOR = 241dp (landscape)
//     right  = 0 (fills to screen edge)
//     bottom = STATUS_BAR_HEIGHT = 22dp
//
//   Rust can call adjustEditorBounds() at runtime to fine-tune after the Slint
//   window has performed its first layout and the exact chrome dimensions are known.
//
// ── Lifecycle ─────────────────────────────────────────────────────────────────
//
//   configChanges in the manifest routes orientation changes to
//   onConfigurationChanged() instead of recreating the Activity.
//   The overlay bounds are recalculated on each orientation change.

package dev.androidide;

import android.app.NativeActivity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.lang.ref.WeakReference;

public final class IDEActivity extends NativeActivity {

    // ── Static weak reference ─────────────────────────────────────────────────
    // Exposed to Rust via getInstance() so android_main can pull the WebView.
    // WeakReference prevents this from leaking the Activity after onDestroy().
    private static WeakReference<IDEActivity> sInstance;

    // ── View hierarchy ────────────────────────────────────────────────────────
    private FrameLayout   mRootOverlay;
    private LinearLayout  mEditorPreviewContainer;
    private WebView       mEditorWebView;   // Monaco editor (always present)
    private WebView       mPreviewWebView;  // Preview panel (hidden by default)

    // ── Editor asset URL ─────────────────────────────────────────────────────
    // Monaco is bundled in APK assets, served via the file:///android_asset/ scheme.
    private static final String EDITOR_URL    = "file:///android_asset/editor/index.html";
    private static final String PREVIEW_BLANK = "about:blank";

    // ── Slint layout constants (must match ui/main.slint) ────────────────────
    // These define the IDE chrome dimensions so the WebView overlay does not
    // cover Slint's toolbar, sidebar, tab bar, or status bar.
    private static final int APP_BAR_DP       = 48;   // app-bar height
    private static final int TAB_BAR_DP       = 35;   // tab-bar height (when visible)
    private static final int STATUS_BAR_DP    = 22;   // status bar height
    private static final int SEPARATOR_DP     = 1;    // sidebar/tab-bar separator
    private static final int SIDEBAR_DP       = 240;  // sidebar width in non-narrow mode

    // Below this screen width (dp), the layout enters narrow/portrait mode:
    // the sidebar is hidden, WebView starts at left=0.
    private static final int NARROW_THRESHOLD_DP = 600;

    // ── Activity lifecycle ────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // super.onCreate() loads libandroid_ide_lib.so and fires JNI_OnLoad.
        // After this returns the JavaVM is stored and Rust JNI functions exist.
        // The native android_main() thread has NOT yet started (it starts in onStart()).
        super.onCreate(savedInstanceState);
        sInstance = new WeakReference<>(this);
        setupEditorOverlay();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        // NativeActivity handles the native/Slint side via super.
        super.onConfigurationChanged(newConfig);
        // Re-apply WebView margins for the new orientation.
        if (mEditorPreviewContainer != null) {
            applyDefaultMargins();
        }
    }

    @Override
    protected void onDestroy() {
        sInstance = null;
        super.onDestroy();
    }

    // ── Overlay construction ──────────────────────────────────────────────────

    private void setupEditorOverlay() {
        // Root overlay: transparent FrameLayout covering the whole window.
        mRootOverlay = new FrameLayout(this);

        // Horizontal split container for editor (and optional preview).
        mEditorPreviewContainer = new LinearLayout(this);
        mEditorPreviewContainer.setOrientation(LinearLayout.HORIZONTAL);
        mEditorPreviewContainer.setBackgroundColor(0x00000000); // transparent

        // Monaco editor WebView — always present, takes full width by default.
        mEditorWebView = buildWebView(EDITOR_URL, true /* attach EditorBridge */);
        LinearLayout.LayoutParams editorLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        mEditorPreviewContainer.addView(mEditorWebView, editorLp);

        // Preview WebView — hidden until showPreview() is called.
        // Has weight=1 so it shares 50% when made visible.
        mPreviewWebView = buildWebView(PREVIEW_BLANK, false /* no editor bridge */);
        mPreviewWebView.setVisibility(View.GONE);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        mEditorPreviewContainer.addView(mPreviewWebView, previewLp);

        // Add container to overlay and position it over the editor-container area.
        FrameLayout.LayoutParams containerLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        mRootOverlay.addView(mEditorPreviewContainer, containerLp);
        applyDefaultMargins();

        // Attach overlay on top of NativeActivity's native Surface.
        // Java Views always composite above the native Surface layer.
        getWindow().addContentView(mRootOverlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void applyDefaultMargins() {
        if (mEditorPreviewContainer == null) return;

        int widthDp = pxToDp(getResources().getDisplayMetrics().widthPixels);
        boolean isNarrow = widthDp < NARROW_THRESHOLD_DP;

        // Top: below the app bar and the tab bar (always reserve tab bar space even
        // if currently empty — avoids a layout jump when the first tab opens).
        int topPx    = dpToPx(APP_BAR_DP + TAB_BAR_DP + SEPARATOR_DP);
        int leftPx   = isNarrow ? 0 : dpToPx(SIDEBAR_DP + SEPARATOR_DP);
        int rightPx  = 0;
        int bottomPx = dpToPx(STATUS_BAR_DP);

        FrameLayout.LayoutParams lp =
                (FrameLayout.LayoutParams) mEditorPreviewContainer.getLayoutParams();
        if (lp == null) return;
        lp.setMargins(leftPx, topPx, rightPx, bottomPx);
        mEditorPreviewContainer.setLayoutParams(lp);
    }

    // ── WebView factory ───────────────────────────────────────────────────────

    private WebView buildWebView(String url, boolean attachEditorBridge) {
        WebView wv = new WebView(this);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        // Allow the Monaco AMD loader to load sibling files via file:// URLs.
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        // Monaco uses localStorage for some editor state.
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        // Monaco handles scrolling internally; hide system scrollbars.
        wv.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        wv.setHorizontalScrollBarEnabled(false);
        wv.setVerticalScrollBarEnabled(false);
        wv.setWebChromeClient(new WebChromeClient());
        if (attachEditorBridge) {
            // EditorBridge exposes window.AndroidBridge to JavaScript.
            // Inbound messages (JS → Rust) arrive via EditorBridge.onMessage().
            wv.addJavascriptInterface(new EditorBridge(), "AndroidBridge");
        }
        wv.loadUrl(url);
        return wv;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()));
    }

    private int pxToDp(int px) {
        return Math.round(px / getResources().getDisplayMetrics().density);
    }

    // ── Static API — called by Rust via JNI ──────────────────────────────────

    /**
     * Returns the current IDEActivity instance, or null if none is live.
     *
     * Called by Rust's init_webview_from_activity() during android_main() to
     * retrieve the Monaco WebView and register it as the WEBVIEW_SENDER.
     */
    public static IDEActivity getInstance() {
        return sInstance != null ? sInstance.get() : null;
    }

    /**
     * Returns the Monaco editor WebView.
     *
     * Safe to call after onCreate() has returned. Returns null only if
     * setupEditorOverlay() has not yet run — which cannot happen when called
     * from android_main() since android_main starts in onStart() (after onCreate).
     */
    public WebView getEditorWebView() {
        return mEditorWebView;
    }

    /**
     * Show the preview panel alongside Monaco (50% / 50% horizontal split).
     *
     * Called from Rust via JNI when the user requests a live preview,
     * e.g. rendered Markdown, an HTML output page, or a build result.
     *
     * Rust implementation: android_ide_editor::webview::android::show_preview(url)
     *
     * @param url  URL or file:// URI to display in the preview panel.
     *             Pass "about:blank" to show a blank preview panel.
     */
    public static void showPreview(String url) {
        IDEActivity a = sInstance != null ? sInstance.get() : null;
        if (a == null) return;
        a.runOnUiThread(() -> {
            if (a.mPreviewWebView == null) return;
            a.mPreviewWebView.loadUrl(url);
            a.mPreviewWebView.setVisibility(View.VISIBLE);
        });
    }

    /**
     * Hide the preview panel and restore Monaco to full editor width.
     *
     * Rust implementation: android_ide_editor::webview::android::hide_preview()
     */
    public static void hidePreview() {
        IDEActivity a = sInstance != null ? sInstance.get() : null;
        if (a == null) return;
        a.runOnUiThread(() -> {
            if (a.mPreviewWebView == null) return;
            a.mPreviewWebView.setVisibility(View.GONE);
        });
    }

    /**
     * Dynamically adjust the editor overlay bounds to match the actual Slint layout.
     *
     * Call this from Rust once Slint has completed its first layout and the exact
     * pixel dimensions of the IDE chrome elements are measured. Until this call,
     * the default compile-time constants (APP_BAR_DP, etc.) are used.
     *
     * All parameters are in density-independent pixels (dp).
     *
     * @param leftDp    Left margin  — sidebar width + separator (0 if sidebar hidden)
     * @param topDp     Top margin   — app bar + tab bar + separator
     * @param rightDp   Right margin — 0 unless a right-side panel is shown
     * @param bottomDp  Bottom margin — status bar height
     */
    public static void adjustEditorBounds(int leftDp, int topDp, int rightDp, int bottomDp) {
        IDEActivity a = sInstance != null ? sInstance.get() : null;
        if (a == null) return;
        a.runOnUiThread(() -> a.doAdjustEditorBounds(leftDp, topDp, rightDp, bottomDp));
    }

    private void doAdjustEditorBounds(int leftDp, int topDp, int rightDp, int bottomDp) {
        if (mEditorPreviewContainer == null) return;
        FrameLayout.LayoutParams lp =
                (FrameLayout.LayoutParams) mEditorPreviewContainer.getLayoutParams();
        if (lp == null) return;
        lp.setMargins(dpToPx(leftDp), dpToPx(topDp), dpToPx(rightDp), dpToPx(bottomDp));
        mEditorPreviewContainer.setLayoutParams(lp);
    }
}
