package io.github.some_example_name.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;
import io.github.some_example_name.Main;
import io.github.some_example_name.assets.ProceduralAssets;
import io.github.some_example_name.config.GameConfig;
import io.github.some_example_name.config.Palette;
import io.github.some_example_name.core.GameSettings;
import io.github.some_example_name.ui.Button;
import io.github.some_example_name.ui.SettingsWidgets;
import io.github.some_example_name.ui.SettingsWidgets.Radio;
import io.github.some_example_name.ui.SettingsWidgets.Slider;
import io.github.some_example_name.ui.SettingsWidgets.Toggle;
import io.github.some_example_name.ui.UIDraw;

import java.util.ArrayList;
import java.util.List;

/**
 * Tabbed settings screen modelled after the Config-and-Lobby design.
 *
 * <p>Sidebar with four tabs (AUDIO / GRAPHICS / CONTROLS / GAME).  Each tab
 * shows setting rows: a label + hint on the left, an input widget on the
 * right.  Widgets are {@link Toggle}, {@link Slider}, and {@link Radio} from
 * {@link SettingsWidgets}.  Top + bottom bars frame the whole screen.</p>
 *
 * <p>The screen always renders straight to the back buffer — no retro
 * post-process pass.  The pixelated filter only kicks in on the match
 * screens.</p>
 */
public final class ConfigScreen extends BaseScreen {

    /** Tab identifiers — kept tight so the sidebar stays one column. */
    private enum Tab { AUDIO, GRAPHICS, CONTROLS, GAME }

    private static final float SIDEBAR_W = 200f;
    private static final float TOP_BAR_H = 36f;
    private static final float BOT_BAR_H = 36f;

    /** Vertical step between rows. */
    private static final float ROW_STEP  = 60f;
    /** Total height of one row's content (label + hint + bottom divider). */
    private static final float ROW_H     = 44f;
    /** Right edge that all widgets right-align to. */
    private static final float WIDGET_RIGHT = GameConfig.WORLD_WIDTH - 64f;

    private final InputHandler input = new InputHandler();
    private final Vector3 cursorWorld = new Vector3();
    private final Runnable backAction;

    private Tab tab = Tab.GRAPHICS;

    // Widgets — rebuilt when the tab changes.
    private final List<Toggle> toggles = new ArrayList<>();
    private final List<Slider> sliders = new ArrayList<>();
    private final List<Radio<?>> radios = new ArrayList<>();
    private final List<TabPill> tabPills = new ArrayList<>();
    private Button backBtn;

    public ConfigScreen(Main game) { this(game, game::openMenu); }

