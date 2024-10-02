/* This class is the entry point of the program. 
 * This class implements the following command line arguments:
 * - init: Initializes the gitlet repository.
 *       Usage: java Main init
 * - hash-object: Hashes a file and stores it in the gitlet repository.
 *       Usage: java Main hash-object <file>
 * - cat-file: Displays the contents of a git object.
 *       Usage: java Main cat-file <type> <object>
 * - add: Stages a file to the gitlet repository.
 *       Usage: java Main add <file>
 * - commit: Commits changes to the gitlet repository.
*/

package gitlet;

import static gitlet.Utils.*;

public class Main {
    public static void main(String[] args) {
        switch (args[0]) {
            case "init":
                validateArgs(args, 1);
                Repository.init();
                break;
            case "hash-object":
                validateArgs(args, 2);
                Repository.hashObject(args[1]);
                break;
            case "cat-file":
                validateArgs(args, 3);
                Repository.catFile(args[2], args[1]);
                break;
            default:
                System.out.println("No command with that name exists.");
                System.exit(1);
        }
    }

    public static void validateArgs(String[] args, int expectedLen) {
        assertCondition(args.length == expectedLen, "Incorrect operands.");
    }
}