package gitlet;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;
import java.io.File;
import java.nio.charset.StandardCharsets;

import gitlet.Data.Commit;
import static gitlet.Utils.*;

public class Base {
    public static final String BASE_PATH = Data.getBasePath();
    public static final int BASE_LENGTH = BASE_PATH.length();

    /** Initializes the repository. */
    public static void init() {
        Data.init();
        String oid = Data.writeCommit(new Commit(writeTree(), null, 
            "initial commit"));
        Data.setHead(oid);
    }

    /** Creates a new blob object with the given file. */
    public static String hashObject(String filename) {
        File file = new File(filename);
        assertFileExists(file);
        return hashObject(file);
    }

    private static String hashObject(File file) {
        return Data.hashObject(readContents(file), "blob");
    }

    /** adds the file to the staging area. */
    public static void add(String filename) {
        File file = new File(filename);
        assertFileExists(file);
        String fullPath = file.getAbsolutePath();
        assertCondition(fullPath.startsWith(BASE_PATH), "Not in repository: " 
            + filename);
        // remove the entry from the index if it exists
        unstageFile(getRelativePath(fullPath));

        List<File> newFiles = getFiles(file);
        Map<String, String> index = Data.readIndex();
        for (File newfile : newFiles) {
            String id = hashObject(newfile);
            String relatPath = getRelativePath(newfile);
            index.put(relatPath, id);
        }
        Data.writeIndex(index);
    }

    /** gets all the files in the path. 
     * @return a list of all the files in the path. If the path is a file, 
     * returns a list with that file. If the path is a directory, returns 
     * a list with all the files in the directory and its subdirectories. */
    private static List<File> getFiles(File file) {
        List<File> files = new ArrayList<File>();
        if (file.isFile()) {
            files.add(file);
        } else if (file.isDirectory()) {
            getFiles(file, files);
        }
        return files;
    }

    private static void getFiles(File dir, List<File> files) {
        File[] subfiles = dir.listFiles();
        for (File subfile : subfiles) {
            if (subfile.isFile()) {
                files.add(subfile);
            } else if (subfile.isDirectory()) {
                getFiles(subfile, files);
            }
        }
    }

    /** gets the relative path of the file from the repository root. */
    private static String getRelativePath(File file) {
        return file.getAbsolutePath().substring(BASE_LENGTH);
    }

    private static String getRelativePath(String fullPath) {
        return fullPath.substring(BASE_LENGTH);
    }

    /** removes the file from the staging area. */
    public static void rm(String filename) {
        File file = new File(filename);
        String fullPath = file.getAbsolutePath();
        assertCondition(fullPath.startsWith(BASE_PATH), "Not in repository: "
            + filename);
        String relatPath = getRelativePath(fullPath);
        Set<String> unstagedFiles = unstageFile(relatPath);
        assertCondition(!unstagedFiles.isEmpty(), "No reason to remove the "
            + "file.");
        
        // Remove the files from the working directory.
        for (String unstagedFile : unstagedFiles) {
            File unstaged = new File(BASE_PATH + unstagedFile);
            if (unstaged.exists()) {
                unstaged.delete();
            }
        }
    }

    /** unstages the matched files from the index.
     * @return the set of files that were unstaged. */
    private static Set<String> unstageFile(String path) {
        Map<String, String> index = Data.readIndex();
        Set<String> oldKeys = new HashSet<>(index.keySet());
        index.keySet().removeIf(p -> p.startsWith(path));
        Data.writeIndex(index);
        oldKeys.removeAll(index.keySet());
        return oldKeys;
    }

    /** Create a tree object from the current index.
     * @return the hash of the new tree object. */
    public static String writeTree() {
        Map<String, String> index = Data.readIndex();
        // transfrom the index entries into tree entries
        Map<String, List<String>> tree = new TreeMap<>((x, y) -> y.length() 
            - x.length());
        tree.put("", new ArrayList<>());
        for (String file : index.keySet()) {
            // create all the parent directories of the file in the tree
            for (String key = dirname(file); !key.isEmpty(); key 
                = dirname(key)) {
                if (!tree.containsKey(key)) {
                    tree.put(key, new ArrayList<>());
                }
            }
            String entry = String.format("%s %s %s\n", "blob", index.get(file), 
                basename(file));
            tree.get(dirname(file)).add(entry);
        }
        // construct the tree object
        String tid = null;
        for (String dir : tree.keySet()) {
            String content = String.join("", tree.get(dir));
            tid = Data.hashObject(content.getBytes(StandardCharsets
                .UTF_8), "tree");
            String parent = dirname(dir);
            String base = basename(dir);
            String entry = String.format("%s %s %s\n", "tree", tid, base);
            tree.get(parent).add(entry);
        }
        return tid;
    }

