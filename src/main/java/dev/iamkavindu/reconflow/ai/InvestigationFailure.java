package dev.iamkavindu.reconflow.ai;

import java.io.InterruptedIOException;
import java.time.Duration;

/** Operator diagnostics contain categories and types only, never provider messages or payloads. */
public final class InvestigationFailure extends RuntimeException {

    public enum Stage {
        RETRIEVAL,
        GENERATION,
        TOOL_EXECUTION,
        VALIDATION,
        JUDGMENT
    }

    public static final class Progress {
        private final long started = System.nanoTime();
        private long stageStarted = started;
        private Stage stage = Stage.RETRIEVAL;

        public void stage(Stage stage) {
            this.stage = stage;
            this.stageStarted = System.nanoTime();
        }

        public Stage currentStage() {
            return stage;
        }
    }

    private final Stage stage;
    private final String category;
    private final String exceptionClass;
    private final String causeClass;
    private final Duration requestTimeout;
    private final long elapsedMs;
    private final long totalElapsedMs;

    public InvestigationFailure(RuntimeException error, Progress progress, Duration requestTimeout) {
        super("AI investigation stage failed", error);
        this.stage = progress.stage;
        this.requestTimeout = requestTimeout;
        this.elapsedMs = (System.nanoTime() - progress.stageStarted) / 1_000_000;
        this.totalElapsedMs = (System.nanoTime() - progress.started) / 1_000_000;
        this.exceptionClass = error.getClass().getName();
        Throwable root = error;
        boolean timeout = false;
        boolean transport = false;
        for (int depth = 0; root != null && depth < 20; depth++) {
            String name = root.getClass().getSimpleName();
            timeout |= root instanceof InterruptedIOException || name.endsWith("TimeoutException");
            transport |= root instanceof java.io.IOException || name.equals("TypeSafeApiConnectionException");
            if (root.getCause() == null || root.getCause() == root) {
                break;
            }
            root = root.getCause();
        }
        this.causeClass = root == null ? exceptionClass : root.getClass().getName();
        this.category = timeout ? "TIMEOUT" : transport ? "TRANSPORT" : stage.name() + "_FAILURE";
    }

    public Stage stage() {
        return stage;
    }

    public String category() {
        return category;
    }

    public String exceptionClass() {
        return exceptionClass;
    }

    public String causeClass() {
        return causeClass;
    }

    public Duration requestTimeout() {
        return requestTimeout;
    }

    public long elapsedMs() {
        return elapsedMs;
    }

    public long totalElapsedMs() {
        return totalElapsedMs;
    }
}
