package io.github.some_example_name.input;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.FitViewport;

/** Runtime controller for pointer-based clicking plus global match shortcuts. */
public final class MatchInputController extends InputAdapter {
    private final FitViewport viewport;
    private final Vector2 pointer = new Vector2();
    private final Vector2 clickPosition = new Vector2();

    private boolean clickRequested;
    private boolean menuRequested;
    private boolean restartRequested;

    public MatchInputController(FitViewport viewport) {
        this.viewport = viewport;
        pointer.set(viewport.getWorldWidth() * 0.5f, viewport.getWorldHeight() * 0.5f);
        clickPosition.set(pointer);
    }

    public boolean consumeMenuRequest() {
        boolean requested = menuRequested;
        menuRequested = false;
        return requested;
    }

    public boolean consumeRestartRequest() {
        boolean requested = restartRequested;
        restartRequested = false;
        return requested;
    }

    public boolean consumeClick(Vector2 out) {
        if (!clickRequested) {
            return false;
        }
        out.set(clickPosition);
        clickRequested = false;
        return true;
    }

    public Vector2 getPointer() {
        return pointer;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE) {
            menuRequested = true;
            return true;
        }
        if (keycode == Input.Keys.R) {
            restartRequested = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        updatePointer(screenX, screenY);
        return true;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        updatePointer(screenX, screenY);
        return true;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        updatePointer(screenX, screenY);
        clickPosition.set(this.pointer);
        clickRequested = true;
        return true;
    }

    private void updatePointer(int screenX, int screenY) {
        pointer.set(screenX, screenY);
        viewport.unproject(pointer);
    }
}
