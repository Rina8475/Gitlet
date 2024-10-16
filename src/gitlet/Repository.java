/* This class implements the repository operations. */

package gitlet;

import java.util.regex.*;
import java.util.HashSet;
import java.util.Set;

import static gitlet.Utils.*;
import static gitlet.Wrapper.*;

public class Repository {

    private static final Set<String> validTypes = new HashSet<String>();
    static {
        validTypes.add("commit");
        validTypes.add("tree");
        validTypes.add("blob");
    }

    // init
    public static void init() {
        Data.assertNotInitialized();
        Base.init();
    }

    // hash-object
    public static void hashObject(String filename) {
        Data.assertInitialized();
        System.out.println(Base.hashBlob(filename));
    }

    // cat-file
    public static void catFile(String oid, String type) {
        Data.assertInitialized();
        assertCondition(validTypes.contains(type), "Invalid type: " + type);
        Data.assertObjectExists(oid);
        writeContents(System.out, Data.readObject(oid, type));
    }

    /* add - Stages a file to the gitlet repository. Staging an already-staged 
     * file overwrites the previous entry in the staging area with the new 
     * contents. */
    public static void add(String filename) {
        Data.assertInitialized();
        Base.add(filename);
    }

    /* rm - Unstages a file from the gitlet repository and remove the file from
     * the working directory if the user has not already done so. */  
    public static void rm(String filename) {
        Data.assertInitialized();
        Base.rm(filename);
    }

    /* write-tree - Create a tree object from the current index, and print the
     * id of the new tree object. */
    public static void writeTree() {
        Data.assertInitialized();
        System.out.println(Base.writeTree());
    }

    // commit
    public static void commit(String message) {
        Data.assertInitialized();
        System.out.println(Base.commit(message));
    }

    // log
    public static void log() {
        Data.assertInitialized();
        System.out.print(Base.log());
    }

    /** ls-tree - List the contents of a tree object recursively. This command
     * implements the git ls-tree -r command. */
    public static void lsTree(String oid) {
        Data.assertInitialized();
        Data.assertObjectExists(oid);
        System.out.println(Base.lsTree(oid));
    }

    /** checkout - Convert the working directory to the specified commit.
     * Only when all the files that will be overwritten have been staged, the
     * checkout operation will succeed, other files will be left unchanged. */
    public static void checkout(String name) {
        Data.assertInitialized();
        if (Data.isBranch(name)) {
            Base.checkoutBranch(name);
            System.out.printf("Switched to branch '%s'.\n", name);
        } else if (Data.isTag(name)) {
            String oid = getTag(name);
            Base.checkoutCommit(oid);
            System.out.printf("Switched to tag '%s'.\n", name);
        } else if (Data.isCommitId(name)) {
            Data.assertObjectExists(name);
            Base.checkoutCommit(name);
            System.out.printf("Switched to commit '%s'.\n", name);
        } else {
            error(String.format("pathspec '%s' did not match any file(s) "
                    + "known to gitlet", name));
        }
    }

    // status
    public static void status() {
        Data.assertInitialized();
        System.out.print(Base.status());
    }

    // tag 
    public static void tag(String name) {
        Data.assertInitialized();
        Base.createTag(name, Data.getHead());
    }

    // tag
    public static void tag(String tagname, String name) {
        Data.assertInitialized();
        String oid = getOid(name);
        Data.assertObjectExists(oid);
        Base.createTag(tagname, oid);
    }

    // tag-list
    public static void tag() {
        Data.assertInitialized();
        System.out.print(Base.tagList());
    }

    // branch-list
    public static void branch() {
        Data.assertInitialized();
        System.out.print(Base.branchList());
    }

    // branch
    public static void branch(String name) {
        Data.assertInitialized();
        Base.createBranch(name, Data.getHead());
    }

    // merge-base
    public static void mergeBase(String name1, String name2) {
        Data.assertInitialized();
        String oid1 = getOid(name1);
        String oid2 = getOid(name2);
        Data.assertObjectExists(oid1);
        Data.assertObjectExists(oid2);
        System.out.println(Base.mergeBase(oid1, oid2));
    }

    // merge
    public static void merge(String name) {
        Data.assertInitialized();
        System.out.println(Base.merge(name));
    }
}
