package gitlet;

import java.io.File;

import static gitlet.Utils.*;

public class Base {
    /** Creates a new blob object with the given file. */
    public static String hashObject(String filename) {
        File file = new File(filename);
        assertCondition(file.exists(), "File does not exist: " + filename);
        assertCondition(file.isFile(), "Not a file: " + filename);
        return Data.hashObject(readContents(file), "blob");
    }
}
