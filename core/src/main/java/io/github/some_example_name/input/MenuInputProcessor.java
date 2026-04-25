package io.github.some_example_name.input;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;

/** Minimal processor for press-to-continue screens. */
public final class MenuInputProcessor extends InputAdapter {
    private final Runnable onConfirm;

    public MenuInputProcessor(Runnable onConfirm) {
        this.onConfirm = onConfirm;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
            onConfirm.run();
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
