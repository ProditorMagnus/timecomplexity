import java.nio.file.Path;

public abstract class FunctionExecutor {
    protected final ResultHolder results = new ResultHolder();
    protected final Path source;

    public ResultHolder getResults() {
        return results;
    }

    public FunctionExecutor(Path source) {
        this.source = source;
    }

    public abstract ResultHolder start();
}
