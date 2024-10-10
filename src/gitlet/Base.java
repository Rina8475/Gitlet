package gitlet;

import java.util.regex.*;
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
    public static String hashBlob(String filename) {
        File file = new File(filename);
        assertFileExists(file);
        return hashBlob(file);
    }

    private static String hashBlob(File file) {
        return Data.hashObject(readContents(file), "blob");
    }

    private static String restrictedHashBlob(String filename) {
        File file = join(BASE_PATH, filename);
        return Data.restrictedHashObject(readContents(file), "blob");
    }

    /** adds the file to the staging area. */
    public static void add(String filename) {
        File file = new File(filename);
        assertCondition(file.exists(), "File does not exist: " + file
            .getPath());
        String fullPath = file.getAbsolutePath();
        assertCondition(fullPath.startsWith(BASE_PATH), "Not in repository: " 
            + filename);
        // remove the entry from the index if it exists
        unstageFile(getRelativePath(fullPath));

        List<File> newFiles = getFiles(file);
        Map<String, String> index = Data.readIndex();
        for (File newfile : newFiles) {
            String id = hashBlob(newfile);
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

    private static void readTree(String oid, String base, List<String[]> 
        entries) {
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
        boolean allTracked = all(fileSet1.keySet(), (file) -> isIdentical(file, 
            fileSet0, fileSet2));
        assertCondition(allTracked, "Not all files are tracked.");
        // deletes all the files that will be overwritten.
        Collection<String> deletedFiles = filter(fileSet0.keySet(), (file) -> 
            isIdentical(file, fileSet0, fileSet2));
        forEach(deletedFiles, (file) -> deleteFile(join(BASE_PATH, file)));
        // copies the files from the index to the working directory.
        forEach(fileSet1.keySet(), (file) -> writeWorkingDir(file, fileSet1
            .get(file)));
        // updates the HEAD pointer to point to the new commit.
        Data.setHead(oid);
    }

    /** Checks if the file FILENAME is identical in the two sets of files.
     * @param set1 the files and corresponding hashes in the first set. */
    private static boolean isIdentical(String filename, Map<String, String> 
        set1, Map<String, String> set2) {
        return set1.containsKey(filename) == set2.containsKey(filename) 
            && set1.getOrDefault(filename, "").equals(set2
            .getOrDefault(filename, ""));
    }

    /** @return the files and corresponding hashes in the working directory. */
    private static Map<String, String> readWorkingDir() {
        Map<String, String> contents = new HashMap<>();
        File base = new File(BASE_PATH);
        Collection<String> paths = map(getFiles(base), (file) -> 
            getRelativePath(file));
        Collection<String> files = removeIgnored(paths);
        forEach(files, (file) -> contents.put(file, restrictedHashBlob(file)));
        return contents;
    }

    private static Collection<String> removeIgnored(Collection<String> paths) {
        List<String> patterns = Data.getIgnorePatterns();
        return filter(paths, (path) -> !isIgnored(path, patterns));
    }

    /** Checks if the path of the file is ignored.
     * @param path the path of the file to be checked. Assumes the path is 
     * relative to the repository root. 
     * @param patterns the list of ignore patterns. For efficiency, we pass
     * the patterns as a parameter instead of reading them from the repository.
     */
    private static boolean isIgnored(String path, List<String> patterns) {
        return path.startsWith(".gitlet") || any(patterns, (p) -> {
            Pattern pattern = Pattern.compile(p);
            return pattern.matcher(path).matches();
        });
    }

    /** Writes the object to the working directory as the given path. 
     * @param path the path to the file to be created. the path is relative to 
     * the repository root. 
     * @param oid the hash of the object to be written. Assumes the object is a
     * blob. */
    private static void writeWorkingDir(String path, String oid) {
        byte[] content = Data.readObject(oid, "blob");
        File file = join(BASE_PATH, path);
        createFile(file);
        writeContents(file, content);
    }

    /** @return the status of the repository. */
    public static String status() {
        String content = "";
        // TODO: get the current branch
        content += statusHeadIndex(Data.readIndex());
        content += statusIndexWorkingDir(Data.readIndex());
        return content;
    }

    private static String statusHeadIndex(Map<String, String> index) {
        // 0. head - index   1. head & index   2. index - head
        //    deleted files     modified files    new files
        String oid = Data.getHead();
        Commit commit = Data.readCommit(oid);
        Map<String, String> commitSet = readTree(commit.getTree());
        Set<String> set0 = difference(commitSet.keySet(), index.keySet());
        Set<String> set1 = intersection(commitSet.keySet(), index.keySet());
        Set<String> set2 = difference(index.keySet(), commitSet.keySet());
        Collection<String> modified = filter(set1, (file) -> !isIdentical(file,
            commitSet, index));

        String newFileStr  = "    new file: %s\n";
        String modifiedStr = "    modified: %s\n";
        String deletedStr  = "    deleted: %s\n";
        StringBuilder content = new StringBuilder();
        content.append("Changes to be committed:\n");
        forEach(set2, (f) -> content.append(String.format(newFileStr, f)));
        forEach(modified, (f) -> content.append(String.format(modifiedStr, 
            f)));
        forEach(set0, (f) -> content.append(String.format(deletedStr, f)));
        content.append("\n");
        return content.toString();
    }

    private static String statusIndexWorkingDir(Map<String, String> index) {
        // 0. index - work dir   1. index & work dir   2. work dir - index
        //    untracked files       modified files        deleted files
        Map<String, String> workSet = readWorkingDir();
        Set<String> set0 = difference(workSet.keySet(), index.keySet());
        Set<String> set1 = intersection(workSet.keySet(), index.keySet());
        Set<String> set2 = difference(index.keySet(), workSet.keySet());
        Collection<String> modified = filter(set1, (file) -> !isIdentical(file,
            workSet, index));

        String modifiedStr = "    modified: %s\n";
        String deletedStr  = "    deleted: %s\n";
        String untrackedStr  = "    %s\n";
        StringBuilder content = new StringBuilder();
        content.append("Changes not staged for commit:\n");
        forEach(modified, (f) -> content.append(String.format(modifiedStr, 
            f)));
        forEach(set2, (f) -> content.append(String.format(deletedStr, f)));
        content.append("\n");
        content.append("Untracked files:\n");
        forEach(set0, (f) -> content.append(String.format(untrackedStr, f)));
        return content.toString();
    }

}
