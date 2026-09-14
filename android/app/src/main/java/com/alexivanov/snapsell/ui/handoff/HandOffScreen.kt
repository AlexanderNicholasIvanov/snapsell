package com.alexivanov.snapsell.ui.handoff

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.alexivanov.snapsell.AppContainer
import com.alexivanov.snapsell.domain.ListingStatus
import com.alexivanov.snapsell.domain.Money
import com.alexivanov.snapsell.handoff.ClipboardStager
import com.alexivanov.snapsell.ui.common.ErrorText
import com.alexivanov.snapsell.ui.common.LoadingBox
import com.alexivanov.snapsell.ui.common.SnapTopBar
import com.alexivanov.snapsell.ui.common.StatusChip
import java.io.File

@Composable
fun HandOffScreen(
    container: AppContainer,
    listingId: String,
    onFinished: () -> Unit,
    onBack: () -> Unit,
) {
    val vm: HandOffViewModel = viewModel(key = listingId) { HandOffViewModel(container, listingId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Coming back from Facebook is the cue to ask how it went.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResumed() }

    // API 24-28 need WRITE_EXTERNAL_STORAGE to create Pictures/Resale.
    val needsLegacyPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
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

    Scaffold(topBar = { SnapTopBar(title = "List on Marketplace", onBack = onBack) }) { padding ->
        val listing = state.listing
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        if (listing == null) {
            Column(Modifier.padding(padding).padding(24.dp)) { Text("This listing no longer exists.") }
            return@Scaffold
        }

        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(listing.listing.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                StatusChip(listing.listing.status)
            }
            Text(Money.usd(listing.listing.price), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                items(listing.items, key = { it.id }) { item ->
                    AsyncImage(
                        model = File(item.photoPath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)),
                    )
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("How this works", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "1. Your ${listing.items.size} photo${if (listing.items.size == 1) "" else "s"} are saved to the \"Resale\" album in your gallery.\n" +
                            "2. The title, price and description are copied to the clipboard as one block.\n" +
                            "3. Facebook Marketplace opens. Create a listing, pick the photos from the Resale album, and paste the text.\n" +
                            "4. Come back here and tell SnapSell whether you listed it.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            Text("Clipboard preview", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
            Text(
                ClipboardStager.format(listing.listing.title, listing.listing.price, listing.listing.description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            ErrorText(state.error, Modifier.padding(top = 8.dp))
            if (state.savedCount > 0) {
                Text("Saved ${state.savedCount} photo(s) to Pictures/Resale.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }

            Button(onClick = { launch() }, enabled = !state.staging, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                if (state.staging) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary) else Text("Open Facebook Marketplace")
            }
            OutlinedButton(onClick = { vm.copyTextAgain(context) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Copy text again") }
            if (listing.listing.status == ListingStatus.LISTED) {
                OutlinedButton(onClick = { vm.markSold(onFinished) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Mark as sold") }
            }
        }

        if (state.askOutcome) {
            AlertDialog(
                onDismissRequest = vm::dismissOutcome,
                title = { Text("Did you list it?") },
                text = { Text("Mark this listing so your inventory stays accurate.") },
                confirmButton = {
                    Button(onClick = { vm.markListed(onFinished) }) { Text("Listed") }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { vm.markSkipped(onFinished) }) { Text("Skipped") }
                        TextButton(onClick = { vm.dismissOutcome(); launch() }) { Text("Try again") }
                    }
                },
            )
        }
    }
}
