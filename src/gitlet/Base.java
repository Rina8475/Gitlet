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

import static gitlet.Utils.*;
import static gitlet.Wrapper.*;

public class Base {
    public static final String BASE_PATH = Data.getBasePath();
    public static final int BASE_LENGTH = BASE_PATH.length();

    /** Initializes the repository. */
    public static void init() {
        Data.init();
        String oid = Data.writeCommit(writeTree(), "initial commit");
        createBranch("master", oid);
        Data.writeHead("master");
    }

    /** Creates a new blob object with the given file. */
    public static String hashBlob(String filename) {
        File file = new File(filename);
        assertFileExists(file);
        return hashBlob(file);
    }

    public static String hashBlob(File file) {
        return Data.hashObject(readContents(file), "blob");
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
        return writeTree(index);
    }

    private static String writeTree(Map<String, String> index) {
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
        String oid = Data.writeCommit(writeTree(), message, parent);
        Data.updateHead(oid);
        return oid;
    }

    /** @return the log of all the commits. */
    public static String log() {
        String oid = Data.getHead();
        List<String> commits = Data.getCommitAncestors(oid);
        Collection<String> lines = map(commits, (cid) -> {
            String message = Data.getCommitMessage(cid);
            return String.format("commit %s\n\n%s\n", cid, message);
        });
        return String.join("\n", lines);
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

    public static void checkoutBranch(String branch) {
        String current = Data.readHead();
        assertCondition(!(Data.isBranch(current) && current.equals(branch)), 
            String.format("Already on '%s'", branch));
        String oid = getBranch(branch);
        checkout(oid);
        Data.writeHead(branch);
    }

    public static void checkoutCommit(String oid) {
        checkout(oid);
        Data.writeHead(oid);
    }

    /** Reads the commit OID and overwrites the working directory with the 
     * commit. 
     * After this operation, the staging area will be empty, i.e., the index is
     * identical with the target commit. */
    private static void checkout(String oid) {
        // 0. current commit    1. target commit    2. current working dir
        String tid0 = Data.getCommitTree(Data.getHead());
        String tid1 = Data.getCommitTree(oid);
        Map<String, String> fileSet0 = readTree(tid0);
        Map<String, String> fileSet1 = readTree(tid1);
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
        // rewrites the index with the target commit.
        Data.writeIndex(fileSet1);
    }

    /** Checks if the file FILE is identical in the two sets of files.
     * @param file the path of the file to be checked, which is relative to the
     * repository root.
     * @param set1 the files and corresponding hashes in the first set. 
     * @return true if 1. file not in set1 && file not in set2
     *                 2. file in set1 && file in set2
     *                    && set1.get(file) == set2.get(file) */
    private static boolean isIdentical(String file, Map<String, String> set1, 
        Map<String, String> set2) {
        boolean cond1 = set1.containsKey(file);
        boolean cond2 = set2.containsKey(file);
        return !cond1 && !cond2 || cond1 && cond2 && set1.get(file)
            .equals(set2.get(file));
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
        String current = Data.readHead();
        String content = "";
        // get the current position of HEAD
        if (Data.isBranch(current)) {
            content += String.format("On branch %s\n", basename(current));
        } else {
            content += String.format("HEAD detached at %s\n", current);
        }
        content += statusHeadIndex(Data.readIndex());
        content += statusIndexWorkingDir(Data.readIndex());
        return content;
    }

    private static String statusHeadIndex(Map<String, String> index) {
        // 0. head - index   1. head & index   2. index - head
        //    deleted files     modified files    new files
        String oid = Data.getHead();
        Map<String, String> commitSet = readTree(Data.getCommitTree(oid));
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

    /** creates a new tag. */
    public static void createTag(String name, String oid) {
        assertCondition(!Data.isTag(name), String.format("tag '%s' already " 
            + "exists", name));
        Data.createRef("refs/tags/" + name, oid);
    }

    public static String tagList() {
        Collection<String> tags = getTags();
        StringBuilder content = new StringBuilder();
        forEach(tags, (tag) -> content.append(tag + "\n"));
        return content.toString();
    }

    private static Collection<String> getTags() {
        List<File> paths = getFiles(Data.TAG_DIR);
        return map(paths, (path) -> basename(path.getAbsolutePath()));
    }

    /** creates a new branch.
     * @param name the name of the branch to be created. i.e. "master"
     * @param content the id of a commit object or the name of another branch. 
     * i.e. "master" */
    public static void createBranch(String name, String content) {
        assertCondition(!Data.isBranch(name), String.format("branch '%s' "
            + "already exists", name));
        Data.createRef("refs/heads/" + name, content);
    }

    public static String branchList() {
        Collection<String> branches = getBranches();
        StringBuilder content = new StringBuilder();
        forEach(branches, (branch) -> content.append(branch + "\n"));
        return content.toString();
    }

    private static Collection<String> getBranches() {
        List<File> paths = getFiles(Data.BRANCH_DIR);
        return map(paths, (path) -> basename(path.getAbsolutePath()));
    }

    /** @return the least common ancestor of the two commits. if not found such
     * ancestor, returns null. */
    public static String mergeBase(String cid1, String cid2) {
        Set<String> ancestors1 = new HashSet<>(Data.getCommitAncestors(cid1));
        for (String ancestor : Data.getCommitAncestors(cid2)) {
            if (ancestors1.contains(ancestor)) {
                return ancestor;
            }
        }
        return null;
    }

    public static String merge(String name) {
        // TODO: check if the working directory is clean
        String remote = getOid(name);
        String local = Data.getHead();
        String lca = mergeBase(local, remote);
        if (lca.equals(remote)) {           // non-fast-forward merge
            return "Already up to date.";
        } else if (lca.equals(local)) {     // fast-forward merge 
            checkout(remote);
            Data.updateHead(remote);
            return "Fast-forward merge.";
        }
        // three-way merge
        String merged = threeWayMerge(lca, local, remote, String.format("Merge"
            + " with '%s'", name));
        checkout(merged);
        Data.updateHead(merged);
        return "Merge made by the three-way merge.";
    }

    private static String threeWayMerge(String base, String local, String 
        remote, String msg) {
        Map<String, String> baseTree = readTree(Data.getCommitTree(base));
        Map<String, String> localTree = readTree(Data.getCommitTree(local));
        Map<String, String> remoteTree = readTree(Data.getCommitTree(remote));
        assertCondition(!isConflict(baseTree, localTree, remoteTree),
            "Conflicts existed.");
        Map<String, String> merged = threeWayMerge(baseTree, localTree,
            remoteTree);
        return Data.writeCommit(writeTree(merged), msg, local, remote);
    }

    private static boolean isConflict(Map<String, String> base,
        Map<String, String> local, Map<String, String> remote) {
        Set<String> targetFiles = union(local.keySet(), remote.keySet());
        return any(targetFiles, (file) -> !isIdentical(file, base, local) &&
            !isIdentical(file, base, remote));
    }

    private static Map<String, String> threeWayMerge(Map<String, String>
        base, Map<String, String> local, Map<String, String> remote) {
        Map<String, String> merged = new HashMap<>();
        Set<String> targetFiles = union(local.keySet(), remote.keySet());
        forEach(targetFiles, (file) -> {
            String localHash = local.get(file);
            String remoteHash = remote.get(file);
            boolean cond = isIdentical(file, base, local);
            String mergedHash = cond ? remoteHash : localHash;
            if (mergedHash != null) {
                merged.put(file, mergedHash);
            }
        });
        return merged;
    }
}
