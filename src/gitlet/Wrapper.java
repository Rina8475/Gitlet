package gitlet;

import static gitlet.Utils.*;

import java.io.File;

public class Wrapper {
    public static final String BASE_PATH = Data.getBasePath();
    public static final int BASE_LENGTH = BASE_PATH.length();

    /** Just calculate the SHA value of the specified file, without create the
     * blob object in the repository. */
    public static String restrictedHashBlob(String filename) {
        File file = join(BASE_PATH, filename);
        return Data.restrictedHashObject(readContents(file), "blob");
    }

    /** gets the relative path of the file from the repository root. */
    public static String getRelativePath(File file) {
        return file.getAbsolutePath().substring(BASE_LENGTH);
    }

    public static String getRelativePath(String fullPath) {
        return fullPath.substring(BASE_LENGTH);
    }

    /** @return the id of the commit pointed by the given branch */
    public static String getBranch(String name) {
        return Data.getRef("refs/heads/" + name);
    }

    /** @return the id of the commit pointed by the given tag */
    public static String getTag(String name) {
        return Data.getRef("refs/tags/" + name);
    }

    /** @return the commit id of the given name */
    public static String getOid(String name) {
        if ("HEAD".equals(name)) {
            return Data.getHead();
        } else if (Data.isBranch(name)) {
            return getBranch(name);
        } else if (Data.isTag(name)) {
            return getTag(name);
        } else if (Data.isCommitId(name)) {
            return name;
        } 
        error("unknown name: " + name);
        return null;
    }
}
