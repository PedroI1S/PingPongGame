package io.github.some_example_name.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.math.Vector3;
import io.github.some_example_name.Main;
import io.github.some_example_name.ui.Button;

import java.util.List;

/**
 * Base for button-driven menu screens (main menu, pause, lobby).
 *
 * <p>Owns the cursor unprojection, the input-processor wiring in
 * {@link #show()} / {@link #hide()}, and the shared button hover/click
 * plumbing.  Subclasses expose their clickable buttons via
 * {@link #activeButtons()} and hook keyboard input via
 * {@link #onKeyDown(int)} / {@link #onKeyTyped(char)}.</p>
 */
public abstract class MenuBaseScreen extends BaseScreen {

    protected final Vector3 cursorWorld = new Vector3();

    private final InputAdapter input = new InputAdapter() {
        @Override
        public boolean keyDown(int keycode) {
            return onKeyDown(keycode);
        }

        @Override
        public boolean keyTyped(char ch) {
            return onKeyTyped(ch);
        }

        @Override
        public boolean touchDown(int sx, int sy, int pointer, int btn) {
            updateCursorWorld();
            for (Button b : activeButtons()) {
                if (b.tryClick(cursorWorld.x, cursorWorld.y)) {
                    playClick();
                    return true;
                }
            }
            return false;
        }
    };

    protected MenuBaseScreen(Main game) {
        super(game);
    }

    /** Buttons that are currently visible / clickable. */
    protected abstract List<Button> activeButtons();

    /** Keyboard hook; return true when the key was handled. */
    protected boolean onKeyDown(int keycode) {
        return false;
    }

    /** Typed-character hook; return true when the character was consumed. */
    protected boolean onKeyTyped(char ch) {
        return false;
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(input);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    protected void updateCursorWorld() {
        cursorWorld.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        context.getViewport().unproject(cursorWorld);
    }

    /** Per-frame cursor + hover refresh; call at the top of {@code render()}. */
    protected void updateButtonHover() {
        updateCursorWorld();
        for (Button b : activeButtons()) {
            boolean was = b.hovered;
            b.updateHover(cursorWorld.x, cursorWorld.y);
            if (!was && b.hovered) playHover();
        }
    }

    protected void playClick() {
        context.getAssets().getUiClickSfx().play(context.getSettings().getSfxGain() * 0.6f);
    }

    protected void playHover() {
        context.getAssets().getUiHoverSfx().play(context.getSettings().getSfxGain() * 0.4f);
    }
}
