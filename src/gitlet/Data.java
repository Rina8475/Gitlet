package gitlet;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
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
    public static final File IGNORES_FILE = join(Base.BASE_PATH, 
        ".gitletignore");

    private static final byte NULL_BYTE = 0;

    static class Commit {
        private final String tree;
        private final String parent;
        private final String message;

        public Commit(String tree, String parent, String message) {
            this.tree = tree;
            this.parent = parent;
            this.message = message;
        }

        public String toString() {
            String parentLine = parent == null ? "" : String.format(
                "parent %s\n", parent);
            return String.format("tree %s\n%s\n%s\n", tree, parentLine, message);
        }

        public String getTree() { return tree; }
        public String getParent() { return parent; }
        public String getMessage() { return message; }
    }

    /** Initializes the .gitlet directory and creates the necessary files. */
    public static void init() {
        createDirectory(VIEW_DIR);
        createDirectory(join(VIEW_DIR, "refs"));
        createDirectory(join(VIEW_DIR, "objects"));
        createFile(join(VIEW_DIR, "HEAD"));
        createFile(join(VIEW_DIR, "index"));
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

    public static void setHead(String ref) {
        writeContents(HEAD_FILE, ref);
    }

    /** Returns the id of the current HEAD commit. */
    public static String getHead() {
        return readContentsAsString(HEAD_FILE);
    }

    public static String writeCommit(Commit commit) {
        String content = commit.toString();
        return hashObject(content.getBytes(StandardCharsets.UTF_8), "commit");
    }

    public static Commit readCommit(String id) {
        String content = new String(readObject(id, "commit"), StandardCharsets
            .UTF_8);
        String[] lines = content.split("\n+");
        String tree = lines[0].split(" ")[1];
        String[] tokens = lines[1].split(" ");
        boolean hasParent = tokens[0].equals("parent");
        String parent = hasParent ? tokens[1] : null;
        String message = hasParent ? lines[2] : lines[1];
        return new Commit(tree, parent, message);
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

    /** Just calculate the SHA value of the specified content.
     * @return the SHA-1 value of the content. */
    public static String restrictedHashObject(byte[] content, String type) {
        byte[] withType = addTypeInfo(content, type);
        return sha1(withType);
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
        assert objFile.exists() : "Object " + id + " does not exist.";

        byte[] content = readContents(objFile);
        int nullIndex = indexOf(content, NULL_BYTE);
        assert nullIndex != -1 : "Invalid object format: " + id;
        String actualType = new String(content, 0, nullIndex, StandardCharsets
            .UTF_8);
        assertCondition(type.equals(actualType), "Object type mismatch," 
            + " expected " + type + " but got " + actualType);
        return Arrays.copyOfRange(content, nullIndex + 1, content.length);
    }

    /** Write the index entries to the index file. */
    public static void writeIndex(Map<String, String> index) {
        StringBuilder content = new StringBuilder();
        for (String path : index.keySet()) {
            String sha1 = index.get(path);
            content.append(String.format("%s %s\n", path, sha1));
        }
        writeContents(INDEX_FILE, content.toString());
    }

    /** Read the index entries from the index file. */
    public static Map<String, String> readIndex() {
        Map<String, String> index = new HashMap<>();
        String content = readContentsAsString(INDEX_FILE);
        if (content.isEmpty()) {
            return index;
        }
        for (String line : content.split("\n")) {
            String[] parts = line.split(" ");   // path sha1
            index.put(parts[0], parts[1]);
        }
        return index;
    }

    /** Returns the base path of the current working directory. */
    public static String getBasePath() {
        String path = join(GITLET_DIR, "..").getAbsolutePath();
        return path.endsWith("/") ? path : path + "/";
    }

    public static List<String> getIgnorePatterns() {
        if (!IGNORES_FILE.exists()) {
            return Arrays.asList();
        }
        String content = readContentsAsString(IGNORES_FILE);
        if (content.isEmpty()) {
            return Arrays.asList();
        }
        return Arrays.asList(content.split("\n"));
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

    public static void assertObjectExists(String id) {
        File objFile = join(OBJS_DIR, id);
        assertFileExists(objFile);
    }
}
