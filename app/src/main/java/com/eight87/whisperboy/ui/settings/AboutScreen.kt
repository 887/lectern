package com.eight87.whisperboy.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eight87.whisperboy.R
import kotlinx.coroutines.launch

/**
 * Phase K.6 — About surface.
 *
 * Renders the app icon + version, license, clean-room "spiritual
 * sibling" credit for Voice, GitHub link, and an open-source licenses
 * row that is wired as TODO for now (the actual `LicensesScreen` is
 * tracked in `docs/plans/oss-licenses.md`).
 *
 * Version + build hash are hard-coded today because the Gradle module
 * has `buildConfig = false` (no `BuildConfig` generated). A follow-up
 * inch will flip `buildConfig = true`, add `VERSION_NAME` / `GIT_SHA`
 * `buildConfigField`s, and replace [VERSION_NAME] / [GIT_SHA_PLACEHOLDER]
 * with `BuildConfig` reads.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val licensesComingSoon = stringResource(R.string.settings_category_pending_snackbar)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_cd),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // App icon + name + version block.
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.about_version_format,
                        VERSION_NAME,
                        GIT_SHA_PLACEHOLDER,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // License / source / OSS-licenses card.
            AboutCard {
                AboutRow(
                    icon = Icons.AutoMirrored.Outlined.Article,
                    title = stringResource(R.string.about_license),
                    subtitle = stringResource(R.string.about_license_subtitle),
                    onClick = { openExternalBrowser(context, LICENSE_URL) },
                )
                AboutRow(
                    icon = Icons.AutoMirrored.Outlined.Launch,
                    title = stringResource(R.string.about_github_label),
                    subtitle = GITHUB_URL,
                    onClick = { openExternalBrowser(context, GITHUB_URL) },
                )
                AboutRow(
                    icon = Icons.Outlined.Code,
                    title = stringResource(R.string.about_oss_licenses_label),
                    subtitle = stringResource(R.string.about_oss_licenses_subtitle),
                    onClick = {
                        // TODO: wire to LicensesScreen when docs/plans/oss-licenses.md ships.
                        scope.launch { snackbarHostState.showSnackbar(licensesComingSoon) }
                    },
                )
            }

            // Clean-room "spiritual sibling" credit. Mirrors tonearmboy's
            // 765c545 pattern: clarify that the relationship with Voice
            // is UX-design-space inspiration only, no shared code, and
            // call out the license delta (MIT vs GPLv3) so a reader
            // doesn't infer a fork.
            AboutCard {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = stringResource(R.string.about_sibling_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.about_sibling_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            content = { content() },
        )
    }
}

@Composable
private fun AboutRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// TODO(K.6 follow-up): replace with BuildConfig.VERSION_NAME / GIT_SHA once
// `buildConfig = true` + buildConfigField wiring lands in app/build.gradle.kts.
// Today the module has `buildConfig = false` so we hard-code.
private const val VERSION_NAME = "1.0"
private const val GIT_SHA_PLACEHOLDER = "dev"

private const val GITHUB_URL = "https://github.com/887/whisperboy"
private const val LICENSE_URL = "https://github.com/887/whisperboy/blob/main/LICENSE"

/**
 * Open a URL in the user's default external browser. Mirrors tonearmboy
 * AboutScreen's pattern — plain `ACTION_VIEW` with `CATEGORY_BROWSABLE`
 * + application-id extra to route through the configured browser
 * launcher rather than an in-app WebView.
 */
private fun openExternalBrowser(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra("com.android.browser.application_id", context.packageName)
    }
    runCatching { context.startActivity(intent) }
}
