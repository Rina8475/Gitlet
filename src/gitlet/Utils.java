/* This class implements some utility functions used by Gitlet. */

package gitlet;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;

public class Utils {
    
    public static String sha1(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hashBytes = digest.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("System does not support SHA-1");
        }
    }

    /* Returns the SHA-1 hash of the given string. */
    public static String sha1(String str) {
        return sha1(str.getBytes(StandardCharsets.UTF_8));
    }

    public static void createFile(File file) {
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public static void createDirectory(File dir) {
        assert !dir.exists() : "Directory already exists: " + dir.getPath();
        dir.mkdirs();
    }

    /** Reads the contents of the given file as a byte array.
     * Assumes the file exists and is a file.
     * @throws IllegalArgumentException in case of I/O errors. */
    public static byte[] readContents(File file) {
        assert file.exists() : "File does not exist: " + file.getPath();
        assert file.isFile() : "Not a file: " + file.getPath();
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public static String readContentsAsString(File file) {
        return new String(readContents(file), StandardCharsets.UTF_8);
    }

    /** Writes the given string to the given file, encoded in UTF-8.
     * Assumes the file exists and is a file.
     * @throws IllegalArgumentException in case of I/O errors. */
    public static void writeContents(File file, String contents) {
        writeContents(file, contents.getBytes(StandardCharsets.UTF_8));
    }

    public static void writeContents(File file, byte[] contents) {
        assert file.exists() : "File does not exist: " + file.getPath();
        assert file.isFile() : "Not a file: " + file.getPath();
        try {
            Files.write(file.toPath(), contents);
        } catch (IOException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public static void writeContents(OutputStream out, byte[] contents) {
        try {
            out.write(contents);
        } catch (IOException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public static File join(String parent, String... others) {
        return Path.of(parent, others).normalize().toFile();
    }

    public static File join(File parent, String... others) {
        return Path.of(parent.getPath(), others).normalize().toFile();
    }

    public static void assertCondition(boolean condition, String message) {
        if (!condition) {
            System.err.println(message);
            System.exit(1);
        }
    }

    public static int indexOf(byte[] array, byte b) {
        for (int i = 0; i < array.length; i += 1) {
            if (array[i] == b) {
                return i;
            }
        }
        return -1;
    }

    /** Gets the parent directory of the given path. */
    public static String dirname(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash == -1) {
            return "";
        }
        return path.substring(0, lastSlash);
    }

    public static String basename(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash == -1) {
            return path;
        }
        return path.substring(lastSlash + 1);
    }
}
