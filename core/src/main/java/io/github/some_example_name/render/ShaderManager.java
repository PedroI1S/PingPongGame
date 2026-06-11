package io.github.some_example_name.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * Singleton cache for compiled {@link ShaderProgram}s.
 *
 * <p>{@link #get(String)} loads {@code assets/shaders/<name>.vert} +
 * {@code <name>.frag}, compiles once, and hands the same instance to every
 * caller.  The manager owns the programs — callers must NOT dispose them;
 * {@code GameContext#dispose()} tears the whole cache down via
 * {@link #disposeInstance()}.</p>
 */
public final class ShaderManager implements Disposable {

    private static ShaderManager instance;

    /** Lazily-created singleton. Requires a live GL context on first {@link #get(String)}. */
    public static ShaderManager instance() {
        if (instance == null) {
            instance = new ShaderManager();
        }
        return instance;
    }

    /** Disposes the singleton if it was ever created (safe to call when it wasn't). */
    public static void disposeInstance() {
        if (instance != null) {
            instance.dispose();
        }
    }

    private final ObjectMap<String, ShaderProgram> shaders = new ObjectMap<>();

    private ShaderManager() {
    }

    /**
     * Returns the compiled program for {@code shaders/<name>.{vert,frag}},
     * compiling and caching it on first request.
     *
     * @throws IllegalStateException with the GL log if compilation fails
     */
    public ShaderProgram get(String name) {
        ShaderProgram shader = shaders.get(name);
        if (shader == null) {
            String vert = Gdx.files.internal("shaders/" + name + ".vert").readString();
            String frag = Gdx.files.internal("shaders/" + name + ".frag").readString();
            shader = new ShaderProgram(vert, frag);
            if (!shader.isCompiled()) {
                throw new IllegalStateException(name + " shader compile failed:\n" + shader.getLog());
            }
            shaders.put(name, shader);
        }
        return shader;
    }

    @Override
    public void dispose() {
        for (ShaderProgram shader : shaders.values()) {
            shader.dispose();
        }
        shaders.clear();
        if (instance == this) {
            instance = null;
        }
    }
}
