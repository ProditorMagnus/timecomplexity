import java.nio.file.Path;

/**
 * Klass FunctionExecutor on abstraktne klass, mida laiendavad klassid JavaExecutor ning PythonExecutor.
 * Nende ühine funktsionaalsus on funktsiooni käivitamine ning nende tööaegade alusel
 * tulemuste struktuuri ResultHolder koostamine.
 */
public abstract class FunctionExecutor {
    protected final ResultHolder results = new ResultHolder();
    protected final Path source;

    /**
     * Salvestab sisendfaili teekonna klassivälja
     *
     * @param source sisendfaili asukoht
     */
    public FunctionExecutor(Path source) {
        if (source == null || source.getFileName() == null) {
            throw new IllegalArgumentException("Source file is missing");
        }
        this.source = source;
    }

    /**
     * Leiab funktsiooni ajalise keerukuse määramise jaoks vajalikud andmed
     *
     * @return kogutud andmed
     */
    public abstract ResultHolder start();
}
