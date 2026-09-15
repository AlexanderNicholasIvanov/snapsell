package com.alexivanov.snapsell.ui.handoff

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alexivanov.snapsell.AppContainer
import com.alexivanov.snapsell.R
import com.alexivanov.snapsell.ui.common.ErrorText
import com.alexivanov.snapsell.ui.common.GhostButton
import com.alexivanov.snapsell.ui.common.LoadingBox
import com.alexivanov.snapsell.ui.common.LucideIcon
import com.alexivanov.snapsell.ui.common.MicroLabel
import com.alexivanov.snapsell.ui.common.PrimaryButton
import com.alexivanov.snapsell.ui.common.Rule
import com.alexivanov.snapsell.ui.common.Scrim
import com.alexivanov.snapsell.ui.common.SecondaryButton
import com.alexivanov.snapsell.ui.common.SnapBottomBar
import com.alexivanov.snapsell.ui.common.SnapTopBar
import com.alexivanov.snapsell.ui.common.Spinner
import com.alexivanov.snapsell.ui.common.StatusChip
import com.alexivanov.snapsell.ui.theme.SnapType
import com.alexivanov.snapsell.ui.theme.snapColors

@Composable
fun HandOffScreen(
    container: AppContainer,
    listingId: String,
    onFinished: (tab: Int) -> Unit,
    onBack: () -> Unit,
) {
    val vm: HandOffViewModel = viewModel(key = listingId) { HandOffViewModel(container, listingId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = snapColors

    // Coming back from Facebook is the cue to ask how it went.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResumed() }

    // API 24-28 need WRITE_EXTERNAL_STORAGE to create Pictures/Resale.
    val needsLegacyPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Proceed either way: without the album the clipboard + link still work.
        vm.stageAndOpen(context)
    }
    fun launch() {
        if (needsLegacyPermission &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            vm.stageAndOpen(context)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = c.bg,
            topBar = { SnapTopBar(title = "Hand-off", onBack = onBack) },
            bottomBar = {
                if (state.listing != null) {
                    SnapBottomBar {
                        PrimaryButton(
                            "Open Facebook Marketplace",
                            onClick = { launch() },
                            enabled = !state.staging,
                            minHeight = 58.dp,
                            modifier = Modifier.weight(1f),
                            leading = { if (state.staging) Spinner(color = c.bg) else LucideIcon(R.drawable.ic_lucide_external_link, null, size = 20.dp, tint = c.bg) },
                        )
                    }
                }
            },
        ) { padding ->
            val listing = state.listing
            if (state.loading) {
                LoadingBox(Modifier.padding(padding))
                return@Scaffold
            }
            if (listing == null) {
                Column(Modifier.padding(padding).padding(24.dp)) { Text("This listing no longer exists.", style = SnapType.body, color = c.text) }
                return@Scaffold
            }
            val n = listing.items.size

            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(listing.listing.status)
                }
                Text(
                    if (state.left) "Waiting for you to come back" else "Two things are ready",
                    style = SnapType.screenHero,
                    color = c.text,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    "Marketplace won't let an app post for you, so SnapSell gets everything ready and you paste it in.",
                    style = SnapType.bodyLarge,
                    color = c.text.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 10.dp),
                )

                // Guided steps: 2dp top rule, 1dp dividers, 28dp numbered squares, accent check when done.
                Column(Modifier.padding(top = 24.dp)) {
                    Rule(thickness = 2.dp)
                    val photos = if (state.staged) state.savedCount else n
                    Step(1, "Photos saved", "$photos cutout${if (photos == 1) "" else "s"} written to your Resale album.", done = state.staged)
                    Step(2, "Text copied", "Title and description are on the clipboard.", done = state.staged)
                    Step(3, "Paste it into Marketplace", "Facebook opens next. Pick the photos, long-press to paste.", done = state.left)
                }

                ErrorText(state.error, Modifier.padding(top = 12.dp))

                Column(Modifier.fillMaxWidth().padding(top = 24.dp).border(1.dp, c.divider).padding(14.dp)) {
                    MicroLabel("On your clipboard")
                    Text(listing.listing.title, style = SnapType.body.copy(fontWeight = FontWeight.ExtraBold), color = c.text, modifier = Modifier.padding(top = 6.dp))
                    Text(
                        listing.listing.description.lineSequence().firstOrNull().orEmpty(),
                        style = SnapType.bodySmall.copy(fontSize = 12.5.sp),
                        color = c.text.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        if (state.askOutcome) {
            Scrim(onClick = vm::dismissOutcome)
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .shadow(12.dp, RectangleShape, ambientColor = c.shadow, spotColor = c.shadow)
                    .background(c.bg)
                    .border(2.dp, c.text)
                    .padding(20.dp),
            ) {
                Text("Did it go up?", style = SnapType.sectionHead, color = c.text)
                Text("Tell SnapSell what happened so the item lands in the right place.", style = SnapType.body, color = c.text.copy(alpha = 0.75f), modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Listed", onClick = { vm.markListed(onFinished) }, minHeight = 50.dp, modifier = Modifier.fillMaxWidth())
                    SecondaryButton("Skipped", onClick = { vm.markSkipped(onFinished) }, minHeight = 50.dp, modifier = Modifier.fillMaxWidth())
                    GhostButton("Try again", onClick = vm::dismissOutcome, minHeight = 44.dp, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun Step(n: Int, title: String, body: String, done: Boolean) {
    val c = snapColors
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).then(if (done) Modifier.background(c.accent) else Modifier.border(1.dp, c.divider)),
                contentAlignment = Alignment.Center,
            ) {
                Text("$n", style = SnapType.rowTitle.copy(fontSize = 13.sp), color = if (done) c.bg else c.text)
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, style = SnapType.rowTitle, color = c.text)
                Text(body, style = SnapType.bodySmall, color = c.text.copy(alpha = 0.65f), modifier = Modifier.padding(top = 2.dp))
            }
            if (done) LucideIcon(R.drawable.ic_lucide_check, "Done", size = 20.dp, tint = c.accent)
            else Spacer(Modifier.width(20.dp))
        }
        Rule(thickness = 1.dp)
    }
}
