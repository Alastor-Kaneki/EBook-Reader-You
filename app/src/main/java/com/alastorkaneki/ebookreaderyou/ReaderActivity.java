package com.alastorkaneki.ebookreaderyou;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.speech.tts.TextToSpeech;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import android.graphics.pdf.PdfRenderer;

import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ReaderActivity extends Activity implements TextToSpeech.OnInitListener {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ThemeManager theme;
    private LibraryStore store;
    private SharedPreferences settings;
    private Uri uri;
    private String uriString;
    private String name;
    private String type;
    private String key;
    private LinearLayout toolbar;
    private LinearLayout bottomBar;
    private FrameLayout content;
    private TextView pageView;
    private TextView bookmarkView;
    private TextView previousView;
    private TextView nextView;
    private ProgressBar loading;
    private int currentIndex;
    private int total = 1;
    private boolean controlsVisible = true;
    private boolean rtl;
    private float brightness;
    private int fontSize;
    private boolean warmLight;
    private ParcelFileDescriptor pdfDescriptor;
    private PdfRenderer pdfRenderer;
    private List<File> comicPages = new ArrayList<>();
    private EpubBook epub;
    private WebView webView;
    private ScrollView textScroll;
    private TextView textView;
    private String plainText;
    private TextToSpeech tts;
    private boolean ttsReady;
    private Bitmap displayedBitmap;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        uriString = getIntent().getStringExtra("uri");
        name = getIntent().getStringExtra("name");
        type = getIntent().getStringExtra("type");
        if (uriString == null || type == null) {
            finish();
            return;
        }
        uri = Uri.parse(uriString);
        key = LibraryStore.stableKey(uri);
        theme = new ThemeManager(this);
        store = new LibraryStore(this);
        settings = getSharedPreferences("reader_settings", MODE_PRIVATE);
        rtl = settings.getBoolean("rtl", false);
        brightness = settings.getFloat("brightness", -1f);
        fontSize = settings.getInt("font_size", 19);
        warmLight = settings.getBoolean("warm_light", false);
        applyBrightness();
        ThemeManager.applyWindow(this, theme.background, theme.isImmersive());
        if (settings.getBoolean("keep_awake", false)) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildShell();
        tts = new TextToSpeech(this, this);
        loadBook();
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(theme.background);

        toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        toolbar.setBackgroundColor(theme.surface);
        TextView back = Ui.button(this, "‹", theme.text, theme.surfaceHigh, view -> finish());
        back.setTextSize(28);
        toolbar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 48)));
        TextView titleView = new TextView(this);
        titleView.setText(name == null ? "Reader" : name);
        titleView.setTextColor(theme.text);
        titleView.setTextSize(16);
        titleView.setTypeface(titleView.getTypeface(), 1);
        titleView.setSingleLine(true);
        titleView.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 8), 0);
        toolbar.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bookmarkView = Ui.button(this, "☆", theme.text, theme.surfaceHigh, view -> toggleBookmark());
        bookmarkView.setTextSize(24);
        toolbar.addView(bookmarkView, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 48)));
        TextView settingsButton = Ui.button(this, "⋮", theme.text, theme.surfaceHigh, view -> showReaderSettings());
        settingsButton.setTextSize(25);
        toolbar.addView(settingsButton, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 48)));
        root.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content = new FrameLayout(this);
        content.setBackgroundColor(readerBackground());
        content.setOnClickListener(view -> toggleControls());
        loading = new ProgressBar(this);
        FrameLayout.LayoutParams loadParams = new FrameLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 54), Gravity.CENTER);
        content.addView(loading, loadParams);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        bottomBar = new LinearLayout(this);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 10));
        bottomBar.setBackgroundColor(theme.surface);
        previousView = Ui.button(this, "Previous", theme.text, theme.surfaceHigh, view -> previous());
        nextView = Ui.button(this, "Next", Color.BLACK, theme.accent, view -> next());
        pageView = new TextView(this);
        pageView.setTextColor(theme.textMuted);
        pageView.setTextSize(14);
        pageView.setGravity(Gravity.CENTER);
        bottomBar.addView(previousView, new LinearLayout.LayoutParams(Ui.dp(this, 104), ViewGroup.LayoutParams.WRAP_CONTENT));
        bottomBar.addView(pageView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bottomBar.addView(nextView, new LinearLayout.LayoutParams(Ui.dp(this, 104), ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(bottomBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
        updateNavigation();
    }

    private int readerBackground() {
        return warmLight ? Color.rgb(34, 27, 18) : theme.background;
    }

    private void loadBook() {
        setLoading(true);
        executor.execute(() -> {
            try {
                switch (type) {
                    case "PDF" -> preparePdf();
                    case "CBZ", "CBR" -> prepareComic();
                    case "EPUB" -> prepareEpub();
                    default -> prepareText();
                }
            } catch (Exception error) {
                runOnUiThread(() -> showError(error));
            }
        });
    }

    private void preparePdf() throws Exception {
        pdfDescriptor = getContentResolver().openFileDescriptor(uri, "r");
        if (pdfDescriptor == null) throw new IllegalStateException("Unable to open PDF");
        pdfRenderer = new PdfRenderer(pdfDescriptor);
        total = pdfRenderer.getPageCount();
        if (total <= 0) throw new IllegalArgumentException("This PDF contains no pages");
        currentIndex = Math.min(total - 1, settings.getInt("position_" + key, 0));
        runOnUiThread(this::showPdfPage);
    }

    private void prepareComic() throws Exception {
        comicPages = ArchiveUtils.extractComic(this, uri, type);
        total = comicPages.size();
        currentIndex = Math.min(total - 1, settings.getInt("position_" + key, 0));
        runOnUiThread(this::showComicPage);
    }

    private void prepareEpub() throws Exception {
        File root = ArchiveUtils.extractEpub(this, uri);
        epub = EpubBook.parse(root);
        total = epub.chapters.size();
        currentIndex = Math.min(total - 1, settings.getInt("position_" + key, 0));
        runOnUiThread(this::showEpubChapter);
    }

    private void prepareText() throws Exception {
        try (InputStream input = getContentResolver().openInputStream(uri); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IllegalStateException("Unable to open text file");
            byte[] buffer = new byte[32 * 1024];
            int read;
            int totalRead = 0;
            while ((read = input.read(buffer)) != -1) {
                totalRead += read;
                if (totalRead > 64 * 1024 * 1024) throw new IllegalArgumentException("Text file is larger than the 64 MB reading limit");
                output.write(buffer, 0, read);
            }
            plainText = decodeText(output.toByteArray());
        }
        runOnUiThread(this::showText);
    }

    private String decodeText(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xef && (bytes[1] & 0xff) == 0xbb && (bytes[2] & 0xff) == 0xbf) return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        if (bytes.length >= 2 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xfe) return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        if (bytes.length >= 2 && (bytes[0] & 0xff) == 0xfe && (bytes[1] & 0xff) == 0xff) return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        long replacements = utf8.chars().filter(value -> value == 0xfffd).count();
        if (replacements > Math.max(3, bytes.length / 1000)) return new String(bytes, Charset.forName("windows-1252"));
        return utf8;
    }

    private void showPdfPage() {
        setLoading(true);
        updateNavigation();
        executor.execute(() -> {
            Bitmap bitmap = null;
            try (PdfRenderer.Page page = pdfRenderer.openPage(currentIndex)) {
                int screenWidth = Math.max(720, getResources().getDisplayMetrics().widthPixels);
                float pageScale = Math.min(2.5f, screenWidth / (float) page.getWidth());
                int width = Math.max(1, Math.round(page.getWidth() * pageScale));
                int height = Math.max(1, Math.round(page.getHeight() * pageScale));
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            } catch (Exception error) {
                Bitmap failed = bitmap;
                runOnUiThread(() -> {
                    if (failed != null) failed.recycle();
                    showError(error);
                });
                return;
            }
            Bitmap ready = bitmap;
            runOnUiThread(() -> displayImage(ready, true));
        });
    }

    private void showComicPage() {
        setLoading(true);
        updateNavigation();
        File page = comicPages.get(currentIndex);
        executor.execute(() -> {
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(page.getAbsolutePath(), bounds);
                int maxDimension = Math.max(getResources().getDisplayMetrics().widthPixels * 3, 2160);
                int sample = 1;
                while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension * 2) sample *= 2;
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sample;
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                Bitmap bitmap = BitmapFactory.decodeFile(page.getAbsolutePath(), options);
                if (bitmap == null) throw new IllegalArgumentException("Unable to decode comic page");
                runOnUiThread(() -> displayImage(bitmap, false));
            } catch (Exception error) {
                runOnUiThread(() -> showError(error));
            }
        });
    }

    private void displayImage(Bitmap bitmap, boolean pdf) {
        if (isFinishing() || isDestroyed()) {
            bitmap.recycle();
            return;
        }
        if (displayedBitmap != null && displayedBitmap != bitmap && !displayedBitmap.isRecycled()) displayedBitmap.recycle();
        displayedBitmap = bitmap;
        content.removeAllViews();
        ZoomImageView image = new ZoomImageView(this);
        image.setBackgroundColor(pdf ? Color.rgb(35, 35, 35) : readerBackground());
        image.setImageBitmap(bitmap);
        image.setOnSingleTapListener(this::toggleControls);
        content.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setLoading(false);
        updateNavigation();
        savePosition();
    }

    private void showEpubChapter() {
        content.removeAllViews();
        webView = new WebView(this);
        webView.setBackgroundColor(readerBackground());
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setDefaultFontSize(fontSize);
        webSettings.setTextZoom(Math.round(fontSize / 19f * 100f));
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                String background = warmLight ? "#221B12" : String.format(Locale.ROOT, "#%06X", theme.background & 0xffffff);
                String foreground = String.format(Locale.ROOT, "#%06X", theme.text & 0xffffff);
                String accent = String.format(Locale.ROOT, "#%06X", theme.accent & 0xffffff);
                String script = "(function(){var s=document.createElement('style');s.innerHTML='html,body{background:" + background + "!important;color:" + foreground + "!important;font-size:" + fontSize + "px!important;line-height:1.65!important;padding:0 4vw!important;max-width:100%!important;}a{color:" + accent + "!important;}img,svg{max-width:100%!important;height:auto!important;}';document.head.appendChild(s);})()";
                view.evaluateJavascript(script, null);
                setLoading(false);
            }
        });
        content.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        webView.loadUrl(Uri.fromFile(epub.chapters.get(currentIndex)).toString());
        updateNavigation();
        savePosition();
    }

    private void showText() {
        content.removeAllViews();
        textScroll = new ScrollView(this);
        textScroll.setFillViewport(true);
        textScroll.setBackgroundColor(readerBackground());
        textView = new TextView(this);
        textView.setText(plainText);
        textView.setTextColor(theme.text);
        textView.setTextSize(fontSize);
        textView.setLineSpacing(Ui.dp(this, 7), 1.08f);
        textView.setTextIsSelectable(true);
        int horizontal = Ui.dp(this, 24);
        textView.setPadding(horizontal, Ui.dp(this, 24), horizontal, Ui.dp(this, 80));
        textScroll.addView(textView, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        textScroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> {
            updateTextProgress();
            settings.edit().putInt("text_scroll_" + key, y).apply();
        });
        textScroll.setOnClickListener(view -> toggleControls());
        content.addView(textScroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        int savedScroll = settings.getInt("text_scroll_" + key, 0);
        textScroll.post(() -> textScroll.scrollTo(0, savedScroll));
        total = 1;
        currentIndex = 0;
        setLoading(false);
        updateNavigation();
    }

    private void previous() {
        if ("TXT".equals(type)) {
            if (textScroll != null) textScroll.smoothScrollBy(0, -Math.max(200, textScroll.getHeight() - Ui.dp(this, 60)));
            return;
        }
        if (currentIndex <= 0) return;
        currentIndex--;
        showCurrent();
    }

    private void next() {
        if ("TXT".equals(type)) {
            if (textScroll != null) textScroll.smoothScrollBy(0, Math.max(200, textScroll.getHeight() - Ui.dp(this, 60)));
            return;
        }
        if (currentIndex >= total - 1) return;
        currentIndex++;
        showCurrent();
    }

    private void showCurrent() {
        if ("PDF".equals(type)) showPdfPage();
        else if ("CBZ".equals(type) || "CBR".equals(type)) showComicPage();
        else if ("EPUB".equals(type)) showEpubChapter();
    }

    private void updateNavigation() {
        if (pageView == null) return;
        if ("TXT".equals(type)) {
            pageView.setText(Math.round(textProgress() * 100) + "%");
            previousView.setAlpha(1f);
            nextView.setAlpha(1f);
        } else {
            String label = (currentIndex + 1) + " / " + Math.max(1, total);
            if ("EPUB".equals(type) && epub != null && currentIndex < epub.titles.size()) label += "  •  " + epub.titles.get(currentIndex);
            pageView.setText(label);
            previousView.setAlpha(currentIndex > 0 ? 1f : 0.35f);
            nextView.setAlpha(currentIndex < total - 1 ? 1f : 0.35f);
        }
        bookmarkView.setText(isBookmarked() ? "★" : "☆");
        bookmarkView.setTextColor(isBookmarked() ? theme.accent : theme.text);
    }

    private void setLoading(boolean visible) {
        if (visible) {
            if (loading.getParent() == null) content.addView(loading, new FrameLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 54), Gravity.CENTER));
            loading.setVisibility(View.VISIBLE);
        } else {
            loading.setVisibility(View.GONE);
        }
    }

    private void toggleControls() {
        controlsVisible = !controlsVisible;
        toolbar.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);
        bottomBar.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);
    }

    private void showError(Exception error) {
        setLoading(false);
        content.removeAllViews();
        TextView message = new TextView(this);
        message.setText("Unable to open this " + type + " file\n\n" + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
        message.setTextColor(theme.text);
        message.setTextSize(17);
        message.setGravity(Gravity.CENTER);
        message.setPadding(Ui.dp(this, 30), Ui.dp(this, 30), Ui.dp(this, 30), Ui.dp(this, 30));
        content.addView(message, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Toast.makeText(this, "Reader error", Toast.LENGTH_LONG).show();
    }

    private void showReaderSettings() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.dp(this, 22), Ui.dp(this, 8), Ui.dp(this, 22), 0);
        panel.addView(settingLabel("Brightness"));
        SeekBar brightnessBar = new SeekBar(this);
        brightnessBar.setMax(100);
        brightnessBar.setProgress(brightness < 0 ? 50 : Math.round(brightness * 100));
        panel.addView(brightnessBar);
        panel.addView(settingLabel("Text size"));
        SeekBar fontBar = new SeekBar(this);
        fontBar.setMax(22);
        fontBar.setProgress(Math.max(0, fontSize - 12));
        panel.addView(fontBar);
        CheckBox warm = new CheckBox(this);
        warm.setText("Warm reading background");
        warm.setChecked(warmLight);
        panel.addView(warm);
        CheckBox manga = new CheckBox(this);
        manga.setText("Right-to-left manga navigation");
        manga.setChecked(rtl);
        manga.setEnabled("CBZ".equals(type) || "CBR".equals(type));
        panel.addView(manga);
        CheckBox awake = new CheckBox(this);
        awake.setText("Keep screen awake");
        awake.setChecked(settings.getBoolean("keep_awake", false));
        panel.addView(awake);

        RadioGroup orientation = new RadioGroup(this);
        orientation.setOrientation(LinearLayout.HORIZONTAL);
        RadioButton auto = radio("Auto");
        RadioButton portrait = radio("Portrait");
        RadioButton landscape = radio("Landscape");
        orientation.addView(auto);
        orientation.addView(portrait);
        orientation.addView(landscape);
        int savedOrientation = settings.getInt("orientation", ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        if (savedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) portrait.setChecked(true);
        else if (savedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) landscape.setChecked(true);
        else auto.setChecked(true);
        panel.addView(settingLabel("Orientation"));
        panel.addView(orientation);

        if ("TXT".equals(type) || "EPUB".equals(type)) {
            TextView speak = Ui.button(this, "Read aloud", theme.text, theme.surfaceHigh, view -> speakCurrent());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, Ui.dp(this, 14), 0, 0);
            panel.addView(speak, params);
        }
        if ("EPUB".equals(type) && epub != null) {
            TextView chapters = Ui.button(this, "Table of contents", theme.text, theme.surfaceHigh, view -> showChapterPicker());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, Ui.dp(this, 8), 0, 0);
            panel.addView(chapters, params);
        }

        new AlertDialog.Builder(this)
                .setTitle("Reader settings")
                .setView(panel)
                .setNeutralButton("Share", (dialog, which) -> shareCurrent())
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", (dialog, which) -> {
                    brightness = brightnessBar.getProgress() / 100f;
                    fontSize = fontBar.getProgress() + 12;
                    warmLight = warm.isChecked();
                    rtl = manga.isChecked();
                    boolean keepAwake = awake.isChecked();
                    int chosenOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
                    int checked = orientation.getCheckedRadioButtonId();
                    if (checked == portrait.getId()) chosenOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    if (checked == landscape.getId()) chosenOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    settings.edit().putFloat("brightness", brightness).putInt("font_size", fontSize).putBoolean("warm_light", warmLight).putBoolean("rtl", rtl).putBoolean("keep_awake", keepAwake).putInt("orientation", chosenOrientation).apply();
                    setRequestedOrientation(chosenOrientation);
                    if (keepAwake) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    applyBrightness();
                    content.setBackgroundColor(readerBackground());
                    if ("TXT".equals(type)) showText();
                    else if ("EPUB".equals(type)) showEpubChapter();
                    else showCurrent();
                })
                .show();
    }

    private TextView settingLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(14);
        label.setTextColor(theme.textMuted);
        label.setPadding(0, Ui.dp(this, 12), 0, 0);
        return label;
    }

    private RadioButton radio(String text) {
        RadioButton button = new RadioButton(this);
        button.setText(text);
        button.setId(View.generateViewId());
        return button;
    }

    private void showChapterPicker() {
        String[] titles = epub.titles.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("Table of contents").setSingleChoiceItems(titles, currentIndex, (dialog, which) -> {
            currentIndex = which;
            dialog.dismiss();
            showEpubChapter();
        }).show();
    }

    private void shareCurrent() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Share book"));
    }

    private void applyBrightness() {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.screenBrightness = brightness < 0 ? WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE : Math.max(0.02f, brightness);
        getWindow().setAttributes(params);
    }

    private void speakCurrent() {
        if (!ttsReady) {
            Toast.makeText(this, "Text-to-speech is still starting", Toast.LENGTH_SHORT).show();
            return;
        }
        if (tts.isSpeaking()) {
            tts.stop();
            Toast.makeText(this, "Read aloud stopped", Toast.LENGTH_SHORT).show();
            return;
        }
        if ("TXT".equals(type)) {
            int start = 0;
            if (textView != null && textScroll != null && textView.getHeight() > 0) start = Math.round(plainText.length() * textProgress());
            tts.speak(plainText.substring(Math.min(start, plainText.length())), TextToSpeech.QUEUE_FLUSH, null, "txt-" + key);
        } else if (webView != null) {
            webView.evaluateJavascript("(document.body.innerText || document.body.textContent)", value -> {
                String speech = value == null ? "" : value.replace("\\n", "\n").replace("\\\"", "\"");
                if (speech.startsWith("\"") && speech.endsWith("\"")) speech = speech.substring(1, speech.length() - 1);
                tts.speak(speech, TextToSpeech.QUEUE_FLUSH, null, "epub-" + key);
            });
        }
    }

    @Override
    public void onInit(int status) {
        ttsReady = status == TextToSpeech.SUCCESS;
        if (ttsReady) tts.setLanguage(Locale.getDefault());
    }

    private boolean isBookmarked() {
        try {
            JSONArray array = new JSONArray(settings.getString("bookmarks_" + key, "[]"));
            int marker = bookmarkMarker();
            for (int i = 0; i < array.length(); i++) if (array.getInt(i) == marker) return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    private int bookmarkMarker() {
        return "TXT".equals(type) ? Math.round(textProgress() * 10000) : currentIndex;
    }

    private void toggleBookmark() {
        try {
            JSONArray source = new JSONArray(settings.getString("bookmarks_" + key, "[]"));
            JSONArray result = new JSONArray();
            int marker = bookmarkMarker();
            boolean removed = false;
            for (int i = 0; i < source.length(); i++) {
                int value = source.getInt(i);
                if (value == marker) removed = true;
                else result.put(value);
            }
            if (!removed) result.put(marker);
            settings.edit().putString("bookmarks_" + key, result.toString()).apply();
            Toast.makeText(this, removed ? "Bookmark removed" : "Bookmark saved", Toast.LENGTH_SHORT).show();
            updateNavigation();
        } catch (Exception ignored) {
        }
    }

    private float textProgress() {
        if (textScroll == null || textView == null) return 0f;
        int range = Math.max(1, textView.getHeight() - textScroll.getHeight());
        return Math.max(0f, Math.min(1f, textScroll.getScrollY() / (float) range));
    }

    private void updateTextProgress() {
        float progress = textProgress();
        store.setProgress(uriString, progress);
        if (pageView != null) pageView.setText(Math.round(progress * 100) + "%");
    }

    private void savePosition() {
        settings.edit().putInt("position_" + key, currentIndex).apply();
        float progress = total <= 1 ? 0f : currentIndex / (float) (total - 1);
        store.setProgress(uriString, progress);
        updateNavigation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if ("TXT".equals(type)) updateTextProgress();
        else savePosition();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (webView != null) webView.destroy();
        if (pdfRenderer != null) pdfRenderer.close();
        try {
            if (pdfDescriptor != null) pdfDescriptor.close();
        } catch (Exception ignored) {
        }
        if (displayedBitmap != null && !displayedBitmap.isRecycled()) displayedBitmap.recycle();
        super.onDestroy();
    }

    public static final class ZoomImageView extends ImageView {
        private final Matrix matrix = new Matrix();
        private final ScaleGestureDetector scaleDetector;
        private final GestureDetector gestureDetector;
        private final PointF last = new PointF();
        private float scale = 1f;
        private Runnable singleTapListener;

        public ZoomImageView(Context context) {
            super(context);
            setScaleType(ScaleType.MATRIX);
            setImageMatrix(matrix);
            scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    float next = Math.max(1f, Math.min(6f, scale * detector.getScaleFactor()));
                    float factor = next / scale;
                    scale = next;
                    matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                    if (scale <= 1.01f) resetMatrix();
                    setImageMatrix(matrix);
                    return true;
                }
            });
            gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapConfirmed(MotionEvent event) {
                    if (singleTapListener != null) singleTapListener.run();
                    return true;
                }

                @Override
                public boolean onDoubleTap(MotionEvent event) {
                    if (scale > 1.1f) resetMatrix();
                    else {
                        scale = 2.5f;
                        matrix.postScale(scale, scale, event.getX(), event.getY());
                        setImageMatrix(matrix);
                    }
                    return true;
                }
            });
        }

        public void setOnSingleTapListener(Runnable listener) {
            singleTapListener = listener;
        }

        @Override
        protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            post(this::resetMatrix);
        }

        private void resetMatrix() {
            if (getDrawable() == null || getWidth() == 0 || getHeight() == 0) return;
            float drawableWidth = getDrawable().getIntrinsicWidth();
            float drawableHeight = getDrawable().getIntrinsicHeight();
            float fit = Math.min(getWidth() / drawableWidth, getHeight() / drawableHeight);
            float dx = (getWidth() - drawableWidth * fit) / 2f;
            float dy = (getHeight() - drawableHeight * fit) / 2f;
            matrix.reset();
            matrix.postScale(fit, fit);
            matrix.postTranslate(dx, dy);
            scale = 1f;
            setImageMatrix(matrix);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            gestureDetector.onTouchEvent(event);
            scaleDetector.onTouchEvent(event);
            if (!scaleDetector.isInProgress() && scale > 1f) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) last.set(event.getX(), event.getY());
                else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    float dx = event.getX() - last.x;
                    float dy = event.getY() - last.y;
                    matrix.postTranslate(dx, dy);
                    setImageMatrix(matrix);
                    last.set(event.getX(), event.getY());
                }
            }
            return true;
        }
    }
}
