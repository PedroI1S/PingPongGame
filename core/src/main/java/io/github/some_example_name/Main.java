package io.github.some_example_name;

import com.badlogic.gdx.Game;
import io.github.some_example_name.core.GameContext;
import io.github.some_example_name.screen.LoadingScreen;
import io.github.some_example_name.screen.LoadoutScreen;
import io.github.some_example_name.screen.MatchScreen3D;
import io.github.some_example_name.screen.MenuScreen;
import io.github.some_example_name.screen.MultiplayerLobbyScreen;

/**
 * Game bootstrap.
 *
 * <p>This keeps the launcher thin and centralizes screen navigation for the whole project.</p>
 */
public class Main extends Game {
    private GameContext context;

    @Override
    public void create() {
        context = new GameContext();
        context.getAssets().queueCoreAssets();
        setScreen(new LoadingScreen(this));
    }

    public GameContext getContext() {
        return context;
    }

    public void openMenu() {
        setScreen(new MenuScreen(this));
    }

    public void openLoadout() {
        context.getSession().rollNewLoadout();
        setScreen(new LoadoutScreen(this));
    }

    public void openMultiplayerLobby() {
        setScreen(new MultiplayerLobbyScreen(this));
    }

    public void openMatch() {
        if (context.getSession().getPlayerItem() == null) {
            openLoadout();
            return;
        }
        setScreen(new MatchScreen3D(this));
    }

    @Override
    public void dispose() {
        super.dispose();
        if (context != null) {
            context.dispose();
        }
    }
}
