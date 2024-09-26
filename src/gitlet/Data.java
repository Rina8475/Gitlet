package gitlet;

import java.io.File;

import static gitlet.Utils.*;

public class Data {

    /* The working directory, the.gitlet directory, and the files in the.gitlet
     directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    public static final File HEAD_FILE = join(GITLET_DIR, "HEAD");
    public static final File INDEX_FILE = join(GITLET_DIR, "index");
    public static final File REFS_DIR = join(GITLET_DIR, "refs");
    public static final File OBJS_DIR = join(GITLET_DIR, "objects");

    /** Initializes the .gitlet directory and creates the necessary files. */
    public static void init() {
        createDirectory(GITLET_DIR);
        createDirectory(OBJS_DIR);
        createDirectory(REFS_DIR);
        createFile(HEAD_FILE);
        createFile(INDEX_FILE);

        // TODO create initial commit and branch (master)
    }

    /** Creates a new object with the given type and content. */
    public static String hashObject(String filename) {
        File file = new File(filename);
        assertCondition(file.exists(), "File does not exist: " + filename);

        byte[] content = readContents(file);
        String id = sha1(content);
        File objFile = join(OBJS_DIR, id);
        if (!objFile.exists()) {    // create object file if it doesn't exist
            createFile(objFile);
            writeContents(objFile, content);
        }
        return id;
    }

    public static byte[] readObject(String id) {
        File objFile = join(OBJS_DIR, id);
        assertCondition(objFile.exists(), "Object does not exist: " + id);
        return readContents(objFile);
    }

    /** Asserts that the current directory is in an initialized Gitlet directory. */
    public static void assertInitialized() {
        assertCondition(GITLET_DIR.exists(), "Not in an initialized Gitlet "
                + "directory.");
    }

    public static void assertNotInitialized() {
        assertCondition(!GITLET_DIR.exists(), "A Gitlet version-control system"
                + " already exists in the current directory.");
    }
}
