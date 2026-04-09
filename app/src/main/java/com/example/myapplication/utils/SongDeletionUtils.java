package com.example.myapplication.utils;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SongDeletionUtils {
    private SongDeletionUtils() {
    }

    public static boolean deleteAppOwnedAudioFile(Context context, String filePath) {
        if (context == null || filePath == null || filePath.isEmpty()) {
            return false;
        }

        Uri uri;
        try {
            uri = Uri.parse(filePath);
        } catch (Exception e) {
            uri = null;
        }

        String scheme = uri != null ? uri.getScheme() : null;
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme) || "content".equalsIgnoreCase(scheme)) {
            return false;
        }

        String path = filePath;
        if ("file".equalsIgnoreCase(scheme) && uri != null) {
            String p = uri.getPath();
            if (p != null && !p.isEmpty()) {
                path = p;
            }
        }

        File target = new File(path);
        if (!target.exists()) {
            return false;
        }

        if (!isUnderAppOwnedRoots(context, target)) {
            return false;
        }

        return deleteFileOrDirectory(target);
    }

    public static void deleteCoverCaches(Context context, String songName, String coverUrl) {
        if (context == null || coverUrl == null || coverUrl.isEmpty() || "null".equalsIgnoreCase(coverUrl)) {
            return;
        }

        ImageCacheManager.getInstance(context).remove(coverUrl);

        String fileName1 = "cover_" + coverUrl.hashCode() + ".jpg";
        deleteIfExists(new File(context.getFilesDir(), fileName1));

        if (songName != null && !songName.isEmpty()) {
            String fileName2 = "cover_" + Math.abs((songName + coverUrl).hashCode()) + ".jpg";
            deleteIfExists(new File(context.getFilesDir(), fileName2));
        }
    }

    public static int cleanupOrphanedAppOwnedAudioFiles(Context context, Set<String> referencedFilePaths) {
        if (context == null) {
            return 0;
        }

        Set<String> keep = new HashSet<>();
        if (referencedFilePaths != null) {
            for (String fp : referencedFilePaths) {
                String path = normalizeToPath(fp);
                if (path == null) {
                    continue;
                }
                try {
                    keep.add(new File(path).getCanonicalPath());
                } catch (IOException ignored) {
                }
            }
        }

        int deleted = 0;
        for (File dir : getCleanupDirs(context)) {
            if (dir == null || !dir.exists() || !dir.isDirectory()) {
                continue;
            }
            File[] files = dir.listFiles();
            if (files == null) {
                continue;
            }
            for (File f : files) {
                if (f == null || !f.exists() || f.isDirectory()) {
                    continue;
                }
                try {
                    String canonical = f.getCanonicalPath();
                    if (!keep.contains(canonical)) {
                        if (f.delete()) {
                            deleted++;
                        }
                    }
                } catch (IOException ignored) {
                }
            }
        }

        return deleted;
    }

    private static List<File> getCleanupDirs(Context context) {
        List<File> dirs = new ArrayList<>();

        File audioDir = context.getExternalFilesDir("audio");
        if (audioDir != null) {
            dirs.add(audioDir);
        }

        File musicDir = context.getExternalFilesDir("music");
        if (musicDir != null) {
            dirs.add(new File(musicDir, "shared"));
        }

        dirs.add(new File(context.getFilesDir(), "music"));

        return dirs;
    }

    private static String normalizeToPath(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }

        Uri uri;
        try {
            uri = Uri.parse(filePath);
        } catch (Exception e) {
            uri = null;
        }

        String scheme = uri != null ? uri.getScheme() : null;
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme) || "content".equalsIgnoreCase(scheme)) {
            return null;
        }

        if ("file".equalsIgnoreCase(scheme) && uri != null) {
            String p = uri.getPath();
            if (p != null && !p.isEmpty()) {
                return p;
            }
        }

        return filePath;
    }

    private static boolean isUnderAppOwnedRoots(Context context, File target) {
        try {
            String targetPath = target.getCanonicalPath();
            for (File root : getAppOwnedRoots(context)) {
                if (root == null) {
                    continue;
                }
                String rootPath = root.getCanonicalPath();
                if (targetPath.equals(rootPath)) {
                    return true;
                }
                if (targetPath.startsWith(rootPath + File.separator)) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    private static List<File> getAppOwnedRoots(Context context) {
        List<File> roots = new ArrayList<>();

        roots.add(context.getFilesDir());
        roots.add(new File(context.getFilesDir(), "music"));

        File externalRoot = context.getExternalFilesDir(null);
        if (externalRoot != null) {
            roots.add(externalRoot);
        }

        File audioDir = context.getExternalFilesDir("audio");
        if (audioDir != null) {
            roots.add(audioDir);
        }

        File musicDir = context.getExternalFilesDir("music");
        if (musicDir != null) {
            roots.add(musicDir);
            roots.add(new File(musicDir, "shared"));
        }

        return roots;
    }

    private static boolean deleteFileOrDirectory(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteFileOrDirectory(child);
                }
            }
        }
        return file.delete();
    }

    private static void deleteIfExists(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }
}
