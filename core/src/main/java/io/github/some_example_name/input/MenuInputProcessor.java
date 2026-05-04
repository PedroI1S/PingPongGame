package io.github.some_example_name.input;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;

/** Minimal processor for press-to-continue screens. */
public final class MenuInputProcessor extends InputAdapter {
    private final Runnable onConfirm;
    private final Runnable onMultiplayer;

    public MenuInputProcessor(Runnable onConfirm) {
        this(onConfirm, null);
    }

    public MenuInputProcessor(Runnable onConfirm, Runnable onMultiplayer) {
        this.onConfirm = onConfirm;
        this.onMultiplayer = onMultiplayer;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
            onConfirm.run();
            return true;
        }
        if (keycode == Input.Keys.M && onMultiplayer != null) {
            onMultiplayer.run();
            return true;
        }
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        onConfirm.run();
        return true;
    }
}
