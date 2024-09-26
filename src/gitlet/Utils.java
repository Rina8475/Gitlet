/* This class implements some utility functions used by Gitlet. */

package gitlet;

public class Utils {
    public static void assertCondition(boolean condition, String message) {
        if (!condition) {
            System.err.println(message);
            System.exit(1);
        }
    }
}
