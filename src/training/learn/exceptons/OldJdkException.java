package training.learn.exceptons;

/**
 * Created by karashevich on 09/09/15.
 */
public class OldJdkException extends Exception {

    public OldJdkException(String javaSdkVersion) {
        super(" Old Java SDK version for Project SDK.");
    }

    public OldJdkException(String javaSdkVersion, String atLeastVersion) {
        super(" Old Java SDK version for Project SDK. Please use version " + atLeastVersion);
    }
}