    public ConfigScreen(Main game, Runnable backAction) {
        super(game);
        this.backAction = backAction;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void show() {
        Gdx.input.setInputProcessor(input);
        rebuildTabPills();
        rebuildBackBtn();
        rebuildTabContent();
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(float delta) {
        if (input.consumeBack()) { goBack(); return; }

        updateCursorWorld();
        for (TabPill p : tabPills) {
            boolean was = p.hovered;
            p.updateHover(cursorWorld.x, cursorWorld.y);
            if (!was && p.hovered) playHover();
        }
        for (Toggle  t : toggles)  t.updateHover (cursorWorld.x, cursorWorld.y);
        for (Slider  s : sliders)  { s.tryDrag(cursorWorld.x, cursorWorld.y); s.updateHover(cursorWorld.x, cursorWorld.y); }
        for (Radio<?>r : radios)   r.updateHover (cursorWorld.x, cursorWorld.y);
        if (backBtn != null) {
            boolean was = backBtn.hovered;
            backBtn.updateHover(cursorWorld.x, cursorWorld.y);
            if (!was && backBtn.hovered) playHover();
        }

        beginFrame(Palette.BG.r, Palette.BG.g, Palette.BG.b);
        SpriteBatch      batch   = context.getBatch();
        ProceduralAssets visuals = context.getAssets().getProceduralAssets();
        Texture          pixel   = visuals.getPixel();
        BitmapFont       title   = context.getTitleFont();
        BitmapFont       body    = context.getBodyFont();

        batch.begin();
        batch.setColor(Color.WHITE);
        UIDraw.fill(batch, pixel, Palette.BG, 0f, 0f,
            GameConfig.WORLD_WIDTH, GameConfig.WORLD_HEIGHT);
        UIDraw.cornerMarks(batch, pixel, 24f);

        UIDraw.topBar(batch, pixel, body, context.getGlyphLayout(),
            "← BACK (ESC)", "PHASE // SETTINGS", Palette.WARM);
        UIDraw.bottomBar(batch, pixel, body, context.getGlyphLayout(),
            "CHANGES SAVED AUTOMATICALLY",
            "TAB: " + tab.name(), Palette.TEXT_DIM);

        // Sidebar separator.
        UIDraw.fill(batch, pixel, Palette.BORDER,
            SIDEBAR_W, BOT_BAR_H, 1f,
            GameConfig.WORLD_HEIGHT - TOP_BAR_H - BOT_BAR_H);

        // Sidebar tabs.
        for (TabPill p : tabPills) p.draw(batch, pixel, body, context.getGlyphLayout());

        // Content area.
        drawTabHeader(batch, title, body);
        drawTabContent(batch, pixel, body);

        if (backBtn != null) backBtn.draw(batch, pixel, body, context.getGlyphLayout());

        batch.end();
    }

    // ── Tab header / content ──────────────────────────────────────────────────

    private void drawTabHeader(SpriteBatch batch, BitmapFont title, BitmapFont body) {
        float headerX = SIDEBAR_W + 48f;
        float ribbonY = GameConfig.WORLD_HEIGHT - TOP_BAR_H - 32f;   // ~652
        float bigY    = GameConfig.WORLD_HEIGHT - TOP_BAR_H - 70f;   // ~614 — title baseline

        body.setColor(Palette.RED);
        body.draw(batch, "═══ " + tab.name() + " ═══", headerX, ribbonY);

        title.getData().setScale(2.4f);
        title.setColor(Palette.TEXT);
        title.draw(batch, tabHeadline(tab), headerX, bigY);
        title.getData().setScale(2.2f);
    }

    /** First-row baseline Y.  Pushed well below the big title so they never overlap. */
    private float firstRowY() {
        return GameConfig.WORLD_HEIGHT - TOP_BAR_H - 200f;
    }

    /** Baseline Y for the {@code i}-th visible row (i = 0 is the topmost). */
    private float rowY(int i) {
        return firstRowY() - i * ROW_STEP;
    }

    /** Y-position for a widget centred vertically on a row (label sits at y + 36). */
    private float widgetY(int i) {
        return rowY(i) + 18f;
    }

    /** Right-aligned X for a widget of the given width. */
    private static float widgetX(float width) {
        return WIDGET_RIGHT - width;
    }

    private static String tabHeadline(Tab t) {
        return switch (t) {
            case AUDIO    -> "SOUND";
            case GRAPHICS -> "DISPLAY";
            case CONTROLS -> "KEYBINDS";
            case GAME     -> "PREFERENCES";
        };
    }

    private void drawTabContent(SpriteBatch batch, Texture pixel, BitmapFont body) {
        float rowX = SIDEBAR_W + 48f;
        float rowW = GameConfig.WORLD_WIDTH - rowX - 64f;
        GameSettings settings = context.getSettings();

        switch (tab) {
            case AUDIO -> {
                drawSettingRow(batch, pixel, body, rowX, rowY(0), rowW, "Master Volume",  "controls all output channels");
                drawSettingRow(batch, pixel, body, rowX, rowY(1), rowW, "Music Volume",   "background track during match");
                drawSettingRow(batch, pixel, body, rowX, rowY(2), rowW, "SFX Volume",     "ball impacts, miss sounds, particles");
                drawSettingRow(batch, pixel, body, rowX, rowY(3), rowW, "Mute All",       "shortcut for master volume = 0");
            }
            case GRAPHICS -> {
                int i = 0;
                drawSettingRow(batch, pixel, body, rowX, rowY(i++), rowW,
                    "Fullscreen", "toggles borderless window");
                if (!settings.isFullscreen()) {
                    drawSettingRow(batch, pixel, body, rowX, rowY(i++), rowW,
                        "Window Resolution", "applies immediately when windowed");
                }
                drawSettingRow(batch, pixel, body, rowX, rowY(i++), rowW,
                    "Retro Filter", "buckshot-style palette + dither overlay on the match");
                drawSettingRow(batch, pixel, body, rowX, rowY(i++), rowW,
                    "Filter Intensity", "smaller = chunkier pixels");
            }
            case CONTROLS -> {
                drawSettingRow(batch, pixel, body, rowX, rowY(0), rowW, "Return Ball",  "MOUSE 1 — click the incoming ball");
                drawSettingRow(batch, pixel, body, rowX, rowY(1), rowW, "Pause / Menu", "ESC — opens the pause overlay");
                drawSettingRow(batch, pixel, body, rowX, rowY(2), rowW, "Restart",      "R — single-player only");
                drawSettingRow(batch, pixel, body, rowX, rowY(3), rowW, "Host LAN",     "H — from the multiplayer lobby");
                drawSettingRow(batch, pixel, body, rowX, rowY(4), rowW, "Join LAN",     "J — from the multiplayer lobby");
                body.setColor(Palette.TEXT_OFF);
                body.draw(batch, "REBINDING NOT YET SUPPORTED", rowX, rowY(5) + 14f);
            }
            case GAME -> {
                drawSettingRow(batch, pixel, body, rowX, rowY(0), rowW, "Show FPS Counter", "top-left overlay during the match");
                drawSettingRow(batch, pixel, body, rowX, rowY(1), rowW, "Screen Shake",     "camera shake on miss");
                drawSettingRow(batch, pixel, body, rowX, rowY(2), rowW, "Event Log",        "upper-left feed of points, items, faults");
            }
        }

        // Widgets paint themselves on top of the rows.
        for (Toggle  t : toggles)  t.draw(batch, pixel);
        for (Slider  s : sliders)  s.draw(batch, pixel, body, context.getGlyphLayout());
        for (Radio<?>r : radios)   r.draw(batch, pixel, body, context.getGlyphLayout());
    }

    /** Background row: label + hint on the left, divider line at the bottom. */
    private void drawSettingRow(SpriteBatch batch, Texture pixel, BitmapFont body,
                                float x, float y, float w, String label, String hint) {
        body.setColor(Palette.TEXT);
        body.draw(batch, label.toUpperCase(), x, y + 36f);
        body.setColor(Palette.TEXT_DIM);
        body.draw(batch, hint, x, y + 14f);
        UIDraw.fill(batch, pixel, Palette.BORDER, x, y - 6f, w, 1f);
    }

    // ── Build widgets per tab ─────────────────────────────────────────────────

    private void rebuildTabPills() {
        tabPills.clear();
        Tab[] all = Tab.values();
        for (int i = 0; i < all.length; i++) {
            Tab t = all[i];
            float py = GameConfig.WORLD_HEIGHT - TOP_BAR_H - 28f - i * 44f;
            tabPills.add(new TabPill(0f, py - 32f, SIDEBAR_W, 44f, t,
                () -> { tab = t; rebuildTabContent(); }));
        }
    }

    private void rebuildBackBtn() {
        float bx = SIDEBAR_W + 48f;
        float by = BOT_BAR_H + 28f;
        backBtn = new Button(bx, by, 200f, 56f, "← BACK", Palette.TEXT_DIM, this::goBack);
    }

    private void rebuildTabContent() {
        toggles.clear();
        sliders.clear();
        radios.clear();
        GameSettings s = context.getSettings();

        // All widgets right-align to WIDGET_RIGHT (= WORLD_WIDTH - 64f).
        float toggleX  = widgetX(48f);
        float sliderW  = 220f;
        float sliderX  = widgetX(sliderW);

        switch (tab) {
            case AUDIO -> {
                sliders.add(makeVolSlider(sliderX, widgetY(0) - 6f, sliderW, s::getMasterVolume, s::setMasterVolume));
                sliders.add(makeVolSlider(sliderX, widgetY(1) - 6f, sliderW, s::getMusicVolume,  s::setMusicVolume));
                sliders.add(makeVolSlider(sliderX, widgetY(2) - 6f, sliderW, s::getSfxVolume,    s::setSfxVolume));
                toggles.add(new Toggle(toggleX, widgetY(3) - 6f,
                    () -> s.getMasterVolume() == 0,
                    on -> s.setMasterVolume(on ? 0 : 100)));
            }
            case GRAPHICS -> {
                int i = 0;
                // Fullscreen toggle.  When it flips, rebuild so the Window
                // Resolution row appears / disappears and the layout collapses.
                toggles.add(new Toggle(toggleX, widgetY(i++) - 6f,
                    s::isFullscreen, on -> {
                        if (on != s.isFullscreen()) s.toggleFullscreen();
                        s.applyWindowMode();
                        rebuildTabContent();
                    }));
                // Window resolution — only when windowed.
                if (!s.isFullscreen()) {
                    radios.add(makeWindowResRadio(widgetY(i++) - 8f, s));
                }
                // Retro filter toggle.
                toggles.add(new Toggle(toggleX, widgetY(i++) - 6f,
                    s::isPostProcessingEnabled, on -> {
                        if (on != s.isPostProcessingEnabled()) s.togglePostProcessing();
                        context.applySettings();
                    }));
                // Filter intensity radio.
                radios.add(makeRetroIntensityRadio(widgetY(i++) - 8f, s));
            }
            case CONTROLS -> { /* read-only — no widgets */ }
            case GAME -> {
                toggles.add(new Toggle(toggleX, widgetY(0) - 6f,
                    s::isShowFpsCounter, s::setShowFpsCounter));
                toggles.add(new Toggle(toggleX, widgetY(1) - 6f,
                    s::isScreenShakeEnabled, s::setScreenShakeEnabled));
                toggles.add(new Toggle(toggleX, widgetY(2) - 6f,
                    s::isEventLogEnabled, s::setEventLogEnabled));
            }
        }
    }

    private static Slider makeVolSlider(float x, float y, float w,
                                        java.util.function.IntSupplier reader,
                                        java.util.function.IntConsumer writer) {
        // The slider reserves a fixed prefix on the left for its value label
        // (e.g. "100%"), so the track itself is narrower than the bounding box.
        return new Slider(x + 60f, y, w - 60f, 0, 100, 1, reader, writer, v -> v + "%");
    }

    private Radio<int[]> makeWindowResRadio(float y, GameSettings s) {
        final int[][] presets = new int[][] {
            {1280, 720}, {1600, 900}, {1920, 1080}
        };
        final float pillW = 110f;
        float x = widgetX(pillW * presets.length);
        return new Radio<>(x, y, pillW, presets,
            () -> matchOrFirst(presets, new int[] { s.getWindowWidth(), s.getWindowHeight() }),
            target -> {
                int[] cur = new int[] { s.getWindowWidth(), s.getWindowHeight() };
                while (cur[0] != target[0] || cur[1] != target[1]) {
                    s.cycleWindowResolution(1);
                    int newW = s.getWindowWidth();
                    int newH = s.getWindowHeight();
                    if (cur[0] == newW && cur[1] == newH) break; // no progress, bail
                    cur = new int[] { newW, newH };
                }
                if (!s.isFullscreen()) s.applyWindowMode();
            },
            v -> v[0] + "×" + v[1]);
    }

    private static int[] matchOrFirst(int[][] presets, int[] target) {
        for (int[] p : presets) if (p[0] == target[0] && p[1] == target[1]) return p;
        return presets[0];
    }

    private Radio<String> makeRetroIntensityRadio(float y, GameSettings s) {
        final String[] labels = { "SOFT", "MEDIUM", "RETRO", "CHUNKY", "BUNKER" };
        final float pillW = 80f; // 5 × 80 = 400, fits within the content column
        float x = widgetX(pillW * labels.length);
        return new Radio<>(x, y, pillW, labels,
            () -> labels[Math.min(s.getRetroResolutionIndex(), labels.length - 1)],
            v -> {
                int targetIdx = 0;
                for (int i = 0; i < labels.length; i++) if (labels[i].equals(v)) { targetIdx = i; break; }
                int delta = targetIdx - s.getRetroResolutionIndex();
                if (delta != 0) s.cycleRetroResolution(delta);
                context.applySettings();
            },
            v -> v);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void updateCursorWorld() {
        cursorWorld.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        context.getViewport().unproject(cursorWorld);
    }

    private void goBack() {
        if (backAction != null) backAction.run();
        else game.openMenu();
    }

    private void playClick() {
        context.getAssets().getUiClickSfx().play(context.getSettings().getSfxGain() * 0.6f);
    }

    private void playHover() {
        context.getAssets().getUiHoverSfx().play(context.getSettings().getSfxGain() * 0.4f);
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
    }

    // ── Sidebar tab pill ──────────────────────────────────────────────────────

    /** Small clickable pill that lives in the sidebar nav. */
    private final class TabPill {
        final com.badlogic.gdx.math.Rectangle bounds;
        final Tab tab;
        final Runnable onClick;
        boolean hovered;

        TabPill(float x, float y, float w, float h, Tab tab, Runnable onClick) {
            this.bounds = new com.badlogic.gdx.math.Rectangle(x, y, w, h);
            this.tab = tab;
            this.onClick = onClick;
        }

        void updateHover(float wx, float wy) { hovered = bounds.contains(wx, wy); }

        boolean tryClick(float wx, float wy) {
            if (!bounds.contains(wx, wy)) return false;
            onClick.run();
            return true;
        }

        void draw(SpriteBatch batch, Texture pixel, BitmapFont font, com.badlogic.gdx.graphics.g2d.GlyphLayout glyph) {
            boolean selected = ConfigScreen.this.tab == tab;
            // Faint background for selected; subtle hover tint.
            if (selected) UIDraw.fill(batch, pixel, Palette.RED, 0.08f,
                bounds.x, bounds.y, bounds.width, bounds.height);
            else if (hovered) UIDraw.fill(batch, pixel, Palette.RED, 0.04f,
                bounds.x, bounds.y, bounds.width, bounds.height);
            // Left accent strip.
            UIDraw.fill(batch, pixel,
                selected ? Palette.RED : (hovered ? Palette.RED_DIM : new com.badlogic.gdx.graphics.Color(0,0,0,0)),
                bounds.x, bounds.y, 2f, bounds.height);
            // Label.
            font.setColor(selected ? Palette.TEXT : Palette.TEXT_DIM);
            font.draw(batch, tab.name(),
                bounds.x + 22f,
                bounds.y + bounds.height * 0.5f + font.getCapHeight() * 0.5f);
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    private final class InputHandler extends InputAdapter {
        private boolean backRequested;
        boolean consumeBack() { boolean v = backRequested; backRequested = false; return v; }

        @Override
        public boolean keyDown(int k) {
            if (k == Input.Keys.ESCAPE || k == Input.Keys.BACKSPACE) {
                backRequested = true; return true;
            }
            return false;
        }

        @Override
        public boolean touchDown(int sx, int sy, int p, int b) {
            updateCursorWorld();
            for (TabPill t : tabPills) if (t.tryClick(cursorWorld.x, cursorWorld.y)) { playClick(); return true; }
            for (Toggle  t : toggles)  if (t.tryClick(cursorWorld.x, cursorWorld.y)) { playClick(); return true; }
            for (Slider  s : sliders)  if (s.tryClick(cursorWorld.x, cursorWorld.y)) { playClick(); return true; }
            for (Radio<?>r : radios)   if (r.tryClick(cursorWorld.x, cursorWorld.y)) { playClick(); return true; }
            if (backBtn != null && backBtn.tryClick(cursorWorld.x, cursorWorld.y)) { playClick(); return true; }
            return false;
        }

        @Override
        public boolean touchDragged(int sx, int sy, int p) {
            updateCursorWorld();
            for (Slider s : sliders) if (s.tryDrag(cursorWorld.x, cursorWorld.y)) return true;
            return false;
        }

        @Override
        public boolean touchUp(int sx, int sy, int p, int b) {
            for (Slider s : sliders) s.release();
            return false;
        }
    }
}
