package com.alexivanov.snapsell.ui.bundle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.alexivanov.snapsell.AppContainer
import com.alexivanov.snapsell.domain.BundlePricing
import com.alexivanov.snapsell.domain.Money
import com.alexivanov.snapsell.ui.common.ErrorText
import com.alexivanov.snapsell.ui.common.LoadingBox
import com.alexivanov.snapsell.ui.common.SnapTopBar
import java.io.File

@Composable
fun BundleBuilderScreen(
    container: AppContainer,
    initialItemId: String?,
    onHandOff: (String) -> Unit,
    onBack: () -> Unit,
) {
    val vm: BundleBuilderViewModel = viewModel { BundleBuilderViewModel(container, initialItemId) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { SnapTopBar(title = "Build a bundle", onBack = onBack) },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = vm::writeListing,
                    enabled = state.selected.size >= 2 && !state.writing,
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.writing) CircularProgressIndicator(Modifier.size(18.dp)) else Text(if (state.hasListingText) "Rewrite listing" else "Write listing")
                }
                Button(
                    onClick = { vm.createListing(onHandOff) },
                    enabled = state.hasListingText && state.selected.size >= 2,
                    modifier = Modifier.weight(1f),
                ) { Text("List on Marketplace") }
            }
        },
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Pick priced items", style = MaterialTheme.typography.titleMedium)
                if (state.candidates.isEmpty()) {
                    Text("No priced items yet. Price at least two items first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(state.candidates, key = { it.id }) { item ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.toggle(item.id) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = item.id in state.selected, onCheckedChange = { vm.toggle(item.id) })
                    AsyncImage(
                        model = File(item.photoPath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)),
                    )
                    Text(item.name, Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 2)
                    Text(Money.usd(item.price ?: 0.0), style = MaterialTheme.typography.titleSmall)
                }
            }

            item {
                Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Bundle discount", style = MaterialTheme.typography.labelLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = state.discount.toFloat(),
                                onValueChange = { vm.setDiscount(it.toDouble()) },
                                valueRange = BundlePricing.MIN_DISCOUNT.toFloat()..BundlePricing.MAX_DISCOUNT.toFloat(),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = state.discountText,
                                onValueChange = vm::setDiscountText,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.width(88.dp).padding(start = 8.dp),
                            )
                        }
                        Text("Items add up to ${Money.usd(state.sum)}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Bundle price ${Money.usd(state.bundlePrice)}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        ErrorText(state.error, Modifier.padding(top = 4.dp))
                    }
                }
            }

            if (state.hasListingText) {
                item {
                    OutlinedTextField(
                        value = state.title, onValueChange = vm::editTitle, label = { Text("Title") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.description, onValueChange = vm::editDescription, label = { Text("Description") },
                        minLines = 4, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
