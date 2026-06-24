package hcmute.com.smarteduapp.data.repository;

/** Returns repository results to the UI thread. */
public interface RepositoryCallback<T> {
    void onSuccess(T result);

    default void onError(Exception exception) {
        // Screens override this only when they need a user-facing error state.
    }
}
