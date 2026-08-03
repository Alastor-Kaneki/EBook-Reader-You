package com.alastorkaneki.ebookreaderyou;

import org.json.JSONException;
import org.json.JSONObject;

public final class BookItem {
    public String uri;
    public String name;
    public String type;
    public long size;
    public long addedAt;
    public long lastOpened;
    public float progress;
    public boolean favorite;

    public BookItem(String uri, String name, String type, long size) {
        this.uri = uri;
        this.name = name;
        this.type = type;
        this.size = size;
        this.addedAt = System.currentTimeMillis();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("uri", uri);
        object.put("name", name);
        object.put("type", type);
        object.put("size", size);
        object.put("addedAt", addedAt);
        object.put("lastOpened", lastOpened);
        object.put("progress", progress);
        object.put("favorite", favorite);
        return object;
    }

    public static BookItem fromJson(JSONObject object) throws JSONException {
        BookItem item = new BookItem(
                object.getString("uri"),
                object.optString("name", "Untitled"),
                object.optString("type", "TXT"),
                object.optLong("size", 0)
        );
        item.addedAt = object.optLong("addedAt", System.currentTimeMillis());
        item.lastOpened = object.optLong("lastOpened", 0);
        item.progress = (float) object.optDouble("progress", 0);
        item.favorite = object.optBoolean("favorite", false);
        return item;
    }

    public boolean isComic() {
        return "CBZ".equals(type) || "CBR".equals(type);
    }
}
