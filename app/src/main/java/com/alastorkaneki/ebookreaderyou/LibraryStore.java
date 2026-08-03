package com.alastorkaneki.ebookreaderyou;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class LibraryStore {
    private static final String PREFS = "library_store";
    private static final String BOOKS = "books";
    private final SharedPreferences preferences;

    public LibraryStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized List<BookItem> load() {
        List<BookItem> items = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(BOOKS, "[]"));
            for (int i = 0; i < array.length(); i++) {
                items.add(BookItem.fromJson(array.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
        items.sort(Comparator.comparingLong((BookItem item) -> item.lastOpened > 0 ? item.lastOpened : item.addedAt).reversed());
        return items;
    }

    public synchronized void save(List<BookItem> items) {
        JSONArray array = new JSONArray();
        for (BookItem item : items) {
            try {
                array.put(item.toJson());
            } catch (Exception ignored) {
            }
        }
        preferences.edit().putString(BOOKS, array.toString()).apply();
    }

    public synchronized void addOrUpdate(BookItem incoming) {
        List<BookItem> items = load();
        boolean found = false;
        for (int i = 0; i < items.size(); i++) {
            BookItem current = items.get(i);
            if (current.uri.equals(incoming.uri)) {
                incoming.addedAt = current.addedAt;
                incoming.lastOpened = current.lastOpened;
                incoming.progress = current.progress;
                incoming.favorite = current.favorite;
                items.set(i, incoming);
                found = true;
                break;
            }
        }
        if (!found) items.add(incoming);
        save(items);
    }

    public synchronized void remove(String uri) {
        List<BookItem> items = load();
        items.removeIf(item -> item.uri.equals(uri));
        save(items);
    }

    public synchronized void toggleFavorite(String uri) {
        List<BookItem> items = load();
        for (BookItem item : items) {
            if (item.uri.equals(uri)) item.favorite = !item.favorite;
        }
        save(items);
    }

    public synchronized void markOpened(String uri) {
        List<BookItem> items = load();
        for (BookItem item : items) {
            if (item.uri.equals(uri)) item.lastOpened = System.currentTimeMillis();
        }
        save(items);
    }

    public synchronized void setProgress(String uri, float progress) {
        List<BookItem> items = load();
        for (BookItem item : items) {
            if (item.uri.equals(uri)) item.progress = Math.max(0f, Math.min(1f, progress));
        }
        save(items);
    }

    public synchronized BookItem find(String uri) {
        for (BookItem item : load()) {
            if (item.uri.equals(uri)) return item;
        }
        return null;
    }

    public String exportJson() {
        return preferences.getString(BOOKS, "[]");
    }

    public boolean importJson(String json) {
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) BookItem.fromJson(array.getJSONObject(i));
            preferences.edit().putString(BOOKS, array.toString()).apply();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static String typeFromName(String name) {
        String lower = name == null ? "" : name.toLowerCase();
        if (lower.endsWith(".epub")) return "EPUB";
        if (lower.endsWith(".pdf")) return "PDF";
        if (lower.endsWith(".cbz") || lower.endsWith(".zip")) return "CBZ";
        if (lower.endsWith(".cbr") || lower.endsWith(".rar")) return "CBR";
        return "TXT";
    }

    public static boolean isSupported(String name) {
        String lower = name == null ? "" : name.toLowerCase();
        return lower.endsWith(".epub") || lower.endsWith(".pdf") || lower.endsWith(".txt") || lower.endsWith(".cbz") || lower.endsWith(".cbr") || lower.endsWith(".zip") || lower.endsWith(".rar");
    }

    public static String stableKey(Uri uri) {
        return Integer.toHexString(uri.toString().hashCode());
    }
}
