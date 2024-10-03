package gitlet;

import java.io.File;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;

import static gitlet.Utils.*;

public class Data {

    /* The working directory, the.gitlet directory, and the files in the.gitlet
     directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /* Points to the .gitlet directory in the current working directory, 
     * which may do not exist. */
    public static final File VIEW_DIR = join(CWD, ".gitlet");
    /* Points to the .gitlet directory, if does not find a such directory, then
     * it points to the VIEW_DIR. */
    public static final File GITLET_DIR = findGitlet();
    public static final File HEAD_FILE = join(GITLET_DIR, "HEAD");
    public static final File INDEX_FILE = join(GITLET_DIR, "index");
    public static final File REFS_DIR = join(GITLET_DIR, "refs");
    public static final File OBJS_DIR = join(GITLET_DIR, "objects");

    private static final byte NULL_BYTE = 0;

    /** Initializes the .gitlet directory and creates the necessary files. */
    public static void init() {
        createDirectory(VIEW_DIR);
        createDirectory(join(VIEW_DIR, "refs"));
        createDirectory(join(VIEW_DIR, "objects"));
        createFile(join(VIEW_DIR, "HEAD"));
        createFile(join(VIEW_DIR, "index"));

        // TODO create initial commit and branch (master)
    }

    private static File findGitlet() {
        File repo = findRepository(CWD);
        return repo == null ? VIEW_DIR : repo;
    }

    /** Finds the .gitlet directory from the PATH back to the root directory.
     * @return the .gitlet directory, or null if it doesn't exist. */
    private static File findRepository(File path) {
        File gitletDir = join(path, ".gitlet");
        if (gitletDir.exists() && gitletDir.isDirectory()) {
            return gitletDir;
        }
        File parent = join(path, "..");
        if (path.equals(parent)) {
            return null;
        }
        return findRepository(parent);
    }

    /** Create a new gitlet object with the given content and type.
     * @return the SHA-1 of the new object. */
    public static String hashObject(byte[] content, String type) {
        byte[] withType = addTypeInfo(content, type);
        String id = sha1(withType);
        File objFile = join(OBJS_DIR, id);
        if (!objFile.exists()) {    // create object file if it doesn't exist
            createFile(objFile);
            writeContents(objFile, withType);
        }
        return id;
    }

    static byte[] addTypeInfo(byte[] content, String type) {
        byte[] typeBytes = type.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[content.length + typeBytes.length + 1];
        System.arraycopy(typeBytes, 0, result, 0, typeBytes.length);
        result[typeBytes.length] = NULL_BYTE;
        System.arraycopy(content, 0, result, typeBytes.length + 1, content
            .length);
        return result;
    }

    /** Read the content of a gitlet object with the given id and type.
     * @return the content of the object. */
    public static byte[] readObject(String id, String type) {
        File objFile = join(OBJS_DIR, id);
        assertCondition(objFile.exists(), "Object does not exist: " + id);

        byte[] content = readContents(objFile);
        int nullIndex = indexOf(content, NULL_BYTE);
        assert nullIndex != -1 : "Invalid object format: " + id;
        String actualType = new String(content, 0, nullIndex, StandardCharsets
            .UTF_8);
        assertCondition(type.equals(actualType), "Object type mismatch," 
            + " expected " + type + " but got " + actualType);
        return Arrays.copyOfRange(content, nullIndex + 1, content.length);
    }

    /** Asserts that the current directory is in an initialized Gitlet directory. */
    public static void assertInitialized() {
        assertCondition(GITLET_DIR.exists(), "Not in an initialized Gitlet "
                + "directory.");
    }

    public static void assertNotInitialized() {
        assertCondition(!VIEW_DIR.exists(), "A Gitlet version-control system"
                + " already exists in the current directory.");
    }
}
