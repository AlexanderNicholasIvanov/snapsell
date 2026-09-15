package com.alexivanov.snapsell.ui.confirm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alexivanov.snapsell.AppContainer
import com.alexivanov.snapsell.R
import com.alexivanov.snapsell.domain.Money
import com.alexivanov.snapsell.ui.common.ConditionChipRow
import com.alexivanov.snapsell.ui.common.CutoutTile
import com.alexivanov.snapsell.ui.common.ErrorText
import com.alexivanov.snapsell.ui.common.FieldLabel
import com.alexivanov.snapsell.ui.common.LoadingBox
import com.alexivanov.snapsell.ui.common.LucideIcon
import com.alexivanov.snapsell.ui.common.MicroLabel
import com.alexivanov.snapsell.ui.common.PrimaryButton
import com.alexivanov.snapsell.ui.common.Rule
import com.alexivanov.snapsell.ui.common.SecondaryButton
import com.alexivanov.snapsell.ui.common.Skeleton
import com.alexivanov.snapsell.ui.common.SnapBottomBar
import com.alexivanov.snapsell.ui.common.SnapTextField
import com.alexivanov.snapsell.ui.common.SnapTopBar
import com.alexivanov.snapsell.ui.common.Spinner
import com.alexivanov.snapsell.ui.theme.SnapType
import com.alexivanov.snapsell.ui.theme.snapColors
import java.io.File

@Composable
fun ConfirmScreen(
    container: AppContainer,
    itemIds: List<String>,
    onOpenItem: (String) -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val vm: ConfirmViewModel = viewModel(key = itemIds.joinToString(",")) { ConfirmViewModel(container, itemIds) }
    val state by vm.state.collectAsStateWithLifecycle()
    val c = snapColors
    val confirmedCount = state.cards.count { it.confirmed }
    val outstanding = state.cards.count { it.identified && !it.confirmed }

    Scaffold(
        containerColor = c.bg,
        topBar = {
            SnapTopBar(title = "Confirm", onBack = onBack) {
                MicroLabel("$confirmedCount/${state.cards.size} confirmed", alpha = 0.55f, style = SnapType.microLabel.copy(fontSize = 11.sp), modifier = Modifier.padding(end = 16.dp))
            }
        },
        bottomBar = {
            SnapBottomBar {
                if (outstanding > 1) {
                    SecondaryButton("Confirm all", onClick = vm::confirmAll, enabled = !state.anyBusy, modifier = Modifier.weight(1f))
                }
                PrimaryButton(
                    "Done",
                    onClick = onDone,
                    enabled = state.cards.isNotEmpty() && state.cards.all { it.confirmed } && !state.anyBusy,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { ErrorText(state.error) }
            items(state.cards, key = { it.itemId }) { card ->
                // 220ms fade + 6dp rise on enter.
                val rise = with(LocalDensity.current) { 6.dp.roundToPx() }
                val visible = remember { MutableTransitionState(false).apply { targetState = true } }
                AnimatedVisibility(visibleState = visible, enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { rise }) {
                    ConfirmCardView(card, vm, onOpenItem)
                }
            }
            item {
                Text(
                    "Nothing is priced until you confirm the identification. Change the name, model or condition afterwards and the price is fetched again.",
                    style = SnapType.fieldLabel.copy(lineHeight = 17.sp),
                    color = c.text.copy(alpha = 0.55f),
                )
            }
        }
    }
}

@Composable
private fun ConfirmCardView(card: ConfirmCard, vm: ConfirmViewModel, onOpenItem: (String) -> Unit) {
    val c = snapColors
    Column(Modifier.fillMaxWidth().background(c.surface).border(1.dp, c.divider)) {
        // Header, 14dp padding: 84dp cutout then the state-dependent column.
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            CutoutTile(File(card.photoPath), 84.dp)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                when {
                    card.identifying -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spinner()
                            Spacer(Modifier.width(8.dp))
                            MicroLabel("Identifying", alpha = 0.65f, style = SnapType.microLabel.copy(fontSize = 12.sp))
                        }
                        Skeleton(Modifier.fillMaxWidth().padding(top = 14.dp))
                        Skeleton(Modifier.fillMaxWidth(0.6f).padding(top = 8.dp), staggerMs = 200)
                    }
                    card.identifyError != null -> {
                        Text("Couldn't identify this one", style = SnapType.body.copy(fontWeight = FontWeight.ExtraBold), color = c.accent700)
                        Text("The model returned no confident match.", style = SnapType.bodySmall, color = c.text, modifier = Modifier.padding(top = 2.dp))
                        Text(card.identifyError, style = SnapType.fieldLabel.copy(fontSize = 11.sp), color = c.text.copy(alpha = 0.55f), modifier = Modifier.padding(top = 4.dp))
                        SecondaryButton("Retry", onClick = { vm.identify(card.itemId) }, minHeight = 40.dp, modifier = Modifier.padding(top = 10.dp))
                    }
                    else -> SnapTextField(card.name, { vm.editName(card.itemId, it) }, label = "Name", placeholder = "What is it?")
                }
            }
        }

        if (card.identified && !card.identifying) {
            Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SnapTextField(card.brand, { vm.editBrand(card.itemId, it) }, label = "Brand", modifier = Modifier.weight(1f))
                    SnapTextField(card.model, { vm.editModel(card.itemId, it) }, label = "Model", modifier = Modifier.weight(1f))
                }
                Column {
                    FieldLabel("Condition", Modifier.padding(bottom = 6.dp))
                    ConditionChipRow(card.condition, onSelect = { vm.editCondition(card.itemId, it) })
                }
                SnapTextField(
                    card.notes, { vm.editNotes(card.itemId, it) },
                    label = "Notes", placeholder = "Scratches, missing parts, pickup details", singleLine = false, minHeight = 64.dp,
                )
            }

            Rule(thickness = 2.dp)
            if (card.confirmed) {
                // Price block: SUGGESTED PRICE over the figure, Detail -> on the right; RE-PRICING while a request is in flight.
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        MicroLabel("Suggested price")
                        val q = card.quote
                        when {
                            card.pricing -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                                Spinner()
                                Spacer(Modifier.width(8.dp))
                                MicroLabel(if (q == null) "Pricing" else "Re-pricing", alpha = 0.65f, style = SnapType.microLabel.copy(fontSize = 12.sp))
                            }
                            q != null -> Text(q.suggestedPrice?.let(Money::usd) ?: "No comps", style = SnapType.cardPrice, color = c.text)
                            else -> ErrorText(card.priceError ?: "Not priced", Modifier.padding(top = 4.dp))
                        }
                    }
                    if (!card.pricing) {
                        if (card.quote != null) {
                            SecondaryButton(
                                "Detail", onClick = { onOpenItem(card.itemId) }, minHeight = 40.dp,
                                trailing = { LucideIcon(R.drawable.ic_lucide_chevron_right, null, size = 16.dp, tint = c.text) },
                            )
                        } else {
                            SecondaryButton("Retry", onClick = { vm.confirm(card.itemId) }, minHeight = 40.dp)
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Remove", onClick = { vm.remove(card.itemId) }, minHeight = 48.dp)
                    PrimaryButton("Confirm", onClick = { vm.confirm(card.itemId) }, enabled = !card.busy, minHeight = 48.dp, modifier = Modifier.weight(1f))
                }
            }
        } else if (card.identifyError != null) {
            Rule(thickness = 2.dp)
            Row(Modifier.fillMaxWidth().padding(14.dp)) {
                SecondaryButton("Remove", onClick = { vm.remove(card.itemId) }, minHeight = 48.dp)
            }
        }
    }
}
