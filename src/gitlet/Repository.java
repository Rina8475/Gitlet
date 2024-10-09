/* This class implements the repository operations. */

package gitlet;

import static gitlet.Utils.assertCondition;
import static gitlet.Utils.writeContents;
import java.util.HashSet;
import java.util.Set;

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
        Data.init();
    }

    // hash-object
    public static void hashObject(String filename) {
        Data.assertInitialized();
        System.out.println(Base.hashObject(filename));
    }

    // cat-file
    public static void catFile(String oid, String type) {
        Data.assertInitialized();
        assertCondition(validTypes.contains(type), "Invalid type: " + type);
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
}
