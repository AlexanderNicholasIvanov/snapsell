package com.alexivanov.snapsell.ui.bundle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alexivanov.snapsell.AppContainer
import com.alexivanov.snapsell.domain.Money
import com.alexivanov.snapsell.ui.common.CutoutTile
import com.alexivanov.snapsell.ui.common.ErrorText
import com.alexivanov.snapsell.ui.common.LoadingBox
import com.alexivanov.snapsell.ui.common.MicroLabel
import com.alexivanov.snapsell.ui.common.PrimaryButton
import com.alexivanov.snapsell.ui.common.Rule
import com.alexivanov.snapsell.ui.common.SecondaryButton
import com.alexivanov.snapsell.ui.common.SnapBottomBar
import com.alexivanov.snapsell.ui.common.SnapSlider
import com.alexivanov.snapsell.ui.common.SnapTextField
import com.alexivanov.snapsell.ui.common.SnapTopBar
import com.alexivanov.snapsell.ui.common.Spinner
import com.alexivanov.snapsell.ui.common.SquareCheckbox
import com.alexivanov.snapsell.ui.theme.SnapType
import com.alexivanov.snapsell.ui.theme.snapColors
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
    val c = snapColors

    Scaffold(
        containerColor = c.bg,
        topBar = { SnapTopBar(title = "Bundle", onBack = onBack) },
        bottomBar = {
            SnapBottomBar {
                PrimaryButton(
                    "List on Marketplace",
                    onClick = { vm.createListing(onHandOff) },
                    enabled = state.hasListingText && state.selected.size >= 2,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                MicroLabel("Priced items", Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
                Rule(thickness = 2.dp)
                if (state.candidates.isEmpty()) {
                    Text(
                        "No priced items yet. Confirm and price at least two items first.",
                        style = SnapType.body,
                        color = c.text.copy(alpha = 0.7f),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(state.candidates, key = { it.id }) { item ->
                val on = item.id in state.selected
                Column(Modifier.fillMaxWidth().clickable { vm.toggle(item.id) }) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        SquareCheckbox(on, onCheckedChange = { vm.toggle(item.id) })
                        Spacer(Modifier.width(12.dp))
                        CutoutTile(File(item.photoPath), 40.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(item.name, style = SnapType.rowTitle.copy(fontSize = 14.sp), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.condition.label, style = SnapType.fieldLabel, color = c.text.copy(alpha = 0.6f))
                        }
                        Text(Money.usd(item.price ?: 0.0), style = SnapType.rowTitle.copy(fontSize = 16.sp), color = c.text)
                    }
                    Rule(thickness = 1.dp)
                }
            }

            item {
                // Summary card: 16dp margin, 2dp border, 16dp padding, three bands.
                Column(Modifier.padding(16.dp).fillMaxWidth().border(2.dp, c.text).padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Sum of ${state.selected.size} item${if (state.selected.size == 1) "" else "s"}", style = SnapType.bodySmall, color = c.text.copy(alpha = 0.75f), modifier = Modifier.weight(1f))
                        Text(Money.usd(state.sum), style = SnapType.rowPrice, color = c.text)
                    }
                    Rule(Modifier.padding(vertical = 14.dp), 1.dp)

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Bundle discount", style = SnapType.body, color = c.text, modifier = Modifier.weight(1f))
                        SnapTextField(
                            value = state.discountText,
                            onValueChange = vm::setDiscountText,
                            minHeight = 40.dp,
                            textStyle = SnapType.fieldValueBold.copy(fontSize = 16.sp),
                            textAlign = TextAlign.End,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(66.dp),
                        )
                        Text("%", style = SnapType.body, color = c.text, modifier = Modifier.padding(start = 6.dp))
                    }
                    SnapSlider(
                        value = (state.discount * 100).toFloat(),
                        onValueChange = { vm.setDiscountPercent(it.toInt()) },
                        valueRange = 50f..100f,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(Modifier.fillMaxWidth()) {
                        MicroLabel("50%", alpha = 0.55f)
                        Spacer(Modifier.weight(1f))
                        MicroLabel("100%", alpha = 0.55f)
                    }
                    Rule(Modifier.padding(vertical = 14.dp), 2.dp)

                    MicroLabel("Bundle price")
                    Text(Money.usd(state.bundlePrice), style = SnapType.bundlePrice, color = c.accent700, modifier = Modifier.padding(top = 2.dp))
                    Text(
                        "${Money.usd(state.sum)} × ${(state.discount * 100).toInt()}% = ${Money.usd(state.bundlePrice)}",
                        style = SnapType.fieldLabel.copy(fontSize = 11.5.sp),
                        color = c.text.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    ErrorText(state.error, Modifier.padding(top = 8.dp))
                }
            }

            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SecondaryButton(
                        label = if (state.hasListingText) "Rewrite listing" else "Write listing",
                        onClick = vm::writeListing,
                        enabled = state.selected.size >= 2 && !state.writing,
                        modifier = Modifier.fillMaxWidth(),
                        trailing = if (state.writing) ({ Spinner() }) else null,
                    )
                    if (state.hasListingText) {
                        SnapTextField(state.title, vm::editTitle, label = "Title", modifier = Modifier.padding(top = 16.dp))
                        SnapTextField(
                            state.description, vm::editDescription, label = "Description", singleLine = false, minHeight = 150.dp,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    Box(Modifier.height(8.dp))
                }
            }
        }
    }
}
