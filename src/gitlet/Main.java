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
 * - rm: Unstages a file from the gitlet repository.
 *       Usage: java Main rm <file>
 * - write-tree: Create a tree object from the current index
 *       Usage: java Main write-tree
 * - commit: Commits changes to the gitlet repository.
 *       Usage: java Main commit <message>
 * - log: Displays the commit history of the gitlet repository.
 *       Usage: java Main log
 * - ls-tree: Displays the contents of a tree object recursively.
 *       Usage: java Main ls-tree <tree>
 * - checkout: Checkout a branch or a commit.
 *       Usage: java Main checkout <branch/commit/tag>
 * - status: Displays the status of the gitlet repository.
 *       Usage: java Main status
 * - tag: Creates a tag for the specified commit, if no commit is specified, 
 * creates a tag for the current commit. If no name is specified, lists all 
 * tags.
 *       Usage: java Main tag <name> <commit>
 *              java Main tag <name>
 *              java Main tag 
 * - branch: Creates a new branch or lists all branches.
 *       Usage: java Main branch <name>
 *              java Main branch 
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
            case "add":
                validateArgs(args, 2);
                Repository.add(args[1]);
                break;
            case "rm":
                validateArgs(args, 2);
                Repository.rm(args[1]);
                break;
            case "write-tree":
                validateArgs(args, 1);
                Repository.writeTree();
                break;
            case "commit":
                validateArgs(args, 2);
                Repository.commit(args[1]);
                break;
            case "log":
                validateArgs(args, 1);
                Repository.log();
                break;
            case "ls-tree":
                validateArgs(args, 2);
                Repository.lsTree(args[1]);
                break;
            case "checkout":
                validateArgs(args, 2);
                Repository.checkout(args[1]);
                break;
            case "status":
                validateArgs(args, 1);
                Repository.status();
                break;
            case "tag":
                validateArgs(args, 1, 3);
                tagOperation(args);
                break;
            case "branch":
                validateArgs(args, 1, 2);
                if (args.length == 1) {
                    Repository.branch();
                } else {
                    Repository.branch(args[1]);
                }
                break;
            default:
                System.out.println("No command with that name exists.");
                System.exit(1);
        }
    }

    public static void validateArgs(String[] args, int expectedLen) {
        assertCondition(args.length == expectedLen, "Incorrect operands.");
    }

    public static void validateArgs(String[] args, int minLen, int maxLen) {
        assertCondition(args.length >= minLen && args.length <= maxLen, 
            "Incorrect operands.");
    }

    private static void tagOperation(String[] args) {
        if (args.length == 1) {
            Repository.tag();
        } else if (args.length == 2) {
            Repository.tag(args[1]);
        } else {
            Repository.tag(args[1], args[2]);
        } 
    }
}