/* This class is the entry point of the program. 
 * This class implements the following command line arguments:
 * - init: Initializes the gitlet repository.
 *       Usage: java Main init
 * - add: Adds a file to the gitlet repository.
 * - commit: Commits changes to the gitlet repository.
*/

package gitlet;

import static gitlet.Utils.*;

public class Main {
    public static void main(String[] args) {
        switch (args[0]) {
            case "init":
                validateArgs(args, 1);
                // Initialize the gitlet repository.
                break;
            default:
                break;
        }
    }

    public static void validateArgs(String[] args, int expectedLen) {
        assertCondition(args.length == expectedLen, "Incorrect operands.");
    }
}