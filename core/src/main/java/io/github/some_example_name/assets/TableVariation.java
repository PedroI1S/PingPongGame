package io.github.some_example_name.assets;

/** Table animation variants backed by sprite sheets. */
public enum TableVariation {
    CLASSIC("classic", "Table.png", 6, 6, 34);

    private final String id;
    private final String spriteSheetPath;
    private final int columns;
    private final int rows;
    private final int frameCount;

    TableVariation(String id, String spriteSheetPath, int columns, int rows, int frameCount) {
        this.id = id;
        this.spriteSheetPath = spriteSheetPath;
        this.columns = columns;
        this.rows = rows;
        this.frameCount = frameCount;
    }

    public String getId() {
        return id;
    }

    public String getSpriteSheetPath() {
        return spriteSheetPath;
    }

    public int getColumns() {
        return columns;
    }

    public int getRows() {
        return rows;
    }

    public int getFrameCount() {
        return frameCount;
    }
}
