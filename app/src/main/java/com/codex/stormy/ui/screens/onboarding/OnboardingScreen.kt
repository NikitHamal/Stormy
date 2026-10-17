package com.codex.stormy.ui.screens.onboarding

import android.provider.Settings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codex.stormy.R
import kotlinx.coroutines.launch

private data class WelcomePage(val asset: String, val eyebrow: Int, val title: Int, val detail: Int)
private val pages = listOf(
    WelcomePage("welcome", R.string.oro_intro_label, R.string.oro_intro_title, R.string.oro_intro_detail),
    WelcomePage("prompt", R.string.oro_prompt_label, R.string.oro_prompt_title, R.string.oro_prompt_detail),
    WelcomePage("models", R.string.oro_models_label, R.string.oro_models_title, R.string.oro_models_detail)
)

/** Native controls and accessible copy; the supplied SVG artwork stays vector sharp. */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory)
) {
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val finishing = uiState.isLoading
    val finish: () -> Unit = { viewModel.completeOnboarding(onComplete) }
    BackHandler(enabled = pager.currentPage > 0 && !finishing) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { insets ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(insets)) {
            val wide = maxWidth > 650.dp
            val artworkHeight = (maxHeight * .43f).coerceIn(170.dp, 350.dp)
            Column(Modifier.fillMaxSize().widthIn(max = 1000.dp).align(Alignment.Center)) {
                Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 16.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Oro", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = finish, enabled = !finishing) { Text(stringResource(R.string.oro_skip)) }
                }
                HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
                    val page = pages[index]
                    if (wide) {
                        Row(Modifier.fillMaxSize().padding(28.dp), verticalAlignment = Alignment.CenterVertically) {
                            Artwork(page.asset, pager.currentPage == index, Modifier.weight(1f).fillMaxHeight())
                            PageCopy(page, Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(28.dp))
                        }
                    } else {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp)) {
                            Artwork(page.asset, pager.currentPage == index,
                                Modifier.fillMaxWidth().heightIn(min = 170.dp, max = 350.dp)
                                    .height(artworkHeight))
                            PageCopy(page, Modifier.padding(top = 20.dp, bottom = 12.dp))
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(Modifier.clearAndSetSemantics { contentDescription = "${pager.currentPage + 1} / ${pages.size}" },
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            repeat(pages.size) { index ->
                                val width by animateDpAsState(if (index == pager.currentPage) 28.dp else 7.dp,
                                    tween(240), label = "page progress")
                                Box(Modifier.size(width, 4.dp).clip(CircleShape).background(
                                    if (index == pager.currentPage) MaterialTheme.colorScheme.onBackground
                                    else MaterialTheme.colorScheme.outlineVariant))
                            }
                        }
                        Text("0${pager.currentPage + 1} / 03", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (uiState.saveFailed) {
                        Text(stringResource(R.string.oro_save_error), color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = {
                        if (pager.currentPage == pages.lastIndex) finish()
                        else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    }, enabled = !finishing && !pager.isScrollInProgress,
                        modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) {
                        Text(stringResource(if (pager.currentPage == pages.lastIndex) R.string.oro_start else R.string.oro_continue))
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PageCopy(page: WelcomePage, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(page.eyebrow), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(page.title), style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold)
        Text(stringResource(page.detail), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Artwork(asset: String, active: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val background = MaterialTheme.colorScheme.background
    val light = background.red + background.green + background.blue > 1.5f
    // Android's animation toggle also applies to CSS, without enabling script or a bridge.
    val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val html = remember(asset, light, reduced, active) {
        context.assets.open("onboarding/$asset.html").bufferedReader().use { it.readText() }
            .replace("ORO_THEME", if (light) "light" else "dark")
            .replace("ORO_MOTION", if (reduced || !active) "still" else "motion")
    }
    AndroidView(modifier = modifier.clip(RoundedCornerShape(24.dp)).clearAndSetSemantics { },
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(background.toArgb())
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkLoads = true
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?) = true
                }
            }
        },
        update = { view ->
            if (view.tag != html) {
                view.tag = html
                view.setBackgroundColor(background.toArgb())
                view.loadDataWithBaseURL("https://oro.invalid/", html, "text/html", "UTF-8", null)
            }
        },
        onRelease = { it.stopLoading(); it.destroy() }
    )
}
