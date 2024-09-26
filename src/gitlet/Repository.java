/* This class implements the repository operations. */

package gitlet;

import static gitlet.Utils.writeContents;

public class Repository {
    
    // init
    public static void init() {
        Data.assertNotInitialized();
        Data.init();
    }

    // hash-object
    public static void hashObject(String filename) {
        Data.assertInitialized();
        System.out.println(Data.hashObject(filename));
    }

    // cat-file
    public static void catFile(String object) {
        Data.assertInitialized();
        writeContents(System.out, Data.readObject(object));
    }
}
