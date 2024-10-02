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
}
