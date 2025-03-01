package gitlet;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Queue;
import java.util.LinkedList;
import java.util.HashSet;
import java.util.ArrayList;
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
    public static final File BRANCH_DIR = join(REFS_DIR, "heads");
    public static final File TAG_DIR = join(REFS_DIR, "tags");
    /* base directory is the parent directory of the .gitlet directory */
    public static final File BASE_DIR = join(GITLET_DIR, "..");
    public static final File IGNORES_FILE = join(BASE_DIR, ".gitletignore");

    public static final String REF_PREFIX = "ref: ";
    public static final int REF_PREFIX_LEN = REF_PREFIX.length();
    private static final byte NULL_BYTE = 0;

    /*********************
     * Commit Operations *
     *********************/
    private static class Commit {
        private final String tree;
        private final String message;
        private final List<String> parents;

        Commit(String tid, String msg, String... pids) {
            this.tree = tid;
            this.message = msg;
            this.parents = Arrays.asList(pids);
        }
    }

    public static String writeCommit(String tid, String msg, String... pids) {
        String parentStr = String.join(" ", pids);
        parentStr = parentStr.isEmpty() ? "" : String.format("parent %s\n",
            parentStr);
        String content = String.format("tree %s\n%s\n%s\n", tid, parentStr, msg);
        return hashObject(content.getBytes(StandardCharsets.UTF_8), "commit");
    }

    private static Commit readCommit(String id) {
        String content = new String(readObject(id, "commit"), StandardCharsets
            .UTF_8);
        String[] lines = content.split("\n+");
        String tree = lines[0].split(" ")[1];
        String[] tokens = lines[1].split(" ");
        boolean hasParent = tokens[0].equals("parent");
        String message = hasParent ? lines[2] : lines[1];
        String[] parents = hasParent ? Arrays.copyOfRange(tokens, 1, tokens
            .length) : new String[0];
        return new Commit(tree, message, parents);
    }

    public static String getCommitTree(String id) {
        return readCommit(id).tree;
    }

    public static String getCommitMessage(String id) {
        return readCommit(id).message;
    }

    public static List<String> getCommitParents(String id) {
        return readCommit(id).parents;
    }

    /** This method back to the ancestor of the given commit and collects all 
     * the ids of the commits. 
     * This method is used for log command and finding the LCA of two commits.
     * @returns the list of the commits that are ancestors of the given commit.
     */
    public static List<String> getCommitAncestors(String id) {
        List<String> ancestors = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        visited.add(id);
        queue.add(id);
        while (!queue.isEmpty()) {
            String commit = queue.poll();
            ancestors.add(commit);
            for (String parent : getCommitParents(commit)) {
                if (!visited.contains(parent)) {
                    visited.add(commit);
                    queue.add(parent);
                }
            }
        }
        return ancestors;
    }

    /******************
     * Ref Operations *
     ******************/
    public static boolean isCommitId(String id) {
        return id.matches("^[0-9a-f]{40}$");
    }

    public static boolean isBranch(String name) {
        File branchFile = join(BRANCH_DIR, name);
        return branchFile.exists() && branchFile.isFile();
    }

    public static boolean isTag(String name) {
        File tagFile = join(TAG_DIR, name);
        return tagFile.exists() && tagFile.isFile();
    }

    /** Write the content to the HEAD directly. */
    public static void writeHead(String content) {
        writeRef("HEAD", content);
    }

    /** This operation is the reverse of writeHead. It reads the content of the
     * HEAD.
     * @return the local branch name or the commit id pointed by the HEAD. */
    public static String readHead() {
        String content = readContentsAsString(HEAD_FILE);
        if (content.startsWith(REF_PREFIX)) {
            return basename(content.substring(REF_PREFIX_LEN));
        } else {
            return content;
        }
    }

    public static void updateHead(String ref) {
        updateRef("HEAD", ref);
    }

    /** Returns the id of the current HEAD commit. */
    public static String getHead() {
        return getRef("HEAD");
    }

    /** Write the content to the REF directly.
     * if the content is a local branch name, then write "ref: refs/heads/name"
     * if the content is a commit id, then write the commit id directly.
     * @param ref is the path of the ref to write, relative to the .gitlet 
     * directory, e.g. "refs/heads/master".
     * @param content is just the commit id or the local branch name, i.e. 
     * "master". */
    private static void writeRef(String ref, String content) {
        File refFile = join(GITLET_DIR, ref);
        if (isBranch(content)) {
            writeContents(refFile, REF_PREFIX + "refs/heads/" + content);
        } else if (isCommitId(content)) {
            writeContents(refFile, content);
        } else {
            error("Invalid ref content: " + content);
        }
    }

    /** @param ref the path of the ref to get, relative to the .gitlet 
     * directory.
     * @return the id of the commit pointed by the given ref */
    public static String getRef(String ref) {
        File refFile = join(GITLET_DIR, ref);
        String content = readContentsAsString(refFile);
        if (content.startsWith(REF_PREFIX)) {
            return getRef(content.substring(REF_PREFIX_LEN));
        } else {
            return content;
        }
    }

    /** Update the deepest-ref with the given content. */
    public static void updateRef(String ref, String content) {
        File refFile = join(GITLET_DIR, ref);
        String refContent = readContentsAsString(refFile);
        if (refContent.startsWith(REF_PREFIX)) {
            updateRef(refContent.substring(REF_PREFIX_LEN), content);
        } else {
            writeContents(refFile, content);
        }
    }

    /** Create a new ref with the given content.
     * @param ref the path of the ref to create, relative to the .gitlet 
     * directory. */
    public static void createRef(String ref, String content) {
        File refFile = join(GITLET_DIR, ref);
        createFile(refFile);
        writeRef(ref, content);
    }

    /** Initializes the .gitlet directory and creates the necessary files. */
    public static void init() {
        createDirectory(VIEW_DIR);
        createDirectory(join(VIEW_DIR, "refs"));
        createDirectory(join(VIEW_DIR, "objects"));
        createDirectory(join(VIEW_DIR, "refs", "heads"));
        createDirectory(join(VIEW_DIR, "refs", "tags"));
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
        String path = BASE_DIR.getAbsolutePath();
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
