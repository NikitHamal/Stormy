package com.oro.app.ui.screens.pdf

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oro.app.OroApplication
import com.oro.app.data.preview.PdfFit
import com.oro.app.data.preview.PdfSourceType
import com.oro.app.data.preview.PdfSources
import com.oro.app.data.preview.PdfViewMath
import com.oro.app.data.repository.LiteMode
import com.oro.app.ui.theme.DevicePerformance
import com.oro.app.ui.theme.OroTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Oro's built-in PDF viewer: documents the agent made or fetched, a PDF link
 * in the live preview, or a PDF opened from the file tree.
 *
 * Pages are drawn by the platform's own PdfRenderer (no extra library, no
 * native code to ship) one at a time into a lazy list, with page jump, zoom
 * (steps, fit width, fit page), rotation, full screen, print, save, share and
 * "open in another app". See [PdfSourceLoader] for what can be opened.
 */
class PdfViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val nightMode = resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val barStyle = if (nightMode) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)

        val raw = intent.getStringExtra(EXTRA_SOURCE).orEmpty()
        val source = if (raw.startsWith(STASH_PREFIX)) stash.remove(raw.removePrefix(STASH_PREFIX)).orEmpty() else raw
        val title = intent.getStringExtra(EXTRA_TITLE)

        setContent {
            val liteModeChoice by OroApplication.getInstance().preferencesRepository.liteMode
                .collectAsStateWithLifecycle(initialValue = LiteMode.AUTO)
            val lowEndDevice = remember { DevicePerformance.isLowEndDevice(applicationContext) }
            OroTheme(liteMode = liteModeChoice.isEnabled(lowEndDevice)) {
                PdfViewerScreen(
                    source = source,
                    titleHint = title,
                    onBack = { finish() },
                    onImmersive = ::setImmersive
                )
            }
        }
    }

    private fun setImmersive(on: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    companion object {
        const val EXTRA_SOURCE = "extra_pdf_source"
        const val EXTRA_TITLE = "extra_pdf_title"

        private const val STASH_PREFIX = "stash:"
        /** Big `data:` URIs wait here: an Intent extra over about 1 MB fails outright. */
        private val stash = ConcurrentHashMap<String, String>()

        fun createIntent(context: Context, source: String, title: String? = null): Intent {
            val carried = if (source.length > 100_000 && PdfSources.classify(source) == PdfSourceType.DATA_URI) {
                val token = System.nanoTime().toString(36)
                stash[token] = source
                STASH_PREFIX + token
            } else source
            return Intent(context, PdfViewerActivity::class.java)
                .putExtra(EXTRA_SOURCE, carried)
                .also { if (title != null) it.putExtra(EXTRA_TITLE, title) }
        }

        /** Opens [source] (a path, URI, link or data URI) in the viewer. */
        fun open(context: Context, source: String, title: String? = null) {
            val intent = createIntent(context, source, title)
            if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}

private sealed interface PdfLoad {
    data object Loading : PdfLoad
    class Ready(val document: PdfDocumentHandle) : PdfLoad
    class Failed(val message: String) : PdfLoad
}

private val PAGE_GAP = 12.dp
private val PAGE_MARGIN = 12.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfViewerScreen(
    source: String,
    titleHint: String?,
    onBack: () -> Unit,
    onImmersive: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var attempt by remember { mutableIntStateOf(0) }

    val load by produceState<PdfLoad>(PdfLoad.Loading, source, attempt) {
        value = PdfLoad.Loading
        value = try {
            val file = PdfSourceLoader.load(context, source)
            PdfLoad.Ready(PdfDocumentHandle.open(file))
        } catch (e: CancellationException) {
            throw e
        } catch (e: PdfSourceException) {
            PdfLoad.Failed(e.message ?: "Couldn't open this PDF.")
        } catch (e: Throwable) {
            PdfLoad.Failed("Couldn't open this PDF.")
        }
    }
    DisposableEffect(load) {
        onDispose { (load as? PdfLoad.Ready)?.document?.close() }
    }

    val name = remember(source, titleHint) { titleHint?.takeIf { it.isNotBlank() } ?: PdfSources.displayName(source) }
    val listState = rememberLazyListState()
    var fit by remember { mutableStateOf(PdfFit.WIDTH) }
    var custom by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableIntStateOf(0) }
    var immersive by remember { mutableStateOf(false) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var showMenu by remember { mutableStateOf(false) }
    var showGoTo by remember { mutableStateOf(false) }

    fun setImmersiveMode(on: Boolean) {
        immersive = on
        onImmersive(on)
    }
    BackHandler(enabled = immersive) { setImmersiveMode(false) }
    DisposableEffect(Unit) { onDispose { onImmersive(false) } }

    val document = (load as? PdfLoad.Ready)?.document
    val density = LocalDensity.current
    val marginPx = with(density) { PAGE_MARGIN.toPx() }
    val baseWidthPx = max(1f, viewport.width - 2 * marginPx)
    val zoom = if (document == null) 1f else {
        val (pw, ph) = document.pageSizes.first().let { PdfViewMath.rotatedSize(it.first, it.second, rotation) }
        PdfViewMath.zoomFor(fit, custom, pw, ph, baseWidthPx, max(1f, viewport.height - 2 * marginPx))
    }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val doc = document
        if (uri != null && doc != null) {
            scope.launch {
                val ok = PdfActions.saveTo(context, doc.file, uri)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, if (ok) "Saved" else "Couldn't save the PDF", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val currentPage by remember(document) {
        derivedStateOf {
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            if (visible.isEmpty()) 0 else {
                val pick = PdfViewMath.currentPage(
                    visible.map { it.offset.toFloat() },
                    visible.map { it.size.toFloat() },
                    0f,
                    (info.viewportEndOffset - info.viewportStartOffset).toFloat()
                )
                visible[pick].index
            }
        }
    }

    fun stepZoom(zoomIn: Boolean) {
        custom = if (zoomIn) PdfViewMath.zoomIn(zoom) else PdfViewMath.zoomOut(zoom)
        fit = PdfFit.CUSTOM
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            if (!immersive) {
                TopAppBar(
                    title = {
                        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (document != null) {
                            IconButton(onClick = { rotation = PdfViewMath.rotate(rotation, clockwise = true) }) {
                                Icon(Icons.Outlined.RotateRight, contentDescription = "Rotate clockwise", modifier = Modifier.size(22.dp))
                            }
                            IconButton(onClick = { setImmersiveMode(true) }) {
                                Icon(Icons.Outlined.Fullscreen, contentDescription = "Full screen", modifier = Modifier.size(22.dp))
                            }
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
                                }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Rotate counter-clockwise") },
                                        leadingIcon = { Icon(Icons.Outlined.RotateLeft, null) },
                                        onClick = { showMenu = false; rotation = PdfViewMath.rotate(rotation, clockwise = false) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Print") },
                                        leadingIcon = { Icon(Icons.Outlined.Print, null) },
                                        onClick = { showMenu = false; PdfActions.print(context, document.file, name) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Save a copy") },
                                        leadingIcon = { Icon(Icons.Outlined.Download, null) },
                                        onClick = { showMenu = false; saver.launch(name.ensurePdfName()) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share") },
                                        leadingIcon = { Icon(Icons.Outlined.Share, null) },
                                        onClick = { showMenu = false; PdfActions.share(context, document.file, name) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Open in another app") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) },
                                        onClick = { showMenu = false; PdfActions.open(context, document.file, name) }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (!immersive && document != null) {
                PdfControlBar(
                    page = currentPage,
                    pageCount = document.pageCount,
                    zoom = zoom,
                    fit = fit,
                    onPrevious = { scope.launch { listState.animateScrollToItem((currentPage - 1).coerceAtLeast(0)) } },
                    onNext = { scope.launch { listState.animateScrollToItem((currentPage + 1).coerceAtMost(document.pageCount - 1)) } },
                    onGoTo = { showGoTo = true },
                    onZoomOut = { stepZoom(false) },
                    onZoomIn = { stepZoom(true) },
                    onZoomPreset = { custom = it; fit = PdfFit.CUSTOM },
                    onFit = { fit = it }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .onSizeChanged { viewport = it }
        ) {
            when (val state = load) {
                PdfLoad.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is PdfLoad.Failed -> PdfProblem(
                    message = state.message,
                    canOpenElsewhere = PdfSources.classify(source).let {
                        it == PdfSourceType.FILE_PATH || it == PdfSourceType.FILE_URI
                    } && File(source.removePrefix("file://")).isFile,
                    onRetry = { attempt++ },
                    onOpenElsewhere = { PdfActions.open(context, File(source.removePrefix("file://")), name) },
                    modifier = Modifier.align(Alignment.Center)
                )
                is PdfLoad.Ready -> PdfPages(
                    document = state.document,
                    listState = listState,
                    viewport = viewport,
                    baseWidthPx = baseWidthPx,
                    zoom = zoom,
                    rotation = rotation,
                    onTap = { setImmersiveMode(!immersive) }
                )
            }
        }
    }

    if (showGoTo && document != null) {
        GoToPageDialog(
            pageCount = document.pageCount,
            current = currentPage + 1,
            onDismiss = { showGoTo = false },
            onGo = { page ->
                showGoTo = false
                scope.launch { listState.animateScrollToItem(page - 1) }
            }
        )
    }
}

private fun String.ensurePdfName(): String = if (endsWith(".pdf", ignoreCase = true)) this else "$this.pdf"

/** The pages: a lazy column, horizontally scrollable once zoomed past the screen's width. */
@Composable
private fun PdfPages(
    document: PdfDocumentHandle,
    listState: LazyListState,
    viewport: IntSize,
    baseWidthPx: Float,
    zoom: Float,
    rotation: Int,
    onTap: () -> Unit
) {
    val density = LocalDensity.current
    val pageWidthPx = max(1, (baseWidthPx * zoom).roundToInt())
    val viewportWidthDp = with(density) { viewport.width.toDp() }
    val contentWidth = maxOf(viewportWidthDp, with(density) { pageWidthPx.toDp() } + PAGE_MARGIN * 2)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(rememberScrollState())
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .width(contentWidth)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PAGE_GAP),
            contentPadding = PaddingValues(vertical = PAGE_MARGIN)
        ) {
            items(count = document.pageCount, key = { it }) { index ->
                PdfPage(document, index, pageWidthPx, rotation)
            }
        }
    }
}

@Composable
private fun PdfPage(document: PdfDocumentHandle, index: Int, widthPx: Int, rotation: Int) {
    val density = LocalDensity.current
    val (pageW, pageH) = document.pageSizes[index].let { PdfViewMath.rotatedSize(it.first, it.second, rotation) }
    val heightPx = max(1, (widthPx * pageH / pageW).roundToInt())
    // Pixels to render: the layout size, capped so a deep zoom can't exhaust memory.
    val (renderW, renderH) = PdfViewMath.renderSize(pageW, pageH, widthPx, 1f)
    val key = PdfPageCache.key(document.id, index, renderW, renderH, rotation)
    var bitmap by remember(key) { mutableStateOf(PdfPageCache.get(key)) }

    LaunchedEffect(key) {
        if (bitmap == null) {
            val rendered = try {
                document.render(index, renderW, renderH, rotation)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            }
            if (rendered != null) {
                PdfPageCache.put(key, rendered)
                bitmap = rendered
            }
        }
    }

    Box(
        modifier = Modifier
            .width(with(density) { widthPx.toDp() })
            .height(with(density) { heightPx.toDp() })
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        val shown = bitmap
        if (shown != null) {
            Image(
                bitmap = shown.asImageBitmap(),
                contentDescription = "Page ${index + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        } else {
            CircularProgressIndicator(Modifier.size(28.dp), color = Color(0xFF9E9E9E), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun PdfControlBar(
    page: Int,
    pageCount: Int,
    zoom: Float,
    fit: PdfFit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onGoTo: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomPreset: (Float) -> Unit,
    onFit: (PdfFit) -> Unit
) {
    var zoomMenu by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious, enabled = page > 0) {
                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Previous page")
                }
                TextButton(onClick = onGoTo) {
                    Text("${page + 1} / $pageCount", maxLines = 1)
                }
                IconButton(onClick = onNext, enabled = page < pageCount - 1) {
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Next page")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onZoomOut) {
                    Icon(Icons.Outlined.Remove, contentDescription = "Zoom out")
                }
                Box {
                    TextButton(onClick = { zoomMenu = true }) {
                        Text(
                            text = when (fit) {
                                PdfFit.PAGE -> "Fit page"
                                else -> "${PdfViewMath.percent(zoom)}%"
                            },
                            maxLines = 1
                        )
                    }
                    DropdownMenu(expanded = zoomMenu, onDismissRequest = { zoomMenu = false }) {
                        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { stop ->
                            DropdownMenuItem(
                                text = { Text("${PdfViewMath.percent(stop)}%") },
                                onClick = { zoomMenu = false; onZoomPreset(stop) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Fit width") },
                            leadingIcon = { Icon(Icons.Outlined.FitScreen, null) },
                            onClick = { zoomMenu = false; onFit(PdfFit.WIDTH) }
                        )
                        DropdownMenuItem(
                            text = { Text("Fit page") },
                            leadingIcon = { Icon(Icons.Outlined.FitScreen, null) },
                            onClick = { zoomMenu = false; onFit(PdfFit.PAGE) }
                        )
                    }
                }
                IconButton(onClick = onZoomIn) {
                    Icon(Icons.Outlined.Add, contentDescription = "Zoom in")
                }
            }
        }
    }
}

@Composable
private fun GoToPageDialog(pageCount: Int, current: Int, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    var text by remember { mutableStateOf(current.toString()) }
    val target = PdfViewMath.parsePageInput(text, pageCount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to page") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(6) },
                singleLine = true,
                label = { Text("1 – $pageCount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        },
        confirmButton = {
            TextButton(onClick = { target?.let(onGo) }, enabled = target != null) { Text("Go") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PdfProblem(
    message: String,
    canOpenElsewhere: Boolean,
    onRetry: () -> Unit,
    onOpenElsewhere: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Outlined.PictureAsPdf,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onRetry) { Text("Try again") }
            if (canOpenElsewhere) TextButton(onClick = onOpenElsewhere) { Text("Open in another app") }
        }
    }
}
