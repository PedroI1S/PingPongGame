package io.github.some_example_name;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Screen;
import io.github.some_example_name.core.GameContext;
import io.github.some_example_name.screen.ConfigScreen;
import io.github.some_example_name.screen.LoadingScreen;
import io.github.some_example_name.screen.MatchScreen3D;
import io.github.some_example_name.screen.MenuScreen;
import io.github.some_example_name.screen.PauseMenuScreen;
import io.github.some_example_name.screen.MultiplayerLobbyScreen;
import io.github.some_example_name.screen.NetMatchScreen;

/**
 * Game bootstrap.
 *
 * <p>This keeps the launcher thin and centralizes screen navigation for the whole project.</p>
 */
public class Main extends Game {
    private GameContext context;

    @Override
    public void create() {
        // Keep libGDX's default HdpiMode.Logical.  Viewport.apply() then
        // auto-converts logical glViewport calls to backbuffer pixels (2× on
        // Retina), which is what every screen needs to fill the window.
        // RetroPostProcess sizes its FBO at backbuffer pixels so the
        // converted viewport matches the FBO.
        context = new GameContext();
        context.getAssets().queueCoreAssets();
        context.getSettings().applyWindowMode();
        context.applySettings();
        setScreen(new LoadingScreen(this));
    }

    public GameContext getContext() {
        return context;
    }

    public void openMenu() {
        setScreen(new MenuScreen(this));
    }

    public void openConfig() {
        setScreen(new ConfigScreen(this));
    }

    public void openConfig(Runnable onBack) {
        setScreen(new ConfigScreen(this, onBack));
    }

    public void openMultiplayerLobby() {
        setScreen(new MultiplayerLobbyScreen(this));
    }

    public void openNetMatch() {
        setScreen(new NetMatchScreen(this));
    }

    /** Single-player vs the local bot — no pre-match loadout. */
    public void openMatch() {
        setScreen(new MatchScreen3D(this));
    }

    public void openPauseMenu(Screen resumeScreen, Runnable quitAction) {
        setScreen(new PauseMenuScreen(this, resumeScreen, quitAction));
    }

    @Override
    public void dispose() {
        super.dispose();
        if (context != null) {
            context.dispose();
        }
    }
}
