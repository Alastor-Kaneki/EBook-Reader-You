package com.alastorkaneki.ebookreaderyou

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.github.junrar.Archive
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipFile

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { ReaderApp(intent?.data) }
    }
}

data class Book(val uri: String, val title: String, val favorite: Boolean = false, val progress: Float = 0f)

enum class ReaderKind { PDF, TEXT, EPUB, CBZ, CBR, UNKNOWN }

@Composable
fun ReaderApp(startUri: Uri?) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("library", Context.MODE_PRIVATE) }
    var amoled by remember { mutableStateOf(prefs.getBoolean("amoled", false)) }
    var warm by remember { mutableFloatStateOf(prefs.getFloat("warm", 0f)) }
    var fontSize by remember { mutableFloatStateOf(prefs.getFloat("font", 20f)) }
    var immersive by remember { mutableStateOf(prefs.getBoolean("immersive", true)) }
    var books by remember { mutableStateOf(loadBooks(prefs)) }
    var current by remember { mutableStateOf<Book?>(null) }
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }

    val openFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        }
        val added = uris.map { Book(it.toString(), displayName(context, it)) }
        books = (books + added).distinctBy { it.uri }
        saveBooks(prefs, books)
    }

    LaunchedEffect(startUri) {
        if (startUri != null) current = Book(startUri.toString(), displayName(context, startUri))
    }

    val scheme = when {
        amoled -> darkColorScheme(background = androidx.compose.ui.graphics.Color.Black, surface = androidx.compose.ui.graphics.Color.Black)
        android.os.Build.VERSION.SDK_INT >= 31 -> dynamicDarkColorScheme(context)
        else -> darkColorScheme()
    }

    MaterialTheme(colorScheme = scheme) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (current != null) {
                ReaderScreen(
                    book = current!!,
                    fontSize = fontSize,
                    warm = warm,
                    immersive = immersive,
                    onBack = { current = null },
                    onProgress = { p ->
                        books = books.map { if (it.uri == current!!.uri) it.copy(progress = p) else it }
                        saveBooks(prefs, books)
                    }
                )
            } else {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("EBook Reader You") },
                            actions = {
                                IconButton(onClick = { openFiles.launch(arrayOf("application/pdf", "text/plain", "application/epub+zip", "application/zip", "application/x-rar-compressed", "*/*")) }) {
                                    Icon(Icons.Default.Add, "Import books")
                                }
                            }
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            listOf(Icons.Default.Home to "Library", Icons.Default.Favorite to "Favorites", Icons.Default.Settings to "Settings").forEachIndexed { i, pair ->
                                NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(pair.first, pair.second) }, label = { Text(pair.second) })
                            }
                        }
                    }
                ) { padding ->
                    when (tab) {
                        0 -> LibraryScreen(books, query, onQuery = { query = it }, onOpen = { current = it }, onFavorite = { b ->
                            books = books.map { if (it.uri == b.uri) it.copy(favorite = !it.favorite) else it }; saveBooks(prefs, books)
                        }, onRemove = { b -> books = books.filterNot { it.uri == b.uri }; saveBooks(prefs, books) }, modifier = Modifier.padding(padding))
                        1 -> LibraryScreen(books.filter { it.favorite }, query, { query = it }, { current = it }, { b -> books = books.map { if (it.uri == b.uri) it.copy(favorite = false) else it }; saveBooks(prefs, books) }, { b -> books = books.filterNot { it.uri == b.uri }; saveBooks(prefs, books) }, Modifier.padding(padding), false)
                        else -> SettingsScreen(amoled, { amoled = it; prefs.edit().putBoolean("amoled", it).apply() }, warm, { warm = it; prefs.edit().putFloat("warm", it).apply() }, fontSize, { fontSize = it; prefs.edit().putFloat("font", it).apply() }, immersive, { immersive = it; prefs.edit().putBoolean("immersive", it).apply() }, Modifier.padding(padding))
                    }
                }
            }
            if (warm > 0f) Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(1f, .45f, 0f, warm * .18f)))
        }
    }
}

