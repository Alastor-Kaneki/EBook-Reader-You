package com.alastorkaneki.ebookreaderyou;

import android.content.Context;
import android.net.Uri;

import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ArchiveUtils {
    private static final int BUFFER = 64 * 1024;
    private static final int MAX_ENTRIES = 10000;
    private static final long MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024;

    private ArchiveUtils() {
    }

    public static File prepareSource(Context context, Uri uri, String key, String extension) throws Exception {
        File root = new File(context.getCacheDir(), "reader_sources");
        if (!root.exists() && !root.mkdirs()) throw new IllegalStateException("Unable to create source cache");
        File target = new File(root, key + extension);
        if (target.exists() && target.length() > 0) return target;
        try (InputStream input = context.getContentResolver().openInputStream(uri); OutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
            if (input == null) throw new IllegalStateException("Unable to open file");
            copy(input, output, MAX_TOTAL_BYTES);
        }
        return target;
    }

    public static List<File> extractComic(Context context, Uri uri, String type) throws Exception {
        String key = LibraryStore.stableKey(uri);
        File destination = new File(context.getCacheDir(), "comics/" + key);
        List<File> existing = imageFiles(destination);
        if (!existing.isEmpty()) return existing;
        clear(destination);
        if (!destination.mkdirs() && !destination.isDirectory()) throw new IllegalStateException("Unable to create comic cache");
        if ("CBR".equals(type)) extractRar(context, uri, destination, key);
        else extractZip(context, uri, destination);
        List<File> files = imageFiles(destination);
        if (files.isEmpty()) throw new IllegalArgumentException("No readable images were found in this archive");
        return files;
    }

    public static File extractEpub(Context context, Uri uri) throws Exception {
        String key = LibraryStore.stableKey(uri);
        File destination = new File(context.getCacheDir(), "epub/" + key);
        File marker = new File(destination, ".ready");
        if (marker.exists()) return destination;
        clear(destination);
        if (!destination.mkdirs() && !destination.isDirectory()) throw new IllegalStateException("Unable to create EPUB cache");
        extractZip(context, uri, destination);
        if (!marker.createNewFile() && !marker.exists()) throw new IllegalStateException("Unable to finalize EPUB cache");
        return destination;
    }

    private static void extractZip(Context context, Uri uri, File destination) throws Exception {
        long total = 0;
        int count = 0;
        try (InputStream raw = context.getContentResolver().openInputStream(uri); ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
            if (raw == null) throw new IllegalStateException("Unable to open archive");
            ZipEntry entry;
            byte[] buffer = new byte[BUFFER];
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > MAX_ENTRIES) throw new IllegalArgumentException("Archive contains too many files");
                File target = safeTarget(destination, entry.getName());
                if (entry.isDirectory()) {
                    if (!target.exists() && !target.mkdirs()) throw new IllegalStateException("Unable to create archive folder");
                    continue;
                }
                File parent = target.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IllegalStateException("Unable to create archive folder");
                try (OutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_TOTAL_BYTES) throw new IllegalArgumentException("Archive expands beyond the safety limit");
                        output.write(buffer, 0, read);
                    }
                }
            }
        }
    }

    private static void extractRar(Context context, Uri uri, File destination, String key) throws Exception {
        File source = prepareSource(context, uri, key, ".cbr");
        long total = 0;
        int count = 0;
        try (Archive archive = new Archive(source)) {
            for (FileHeader header : archive.getFileHeaders()) {
                if (++count > MAX_ENTRIES) throw new IllegalArgumentException("Archive contains too many files");
                String name = header.getFileName();
                if (name == null || name.isBlank()) continue;
                File target = safeTarget(destination, name.replace('\\', '/'));
                if (header.isDirectory()) {
                    if (!target.exists() && !target.mkdirs()) throw new IllegalStateException("Unable to create archive folder");
                    continue;
                }
                long unpacked = header.getFullUnpackSize();
                total += Math.max(0, unpacked);
                if (total > MAX_TOTAL_BYTES) throw new IllegalArgumentException("Archive expands beyond the safety limit");
                File parent = target.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IllegalStateException("Unable to create archive folder");
                try (OutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
                    archive.extractFile(header, output);
                }
            }
        }
    }

    private static File safeTarget(File destination, String name) throws Exception {
        File target = new File(destination, name);
        String rootPath = destination.getCanonicalPath() + File.separator;
        String targetPath = target.getCanonicalPath();
        if (!targetPath.startsWith(rootPath)) throw new SecurityException("Unsafe archive path");
        return target;
    }

    private static List<File> imageFiles(File root) {
        List<File> files = new ArrayList<>();
        collectImages(root, files);
        files.sort(Comparator.comparing(file -> naturalKey(file.getName())));
        return files;
    }

    private static void collectImages(File file, List<File> files) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) collectImages(child, files);
            return;
        }
        String lower = file.getName().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".bmp") || lower.endsWith(".avif")) files.add(file);
    }

    private static String naturalKey(String name) {
        StringBuilder builder = new StringBuilder();
        String lower = name.toLowerCase(Locale.ROOT);
        int i = 0;
        while (i < lower.length()) {
            char c = lower.charAt(i);
            if (Character.isDigit(c)) {
                int j = i;
                while (j < lower.length() && Character.isDigit(lower.charAt(j))) j++;
                String digits = lower.substring(i, j);
                builder.append(String.format(Locale.ROOT, "%020d", Long.parseLong(digits.length() > 18 ? digits.substring(0, 18) : digits)));
                i = j;
            } else {
                builder.append(c);
                i++;
            }
        }
        return builder.toString();
    }

    private static long copy(InputStream input, OutputStream output, long limit) throws Exception {
        byte[] buffer = new byte[BUFFER];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > limit) throw new IllegalArgumentException("File exceeds the safety limit");
            output.write(buffer, 0, read);
        }
        return total;
    }

    public static void clear(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) clear(child);
        }
        file.delete();
    }
}
