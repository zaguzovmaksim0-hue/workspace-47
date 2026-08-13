package android.webkit;

/** Test-only concrete WebResourceError for JVM tests. */
public final class TestWebResourceError extends WebResourceError {
    private final int errorCode;
    private final CharSequence description;

    public TestWebResourceError(int errorCode, CharSequence description) {
        this.errorCode = errorCode;
        this.description = description;
    }

    @Override
    public CharSequence getDescription() {
        return description;
    }

    @Override
    public int getErrorCode() {
        return errorCode;
    }
}