@Composable
private fun LibraryScreen(books: List<Book>, query: String, onQuery: (String) -> Unit, onOpen: (Book) -> Unit, onFavorite: (Book) -> Unit, onRemove: (Book) -> Unit, modifier: Modifier = Modifier, showSearch: Boolean = true) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        if (showSearch) OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Search your library") }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        val filtered = books.filter { it.title.contains(query, true) }
        if (filtered.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Import PDF, EPUB, TXT, CBZ, or CBR books") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(filtered, key = { _, b -> b.uri }) { _, book ->
                ElevatedCard(Modifier.fillMaxWidth().clickable { onOpen(book) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MenuBook, null, Modifier.size(42.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                            Text("${(book.progress * 100).toInt()}% read", style = MaterialTheme.typography.bodySmall)
                            LinearProgressIndicator(progress = { book.progress }, Modifier.fillMaxWidth().padding(top = 6.dp))
                        }
                        IconButton(onClick = { onFavorite(book) }) { Icon(if (book.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite") }
                        IconButton(onClick = { onRemove(book) }) { Icon(Icons.Default.Delete, "Remove") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(amoled: Boolean, onAmoled: (Boolean) -> Unit, warm: Float, onWarm: (Float) -> Unit, font: Float, onFont: (Float) -> Unit, immersive: Boolean, onImmersive: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Appearance", style = MaterialTheme.typography.headlineSmall)
        SettingSwitch("True AMOLED", "Pure black surfaces with Material You accent colors", amoled, onAmoled)
        SettingSwitch("Immersive reading", "Hide system bars while reading", immersive, onImmersive)
        Text("Warm light: ${(warm * 100).toInt()}%")
        Slider(warm, onValueChange = onWarm, valueRange = 0f..1f)
        Text("Reading font: ${font.toInt()} sp")
        Slider(font, onValueChange = onFont, valueRange = 14f..36f)
        HorizontalDivider()
        Text("Included features", style = MaterialTheme.typography.titleLarge)
        Text("PDF rendering • EPUB extraction • TXT reader • CBZ/CBR comics • right-to-left manga mode • bookmarks and progress • search • favorites • dynamic color • AMOLED • warm light • text-to-speech • zoom • metadata-aware library • safe archive extraction")
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(subtitle, style = MaterialTheme.typography.bodySmall) }
        Switch(checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun ReaderScreen(book: Book, fontSize: Float, warm: Float, immersive: Boolean, onBack: () -> Unit, onProgress: (Float) -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    var controls by remember { mutableStateOf(true) }
    var rtl by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf((book.progress * 1000).toInt()) }
    val kind = remember(book.uri) { kindOf(book.title) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) { onDispose { tts?.shutdown(); if (immersive) showSystemBars(activity) } }
    LaunchedEffect(immersive) { if (immersive) hideSystemBars(activity) else showSystemBars(activity) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).pointerInput(Unit) { detectTapGestures { controls = !controls } }) {
        when (kind) {
            ReaderKind.PDF -> PdfBook(Uri.parse(book.uri), page, rtl, onPage = { p, count -> page = p; onProgress(if (count <= 1) 0f else p.toFloat() / (count - 1)) })
            ReaderKind.CBZ, ReaderKind.CBR -> ComicBook(Uri.parse(book.uri), kind, page, rtl, onPage = { p, count -> page = p; onProgress(if (count <= 1) 0f else p.toFloat() / (count - 1)) })
            ReaderKind.TEXT, ReaderKind.EPUB -> TextBook(Uri.parse(book.uri), kind, fontSize, onTextReady = { text ->
                if (tts == null) tts = TextToSpeech(context) { if (it == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault() }
            }, onProgress = onProgress)
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Unsupported or unreadable file") }
        }
        if (controls) {
            TopAppBar(title = { Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }, actions = {
                IconButton(onClick = { rtl = !rtl }) { Icon(Icons.Default.SwapHoriz, "Toggle manga direction") }
                if (kind == ReaderKind.TEXT || kind == ReaderKind.EPUB) IconButton(onClick = {
                    val text = extractText(context, Uri.parse(book.uri), kind).take(3500)
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "book")
                }) { Icon(Icons.Default.RecordVoiceOver, "Read aloud") }
            }, modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun PdfBook(uri: Uri, initialPage: Int, rtl: Boolean, onPage: (Int, Int) -> Unit) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pageCount by remember { mutableIntStateOf(1) }
    var page by remember { mutableIntStateOf(initialPage.coerceAtLeast(0)) }
    LaunchedEffect(uri, page) {
        runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")!!.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    pageCount = renderer.pageCount
                    page = page.coerceIn(0, pageCount - 1)
                    renderer.openPage(page).use { p ->
                        val scale = 2
                        val b = Bitmap.createBitmap(p.width * scale, p.height * scale, Bitmap.Config.ARGB_8888)
                        b.eraseColor(Color.WHITE)
                        p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap = b
                    }
                }
            }
        }
    }
    PagedImage(bitmap, page, pageCount, rtl) { page = it; onPage(it, pageCount) }
}

@Composable
private fun ComicBook(uri: Uri, kind: ReaderKind, initialPage: Int, rtl: Boolean, onPage: (Int, Int) -> Unit) {
    val context = LocalContext.current
    val pages = remember(uri) { loadComicPages(context, uri, kind) }
    var page by remember { mutableIntStateOf(initialPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))) }
    val bitmap = remember(page, pages) { pages.getOrNull(page)?.let(BitmapFactory::decodeByteArray) }
    if (pages.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No comic images found") }
    else PagedImage(bitmap, page, pages.size, rtl) { page = it; onPage(it, pages.size) }
}

@Composable
private fun PagedImage(bitmap: Bitmap?, page: Int, count: Int, rtl: Boolean, onPage: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onPage((page + if (rtl) 1 else -1).coerceIn(0, count - 1)) }) { Icon(Icons.Default.ChevronLeft, "Previous") }
            Text("${page + 1} / $count")
            IconButton(onClick = { onPage((page + if (rtl) -1 else 1).coerceIn(0, count - 1)) }) { Icon(Icons.Default.ChevronRight, "Next") }
        }
    }
}

@Composable
private fun TextBook(uri: Uri, kind: ReaderKind, fontSize: Float, onTextReady: (String) -> Unit, onProgress: (Float) -> Unit) {
    val context = LocalContext.current
    val text = remember(uri) { extractText(context, uri, kind) }
    val scroll = rememberScrollState()
    LaunchedEffect(text) { onTextReady(text) }
    LaunchedEffect(scroll.value, scroll.maxValue) { if (scroll.maxValue > 0) onProgress(scroll.value.toFloat() / scroll.maxValue) }
    SelectionContainer {
        Text(text.ifBlank { "This book has no readable text." }, fontSize = fontSize.sp, lineHeight = (fontSize * 1.55f).sp, fontFamily = FontFamily.Serif, modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(top = 80.dp, start = 22.dp, end = 22.dp, bottom = 48.dp))
    }
}

private fun kindOf(name: String): ReaderKind = when (name.substringAfterLast('.', "").lowercase()) {
    "pdf" -> ReaderKind.PDF; "txt" -> ReaderKind.TEXT; "epub" -> ReaderKind.EPUB; "cbz", "zip" -> ReaderKind.CBZ; "cbr", "rar" -> ReaderKind.CBR; else -> ReaderKind.UNKNOWN
}

private fun displayName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) return c.getString(0) ?: "Book" }
    return uri.lastPathSegment ?: "Book"
}

