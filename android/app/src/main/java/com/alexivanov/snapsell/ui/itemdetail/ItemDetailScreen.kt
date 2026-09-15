package com.alexivanov.snapsell.ui.itemdetail

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.alexivanov.snapsell.AppContainer
import com.alexivanov.snapsell.R
import com.alexivanov.snapsell.data.remote.dto.Comp
import com.alexivanov.snapsell.data.remote.dto.SoldEstimateSource
import com.alexivanov.snapsell.domain.Money
import com.alexivanov.snapsell.ui.common.CutoutTile
import com.alexivanov.snapsell.ui.common.ErrorText
import com.alexivanov.snapsell.ui.common.LoadingBox
import com.alexivanov.snapsell.ui.common.LucideIcon
import com.alexivanov.snapsell.ui.common.MicroLabel
import com.alexivanov.snapsell.ui.common.OutlinedTag
import com.alexivanov.snapsell.ui.common.PrimaryButton
import com.alexivanov.snapsell.ui.common.Rule
import com.alexivanov.snapsell.ui.common.SecondaryButton
import com.alexivanov.snapsell.ui.common.SnapBottomBar
import com.alexivanov.snapsell.ui.common.SnapTextField
import com.alexivanov.snapsell.ui.common.SnapTopBar
import com.alexivanov.snapsell.ui.common.Spinner
import com.alexivanov.snapsell.ui.common.VRule
import com.alexivanov.snapsell.ui.theme.SnapType
import com.alexivanov.snapsell.ui.theme.snapColors
import java.io.File

