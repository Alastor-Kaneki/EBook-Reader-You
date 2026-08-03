package com.alastorkaneki.ebookreaderyou;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int PICK_FILES = 100;
    private static final int PICK_FOLDER = 101;
    private static final int EXPORT_LIBRARY = 102;
    private static final int IMPORT_LIBRARY = 103;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LibraryStore store;
    private ThemeManager theme;
    private LinearLayout listContainer;
    private TextView countView;
    private EditText searchView;
    private String activeFilter = "All";
    private List<BookItem> allBooks = new ArrayList<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = new LibraryStore(this);
        theme = new ThemeManager(this);
        ThemeManager.applyWindow(this, theme.background, false);
        buildUi();
        handleIncoming(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncoming(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLibrary();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(theme.background);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 36), Ui.dp(this, 18), Ui.dp(this, 16));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("EBook Reader You");
        title.setTextColor(theme.text);
        title.setTextSize(28);
        title.setTypeface(title.getTypeface(), 1);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView settings = Ui.button(this, "⚙", theme.text, theme.surfaceHigh, view -> showSettings());
        titleRow.addView(settings, new LinearLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 48)));
        root.addView(titleRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        countView = new TextView(this);
        countView.setTextColor(theme.textMuted);
        countView.setTextSize(14);
        countView.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 14));
        root.addView(countView);

        searchView = new EditText(this);
        searchView.setSingleLine(true);
        searchView.setHint("Search title or format");
        searchView.setHintTextColor(theme.textMuted);
        searchView.setTextColor(theme.text);
        searchView.setTextSize(16);
        searchView.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchView.setPadding(Ui.dp(this, 18), Ui.dp(this, 13), Ui.dp(this, 18), Ui.dp(this, 13));
        searchView.setBackground(Ui.rounded(theme.surfaceHigh, 24, this));
        searchView.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { renderBooks(); }
            @Override public void afterTextChanged(Editable value) {}
        });
        root.addView(searchView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        String[] filters = {"All", "Recent", "Favorites", "Books", "Comics"};
        for (String filter : filters) {
            TextView chip = createFilterChip(filter);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(Ui.dp(this, 8));
            filterRow.addView(chip, params);
        }
        filterScroll.addView(filterRow);
        root.addView(filterScroll);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView addFiles = Ui.button(this, "+ Add books", Color.BLACK, theme.accent, view -> pickFiles());
        TextView addFolder = Ui.button(this, "Add folder", theme.text, theme.accentContainer, view -> pickFolder());
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        actionParams.setMarginEnd(Ui.dp(this, 8));
        actions.addView(addFiles, actionParams);
        actions.addView(addFolder, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(actions);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 90));
        scroll.addView(listContainer, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }

    private TextView createFilterChip(String label) {
        boolean selected = activeFilter.equals(label);
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(14);
        chip.setTextColor(selected ? Color.BLACK : theme.text);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(Ui.dp(this, 16), Ui.dp(this, 9), Ui.dp(this, 16), Ui.dp(this, 9));
        chip.setBackground(Ui.rounded(selected ? theme.accent : theme.surfaceHigh, 20, this));
        chip.setOnClickListener(view -> {
            activeFilter = label;
            buildUi();
            refreshLibrary();
        });
        return chip;
    }

    private void refreshLibrary() {
        allBooks = store.load();
        renderBooks();
    }

    private void renderBooks() {
        if (listContainer == null) return;
        listContainer.removeAllViews();
        String query = searchView == null ? "" : searchView.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<BookItem> visible = new ArrayList<>();
        for (BookItem item : allBooks) {
            boolean filterMatch = switch (activeFilter) {
                case "Recent" -> item.lastOpened > 0;
                case "Favorites" -> item.favorite;
                case "Books" -> !item.isComic();
                case "Comics" -> item.isComic();
                default -> true;
            };
            boolean queryMatch = query.isEmpty() || item.name.toLowerCase(Locale.ROOT).contains(query) || item.type.toLowerCase(Locale.ROOT).contains(query);
            if (filterMatch && queryMatch) visible.add(item);
        }
        countView.setText(allBooks.size() + (allBooks.size() == 1 ? " title" : " titles") + "  •  " + visible.size() + " shown");
        if (visible.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(allBooks.isEmpty() ? "Your library is empty\n\nAdd individual books or choose a folder containing EPUB, PDF, TXT, CBZ, or CBR files." : "No titles match this view.");
            empty.setTextColor(theme.textMuted);
            empty.setTextSize(16);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(Ui.dp(this, 24), Ui.dp(this, 80), Ui.dp(this, 24), Ui.dp(this, 24));
            listContainer.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }
        for (BookItem item : visible) listContainer.addView(createBookCard(item));
    }

    private View createBookCard(BookItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        card.setBackground(Ui.rounded(theme.surface, 22, this));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(view -> openBook(item));
        card.setOnLongClickListener(view -> {
            showBookMenu(view, item);
            return true;
        });

        FrameLayout cover = new FrameLayout(this);
        cover.setBackground(Ui.rounded(theme.accentContainer, 14, this));
        TextView typeView = new TextView(this);
        typeView.setText(item.type);
        typeView.setTextColor(theme.accent);
        typeView.setTextSize(14);
        typeView.setTypeface(typeView.getTypeface(), 1);
        typeView.setGravity(Gravity.CENTER);
        cover.addView(typeView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        card.addView(cover, new LinearLayout.LayoutParams(Ui.dp(this, 68), Ui.dp(this, 94)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 8), 0);
        TextView nameView = new TextView(this);
        nameView.setText(item.name);
        nameView.setTextColor(theme.text);
        nameView.setTextSize(17);
        nameView.setTypeface(nameView.getTypeface(), 1);
        nameView.setMaxLines(2);
        details.addView(nameView);
        TextView meta = new TextView(this);
        String size = Ui.size(item.size);
        meta.setText(item.type + (size.isEmpty() ? "" : "  •  " + size));
        meta.setTextColor(theme.textMuted);
        meta.setTextSize(13);
        meta.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 10));
        details.addView(meta);
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgress(Math.round(item.progress * 1000));
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(theme.accent));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(theme.surfaceHigh));
        details.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 4)));
        TextView status = new TextView(this);
        status.setText(item.progress > 0 ? Math.round(item.progress * 100) + "% read" : "Not started");
        status.setTextColor(theme.textMuted);
        status.setTextSize(12);
        status.setPadding(0, Ui.dp(this, 5), 0, 0);
        details.addView(status);
        card.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView favorite = new TextView(this);
        favorite.setText(item.favorite ? "★" : "☆");
        favorite.setTextColor(item.favorite ? theme.accent : theme.textMuted);
        favorite.setTextSize(25);
        favorite.setGravity(Gravity.CENTER);
        favorite.setOnClickListener(view -> {
            store.toggleFavorite(item.uri);
            refreshLibrary();
        });
        card.addView(favorite, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 52)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, Ui.dp(this, 10));
        card.setLayoutParams(params);
        return card;
    }

    private void showBookMenu(View anchor, BookItem item) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Open");
        menu.getMenu().add(item.favorite ? "Remove favorite" : "Add favorite");
        menu.getMenu().add("Share");
        menu.getMenu().add("Remove from library");
        menu.setOnMenuItemClickListener(option -> {
            String title = option.getTitle().toString();
            if (title.equals("Open")) openBook(item);
            else if (title.contains("favorite")) {
                store.toggleFavorite(item.uri);
                refreshLibrary();
            } else if (title.equals("Share")) shareBook(item);
            else if (title.equals("Remove from library")) {
                store.remove(item.uri);
                refreshLibrary();
            }
            return true;
        });
        menu.show();
    }

    private void openBook(BookItem item) {
        store.markOpened(item.uri);
        Intent intent = new Intent(this, ReaderActivity.class);
        intent.putExtra("uri", item.uri);
        intent.putExtra("name", item.name);
        intent.putExtra("type", item.type);
        startActivity(intent);
    }

    private void shareBook(BookItem item) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Share book"));
    }

    private void pickFiles() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/epub+zip", "application/pdf", "text/plain", "application/zip", "application/x-rar-compressed", "application/vnd.comicbook+zip", "application/vnd.comicbook-rar"});
        startActivityForResult(intent, PICK_FILES);
    }

    private void pickFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, PICK_FOLDER);
    }

    private void handleIncoming(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction()) || intent.getData() == null) return;
        Uri incoming = intent.getData();
        tryPersist(incoming, intent.getFlags());
        BookItem item = itemFromUri(incoming);
        if (item != null) {
            store.addOrUpdate(item);
            openBook(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == PICK_FILES) {
            List<Uri> uris = new ArrayList<>();
            if (data.getData() != null) uris.add(data.getData());
            ClipData clip = data.getClipData();
            if (clip != null) for (int i = 0; i < clip.getItemCount(); i++) uris.add(clip.getItemAt(i).getUri());
            int added = 0;
            for (Uri selected : uris) {
                tryPersist(selected, data.getFlags());
                BookItem item = itemFromUri(selected);
                if (item != null) {
                    store.addOrUpdate(item);
                    added++;
                }
            }
            Toast.makeText(this, "Added " + added + (added == 1 ? " title" : " titles"), Toast.LENGTH_SHORT).show();
            refreshLibrary();
        } else if (requestCode == PICK_FOLDER && data.getData() != null) {
            Uri tree = data.getData();
            tryPersist(tree, data.getFlags());
            importFolder(tree);
        } else if (requestCode == EXPORT_LIBRARY && data.getData() != null) {
            exportLibrary(data.getData());
        } else if (requestCode == IMPORT_LIBRARY && data.getData() != null) {
            importLibrary(data.getData());
        }
    }

    private BookItem itemFromUri(Uri selected) {
        String displayName = null;
        long size = 0;
        try (Cursor cursor = getContentResolver().query(selected, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameIndex >= 0) displayName = cursor.getString(nameIndex);
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex);
            }
        } catch (Exception ignored) {
        }
        if (displayName == null) displayName = selected.getLastPathSegment();
        if (displayName == null || !LibraryStore.isSupported(displayName)) {
            Toast.makeText(this, "Unsupported file: " + (displayName == null ? "Unknown" : displayName), Toast.LENGTH_SHORT).show();
            return null;
        }
        return new BookItem(selected.toString(), displayName, LibraryStore.typeFromName(displayName), size);
    }

    private void tryPersist(Uri selected, int flags) {
        try {
            int takeFlags = flags & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(selected, takeFlags | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
    }

    private void importFolder(Uri treeUri) {
        ProgressBar progress = new ProgressBar(this);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Scanning folder")
                .setMessage("Finding supported books and comics…")
                .setView(progress)
                .setCancelable(false)
                .create();
        dialog.show();
        executor.execute(() -> {
            List<BookItem> found = new ArrayList<>();
            try {
                String rootId = DocumentsContract.getTreeDocumentId(treeUri);
                scanDocument(treeUri, rootId, found, 0);
                for (BookItem item : found) store.addOrUpdate(item);
                runOnUiThread(() -> {
                    dialog.dismiss();
                    Toast.makeText(this, "Added " + found.size() + (found.size() == 1 ? " title" : " titles"), Toast.LENGTH_LONG).show();
                    refreshLibrary();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    dialog.dismiss();
                    Toast.makeText(this, "Folder scan failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void scanDocument(Uri treeUri, String documentId, List<BookItem> found, int depth) {
        if (depth > 32 || found.size() >= 5000) return;
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId);
        String[] projection = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE};
        try (Cursor cursor = getContentResolver().query(children, projection, null, null, null)) {
            if (cursor == null) return;
            while (cursor.moveToNext()) {
                String childId = cursor.getString(0);
                String displayName = cursor.getString(1);
                String mime = cursor.getString(2);
                long size = cursor.isNull(3) ? 0 : cursor.getLong(3);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    scanDocument(treeUri, childId, found, depth + 1);
                } else if (LibraryStore.isSupported(displayName)) {
                    Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId);
                    found.add(new BookItem(documentUri.toString(), displayName, LibraryStore.typeFromName(displayName), size));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void showSettings() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        CheckBox amoled = new CheckBox(this);
        amoled.setText("True-black AMOLED surfaces");
        amoled.setChecked(theme.amoled);
        panel.addView(amoled);
        CheckBox dynamic = new CheckBox(this);
        dynamic.setText("Material You dynamic colors");
        dynamic.setChecked(theme.dynamic);
        panel.addView(dynamic);
        CheckBox immersive = new CheckBox(this);
        immersive.setText("Immersive reader mode");
        immersive.setChecked(theme.isImmersive());
        panel.addView(immersive);
        TextView export = Ui.button(this, "Export library backup", theme.text, theme.surfaceHigh, view -> createLibraryBackup());
        LinearLayout.LayoutParams action = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        action.setMargins(0, Ui.dp(this, 14), 0, Ui.dp(this, 8));
        panel.addView(export, action);
        TextView restore = Ui.button(this, "Restore library backup", theme.text, theme.surfaceHigh, view -> chooseLibraryBackup());
        panel.addView(restore);
        new AlertDialog.Builder(this)
                .setTitle("Appearance & library")
                .setView(panel)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", (dialog, which) -> {
                    theme.setAmoled(amoled.isChecked());
                    theme.setDynamic(dynamic.isChecked());
                    theme.setImmersive(immersive.isChecked());
                    recreate();
                })
                .show();
    }

    private void createLibraryBackup() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "EBook-Reader-You-library.json");
        startActivityForResult(intent, EXPORT_LIBRARY);
    }

    private void chooseLibraryBackup() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, IMPORT_LIBRARY);
    }

    private void exportLibrary(Uri selected) {
        try (OutputStream output = getContentResolver().openOutputStream(selected)) {
            if (output == null) throw new IllegalStateException("Unable to create backup");
            output.write(store.exportJson().getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "Library backup saved", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(this, "Backup failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void importLibrary(Uri selected) {
        try (InputStream input = getContentResolver().openInputStream(selected); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IllegalStateException("Unable to open backup");
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            if (!store.importJson(output.toString(StandardCharsets.UTF_8))) throw new IllegalArgumentException("Invalid backup file");
            Toast.makeText(this, "Library restored", Toast.LENGTH_SHORT).show();
            refreshLibrary();
        } catch (Exception error) {
            Toast.makeText(this, "Restore failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