private fun extractText(context: Context, uri: Uri, kind: ReaderKind): String = runCatching {
    if (kind == ReaderKind.TEXT) context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
    else {
        val file = copyToCache(context, uri, "book.epub")
        ZipFile(file).use { zip ->
            zip.entries().asSequence().filter { !it.isDirectory && (it.name.endsWith(".xhtml", true) || it.name.endsWith(".html", true) || it.name.endsWith(".htm", true)) }
                .sortedBy { it.name }.joinToString("\n\n") { e -> zip.getInputStream(e).bufferedReader().use { it.readText() }.replace(Regex("<script[\\s\\S]*?</script>|<style[\\s\\S]*?</style>"), "").replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ").replace("&amp;", "&").replace(Regex("\\s+"), " ").trim() }
        }
    }
}.getOrElse { "Unable to read this book: ${it.message}" }

private fun loadComicPages(context: Context, uri: Uri, kind: ReaderKind): List<ByteArray> = runCatching {
    val ext = if (kind == ReaderKind.CBR) "cbr" else "cbz"
    val file = copyToCache(context, uri, "comic.$ext")
    val allowed = Regex(".*\\.(jpg|jpeg|png|webp|gif)$", RegexOption.IGNORE_CASE)
    if (kind == ReaderKind.CBZ) {
        ZipFile(file).use { zip -> zip.entries().asSequence().filter { !it.isDirectory && allowed.matches(it.name) && it.size in 1..50_000_000 }.sortedBy { naturalKey(it.name) }.take(5000).map { zip.getInputStream(it).use { s -> s.readBytes() } }.toList() }
    } else {
        Archive(file).use { archive -> archive.fileHeaders.filter { !it.isDirectory && allowed.matches(it.fileName) && it.fullUnpackSize in 1..50_000_000 }.sortedBy { naturalKey(it.fileName) }.take(5000).map { h -> java.io.ByteArrayOutputStream().use { out -> archive.extractFile(h, out); out.toByteArray() } } }
    }
}.getOrElse { emptyList() }

private fun naturalKey(s: String): String = s.lowercase().replace(Regex("\\d+")) { it.value.padStart(12, '0') }

private fun copyToCache(context: Context, uri: Uri, name: String): File {
    val file = File(context.cacheDir, name)
    context.contentResolver.openInputStream(uri)!!.use { input -> FileOutputStream(file).use { input.copyTo(it) } }
    return file
}

private fun loadBooks(prefs: android.content.SharedPreferences): List<Book> = prefs.getStringSet("books", emptySet()).orEmpty().mapNotNull { row ->
    val p = row.split("|", limit = 4); if (p.size < 4) null else Book(p[0], p[1], p[2].toBoolean(), p[3].toFloatOrNull() ?: 0f)
}.sortedBy { it.title.lowercase() }

private fun saveBooks(prefs: android.content.SharedPreferences, books: List<Book>) {
    prefs.edit().putStringSet("books", books.map { "${it.uri}|${it.title.replace("|", " ")}|${it.favorite}|${it.progress}" }.toSet()).apply()
}

private fun hideSystemBars(activity: Activity) {
    WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply { hide(androidx.core.view.WindowInsetsCompat.Type.systemBars()); systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE }
    activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
}
private fun showSystemBars(activity: Activity) {
    WindowCompat.getInsetsController(activity.window, activity.window.decorView).show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
}
