package io.github.some_example_name.input;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;

/** Input for card selection. */
public final class LoadoutInputProcessor extends InputAdapter {
    private final Runnable onLeft;
    private final Runnable onRight;
    private final Runnable onConfirm;
    private final Runnable onBack;

    public LoadoutInputProcessor(Runnable onLeft, Runnable onRight, Runnable onConfirm, Runnable onBack) {
        this.onLeft = onLeft;
        this.onRight = onRight;
        this.onConfirm = onConfirm;
        this.onBack = onBack;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.LEFT || keycode == Input.Keys.A) {
            onLeft.run();
            return true;
        }
        if (keycode == Input.Keys.RIGHT || keycode == Input.Keys.D) {
            onRight.run();
            return true;
        }
        if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
            onConfirm.run();
            return true;
        }
        if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACKSPACE) {
            onBack.run();
            return true;
        }
        return false;
    }
}