@Composable
fun ItemDetailScreen(
    container: AppContainer,
    itemId: String,
    onHandOff: (String) -> Unit,
    onAddToBundle: (String) -> Unit,
    onBack: () -> Unit,
) {
    val vm: ItemDetailViewModel = viewModel(key = itemId) { ItemDetailViewModel(container, itemId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = snapColors

    Scaffold(
        containerColor = c.bg,
        topBar = { SnapTopBar(title = "Item", onBack = onBack) },
        bottomBar = {
            val item = state.item
            if (item != null) {
                SnapBottomBar {
                    SecondaryButton("Add to bundle", onClick = { onAddToBundle(item.id) })
                    PrimaryButton("List on Marketplace", onClick = { vm.createListing(onHandOff) }, modifier = Modifier.weight(1f))
                }
            }
        },
    ) { padding ->
        val item = state.item
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        if (item == null) {
            Column(Modifier.padding(padding).padding(24.dp)) { Text("This item no longer exists.", style = SnapType.body, color = c.text) }
            return@Scaffold
        }
        val quote = item.quote

        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            // 190dp white cutout band with a 2dp bottom divider.
            Box(Modifier.fillMaxWidth().height(190.dp).background(c.cutoutWhite), contentAlignment = Alignment.Center) {
                AsyncImage(model = File(item.photoPath), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(12.dp))
            }
            Rule(thickness = 2.dp)

            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(item.name.ifBlank { "Unidentified item" }, style = SnapType.sectionHead, color = c.text, modifier = Modifier.padding(top = 16.dp))
                Text(
                    listOfNotNull(item.brand, item.model, item.condition.label).joinToString(" · "),
                    style = SnapType.fieldLabel,
                    color = c.text.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp),
                )

                // Price row, baseline-aligned: SUGGESTED over the 46sp figure, Final price field beside it.
                Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        MicroLabel("Suggested")
                        val suggested = quote?.suggestedPrice
                        Text(
                            when {
                                suggested != null -> Money.usd(suggested)
                                quote != null -> "No comps"
                                else -> "—"
                            },
                            style = SnapType.displayPrice,
                            color = c.text,
                        )
                    }
                    SnapTextField(
                        value = state.priceText,
                        onValueChange = vm::editPrice,
                        label = "Final price",
                        placeholder = quote?.suggestedPrice?.let(Money::plain) ?: "0",
                        prefix = "$",
                        minHeight = 48.dp,
                        textStyle = SnapType.fieldValueBold,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(126.dp),
                    )
                }
                ErrorText(state.error, Modifier.padding(top = 8.dp))
                if (quote == null) {
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        SecondaryButton("Get a price", onClick = vm::reprice, enabled = !state.pricing, minHeight = 44.dp)
                        if (state.pricing) { Spacer(Modifier.width(12.dp)); Spinner() }
                    }
                }
            }

            if (quote != null) {
                // Stat strip: 2dp top rule, 1dp bottom rule, cells divided by 1dp verticals.
                Column(Modifier.padding(top = 20.dp)) {
                    Rule(thickness = 2.dp)
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        StatCell("Asking median", quote.askingMedian, Modifier.weight(1f))
                        VRule()
                        StatCell("Low", quote.askingLow, Modifier.weight(1f))
                        VRule()
                        StatCell("High", quote.askingHigh, Modifier.weight(1f))
                    }
                    Rule(thickness = 1.dp)
                }

                Column(Modifier.padding(horizontal = 16.dp)) {
                    val cond = item.condition.label.lowercase()
                    Text(
                        "From ${quote.compCount} active ${if (quote.conditionFiltered) "$cond " else ""}listings · local factor ${(quote.localSaleFactor * 100).toInt()}%",
                        style = SnapType.fieldLabel.copy(fontSize = 11.5.sp),
                        color = c.text.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    if (!quote.conditionFiltered) {
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp).background(c.surface)) {
                            Box(Modifier.width(3.dp).height(IntrinsicSize.Min).fillMaxSize().background(c.accent))
                            Text(
                                "No $cond-condition comps were found, so these ${quote.compCount} use every condition. Treat the number as rough.",
                                style = SnapType.bodySmall,
                                color = c.text,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        }
                    }

                    if (quote.comps.isNotEmpty()) {
                        MicroLabel("Comparable listings", Modifier.padding(top = 24.dp, bottom = 8.dp))
                        Rule(thickness = 2.dp)
                        quote.comps.forEach { comp ->
                            CompRow(comp) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, comp.url.toUri())) } }
                            Rule(thickness = 1.dp)
                        }
                    }

                    quote.estimatedSold?.let { est ->
                        Column(Modifier.fillMaxWidth().padding(top = 24.dp).border(2.dp, c.text).padding(16.dp)) {
                            MicroLabel("Estimated sold range")
                            Text("${Money.usd(est.low)}–${Money.usd(est.high)}", style = SnapType.soldRange, color = c.text, modifier = Modifier.padding(top = 4.dp))
                            val isLlm = est.source == SoldEstimateSource.LLM_ESTIMATE
                            OutlinedTag(
                                if (isLlm) "Estimate, not sales data" else "Source: ${est.source.name.lowercase().replace('_', ' ')}",
                                Modifier.padding(top = 10.dp),
                            )
                            Text(
                                if (isLlm) {
                                    "Modelled down from what sellers are asking. eBay does not publish completed-sale prices through the API."
                                } else {
                                    est.rationale ?: "Reported by ${est.source.name.lowercase().replace('_', ' ')}."
                                },
                                style = SnapType.bodySmall,
                                color = c.text.copy(alpha = 0.8f),
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatCell(label: String, value: Double?, modifier: Modifier) {
    val c = snapColors
    Column(modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        MicroLabel(label, alpha = 0.55f, style = SnapType.statLabel)
        Text(value?.let(Money::usd) ?: "—", style = SnapType.statValue, color = c.text, modifier = Modifier.padding(top = 4.dp))
    }
}

/** 46dp thumbnail · two-line title + condition · TOTAL on the right · external-link icon. */
@Composable
private fun CompRow(comp: Comp, onClick: () -> Unit) {
    val c = snapColors
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (comp.imageUrl != null) {
            CutoutTile(comp.imageUrl, 46.dp, contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.size(46.dp).background(c.neutral300))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(comp.title, style = SnapType.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 16.sp), color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            comp.condition?.let { Text(it, style = SnapType.fieldLabel.copy(fontSize = 11.sp), color = c.text.copy(alpha = 0.55f), modifier = Modifier.padding(top = 2.dp)) }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Money.usd(comp.total), style = SnapType.rowTitle, color = c.text, textAlign = TextAlign.End)
            MicroLabel("Total")
        }
        LucideIcon(R.drawable.ic_lucide_external_link, "Open listing", Modifier.padding(start = 12.dp), size = 16.dp, tint = c.text.copy(alpha = 0.7f))
    }
}