    /** Commits the changes in the working directory to the repository, creates
     * a new commit object, and updatess the HEAD pointer to point to the new
     * commit.
     * @return the hash of the new commit object. */
    public static String commit(String message) {
        String parent = Data.getHead();
        Commit commit = new Commit(writeTree(), parent, message);
        String oid = Data.writeCommit(commit);
        Data.setHead(oid);
        return oid;
    }

    /** @return the log of all the commits. */
    public static String log() {
        String oid = Data.getHead();
        StringBuilder content = new StringBuilder();
        while (oid != null) {
            Commit commit = Data.readCommit(oid);
            content.append(String.format("commit %s\n%s\n", oid, commit
                .toString()));
            oid = commit.getParent();
        }
        return content.toString();
    }

    /** @return the contents of the tree object. */
    public static String lsTree(String oid) {
        Map<String, String> tree = readTree(oid);
        Collection<String> lines = map(tree.keySet(), (key) -> String
            .format("%s %s", tree.get(key), key));
        return String.join("\n", lines);
    }

    /** Reads the tree information given by OID.  
     * @return the the contents of a tree object by the format of index. */
    private static Map<String, String> readTree(String oid) {
        Map<String, String> contents = new HashMap<>();
        List<String[]> entries = new ArrayList<>();
        readTree(oid, "", entries);
        forEach(entries, e -> contents.put(e[0], e[1]));
        return contents;
    }

    private static void readTree(String oid, String base, List<String[]> entries) {
        String content = new String(Data.readObject(oid, "tree"), 
            StandardCharsets.UTF_8);
        for (String line : content.split("\n")) {
            String[] fields = line.split(" ");     // type, oid, name
            if ("blob".equals(fields[0])) {
                entries.add(new String[] {joinPaths(base, fields[2]), 
                    fields[1]});
            } else if ("tree".equals(fields[0])) {
                readTree(fields[1], joinPaths(base, fields[2]), entries);
            }
        }
    }

    /** Reads the commit OID and overwrites the working directory with the 
     * commit. */
    public static void checkout(String oid) {
        // 0. current commit    1. target commit    2. current working dir
        Commit c0 = Data.readCommit(Data.getHead());
        Commit c1 = Data.readCommit(oid);
        Map<String, String> fileSet0 = readTree(c0.getTree());
        Map<String, String> fileSet1 = readTree(c1.getTree());
        Map<String, String> fileSet2 = readWorkingDir();

        // checks if all the files will be overwritten is tracked.
        boolean allTracked = all(fileSet1.keySet(), (file) -> isCommitted(file, 
            fileSet0, fileSet2));
        assertCondition(allTracked, "Not all files are tracked.");
        // deletes all the files that will be overwritten.
        Collection<String> deletedFiles = filter(fileSet0.keySet(), (file) -> 
            isCommitted(file, fileSet0, fileSet2));
        forEach(deletedFiles, (file) -> deleteFile(join(BASE_PATH, file)));
        // copies the files from the index to the working directory.
        forEach(fileSet1.keySet(), (file) -> writeWorkingDir(file, fileSet1
            .get(file)));
        // updates the HEAD pointer to point to the new commit.
        Data.setHead(oid);
    }

    /** Checks if the file FILENAME is committed in the current commit.
     * @param commit the files and corresponding hashes in the current commit.
     * @param workDir the files and corresponding hashes in the working 
     * directory. */
    private static boolean isCommitted(String filename, Map<String, String> 
        commit, Map<String, String> workDir) {
        return commit.containsKey(filename) == workDir.containsKey(filename); 
    }

    /** @return the files and corresponding hashes in the working directory. */
    private static Map<String, String> readWorkingDir() {
        Map<String, String> contents = new HashMap<>();
        File base = new File(BASE_PATH);
        forEach(getFiles(base), (file) -> contents.put(getRelativePath(file), 
            hashObject(file)));
        return contents;
    }

    /** Checks if all the files is tracked by the current commit. */
    public static boolean isAllTracked(Collection<String> files) {
        String oid = Data.getHead();
        Commit commit = Data.readCommit(oid);
        Map<String, String> entries = readTree(commit.getTree());
        return all(files, (file) -> entries.containsKey(file));
    }

    /** Writes the object to the working directory as the given path. 
     * @path the path to the file to be created. the path is relative to the 
     * repository root. 
     * @oid the hash of the object to be written. Assumes the object is a blob.
     */
    private static void writeWorkingDir(String path, String oid) {
        byte[] content = Data.readObject(oid, "blob");
        File file = join(BASE_PATH, path);
        createFile(file);
        writeContents(file, content);
    }
}
