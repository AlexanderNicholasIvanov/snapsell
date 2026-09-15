package com.alexivanov.snapsell.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alexivanov.snapsell.R
import com.alexivanov.snapsell.domain.Condition
import com.alexivanov.snapsell.domain.ListingStatus
import com.alexivanov.snapsell.ui.theme.SnapType
import com.alexivanov.snapsell.ui.theme.snapColors

// ---------------------------------------------------------------- icons

/** A Lucide vector drawable, tinted at the use site (the XML strokes are black). */
@Composable
fun LucideIcon(@DrawableRes id: Int, contentDescription: String?, modifier: Modifier = Modifier, size: Dp = 20.dp, tint: Color = snapColors.text) {
    Icon(painterResource(id), contentDescription = contentDescription, tint = tint, modifier = modifier.size(size))
}

/** A square touch box holding one icon. */
@Composable
fun IconBox(@DrawableRes id: Int, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, boxSize: Dp = 48.dp, iconSize: Dp = 21.dp, tint: Color = snapColors.text) {
    Box(modifier.size(boxSize).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        LucideIcon(id, contentDescription, size = iconSize, tint = tint)
    }
}

// ---------------------------------------------------------------- rules

/** A horizontal rule in divider colour. */
@Composable
fun Rule(modifier: Modifier = Modifier, thickness: Dp = 1.dp, color: Color = snapColors.divider) {
    Box(modifier.fillMaxWidth().height(thickness).background(color))
}

/** A vertical rule in divider colour; give it a height or put it in a Row with IntrinsicSize. */
@Composable
fun VRule(modifier: Modifier = Modifier, thickness: Dp = 1.dp, color: Color = snapColors.divider) {
    Box(modifier.width(thickness).fillMaxHeight().background(color))
}

// ---------------------------------------------------------------- bars

/**
 * Top app bar: 56dp, 2dp bottom divider, 48dp back box with a 22dp arrow,
 * title flush left at weight 1f, trailing 48dp icon boxes.
 */
@Composable
fun SnapTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    titleStyle: TextStyle = SnapType.appBarTitle,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = snapColors
    Column(Modifier.fillMaxWidth().background(c.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconBox(R.drawable.ic_lucide_arrow_left, "Back", onBack, iconSize = 22.dp)
            }
            Text(
                title,
                style = titleStyle,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = if (onBack == null) 16.dp else 4.dp, end = 8.dp),
            )
            trailing()
        }
        Rule(thickness = 2.dp)
    }
}

/** Pinned bottom bar: 2dp top divider, padding 12 / 16 / 16, 8dp gap between children. */
@Composable
fun SnapBottomBar(content: @Composable RowScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(snapColors.bg)) {
        Rule(thickness = 2.dp)
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

// ---------------------------------------------------------------- buttons

private val ButtonPadding = PaddingValues(horizontal = 16.dp)

/**
 * Accent fill, bg-coloured label, flush left. The label starts at the left
 * padding edge no matter how wide the button is.
 */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = 54.dp,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = snapColors
    Row(
        modifier
            .defaultMinSize(minHeight = minHeight)
            .background(c.accent)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(ButtonPadding),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Box(Modifier.width(12.dp))
        }
        Text(label, style = SnapType.buttonLabel, color = c.bg, modifier = Modifier.weight(1f, fill = false))
        if (trailing != null) {
            Box(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** 1dp divider outline, ink label, flush left. */
@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = 54.dp,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = snapColors
    Row(
        modifier
            .defaultMinSize(minHeight = minHeight)
            .border(1.dp, c.divider)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(ButtonPadding),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Box(Modifier.width(12.dp))
        }
        Text(label, style = SnapType.buttonLabel, color = c.text, modifier = Modifier.weight(1f, fill = false))
        if (trailing != null) {
            Box(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** No fill, no border, ink label, flush left. */
@Composable
fun GhostButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = 44.dp,
    color: Color = snapColors.text,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .defaultMinSize(minHeight = minHeight)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Box(Modifier.width(10.dp))
        }
        Text(label, style = SnapType.buttonLabel, color = color)
    }
}

// ---------------------------------------------------------------- labels & chips

/** 10sp uppercase +0.10em at 72% ink. */
@Composable
fun MicroLabel(text: String, modifier: Modifier = Modifier, alpha: Float = 0.72f, style: TextStyle = SnapType.microLabel, color: Color = snapColors.text) {
    Text(text.uppercase(), style = style, color = color.copy(alpha = alpha), modifier = modifier, maxLines = 1)
}

/** 12sp field label at 70%. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = SnapType.fieldLabel, color = snapColors.text.copy(alpha = 0.70f), modifier = modifier)
}

@Composable
fun StatusChip(status: ListingStatus, modifier: Modifier = Modifier) {
    val c = snapColors
    val base = modifier
    val (boxModifier, textColor) = when (status) {
        ListingStatus.DRAFT -> base.background(c.neutral100) to c.neutral800
        ListingStatus.LISTED -> base.background(c.accent100) to c.accent800
        ListingStatus.SKIPPED, ListingStatus.SOLD -> base.border(1.dp, c.accent) to c.text
    }
    Box(boxModifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(status.label.uppercase(), style = SnapType.tag, color = textColor, maxLines = 1)
    }
}

/** An outlined tag ("ESTIMATE, NOT SALES DATA"). */
@Composable
fun OutlinedTag(text: String, modifier: Modifier = Modifier, color: Color = snapColors.text) {
    Box(modifier.border(1.dp, color).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text.uppercase(), style = SnapType.tag, color = color, maxLines = 1)
    }
}

/** Five condition chips, always all visible; wraps to a second row on narrow screens. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConditionChipRow(selected: Condition, onSelect: (Condition) -> Unit, modifier: Modifier = Modifier) {
    val c = snapColors
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Condition.entries.forEach { cond ->
            val on = cond == selected
            Box(
                Modifier
                    .defaultMinSize(minHeight = 40.dp)
                    .then(if (on) Modifier.background(c.accent) else Modifier.border(1.5.dp, c.divider))
                    .clickable { onSelect(cond) }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    cond.label,
                    style = if (on) SnapType.chipLabelSelected else SnapType.chipLabel,
                    color = if (on) c.bg else c.text,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- tiles & fields

/** A cutout on pure white with a 1dp divider border, in both themes. */
@Composable
fun CutoutTile(model: Any?, size: Dp, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit) {
    val c = snapColors
    Box(modifier.size(size).background(c.cutoutWhite).border(1.dp, c.divider), contentAlignment = Alignment.Center) {
        if (model != null) {
            AsyncImage(model = model, contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize().padding(1.dp))
        }
    }
}

/**
 * Text field: surface fill, 1dp divider border, 2dp accent border on focus,
 * label above at 12sp / 70%. No M3 indicator line.
 */
@Composable
fun SnapTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minHeight: Dp = 44.dp,
    textStyle: TextStyle = SnapType.fieldValue,
    textAlign: TextAlign = TextAlign.Start,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    prefix: String? = null,
    contentAlpha: Float = 1f,
) {
    val c = snapColors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier) {
        if (label != null) FieldLabel(label, Modifier.padding(bottom = 6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            enabled = enabled,
            readOnly = readOnly,
            keyboardOptions = keyboardOptions,
            interactionSource = interaction,
            textStyle = textStyle.copy(color = c.text.copy(alpha = contentAlpha), textAlign = textAlign),
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = minHeight)
                        .background(c.surface)
                        .then(if (focused) Modifier.border(2.dp, c.accent) else Modifier.border(1.dp, c.divider))
                        .padding(horizontal = 12.dp, vertical = if (singleLine) 0.dp else 10.dp),
                    contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (prefix != null) {
                            Text(prefix, style = textStyle, color = c.text.copy(alpha = 0.6f * contentAlpha))
                        }
                        Box(Modifier.weight(1f)) {
                            if (value.isEmpty() && placeholder != null) {
                                Text(placeholder, style = textStyle, color = c.text.copy(alpha = 0.45f), textAlign = textAlign, modifier = Modifier.fillMaxWidth())
                            }
                            inner()
                        }
                    }
                }
            },
        )
    }
}

/** 24dp square checkbox: accent fill + bg tick when on, 1.5dp divider border when off. */
@Composable
fun SquareCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = snapColors
    Box(
        modifier
            .size(24.dp)
            .then(if (checked) Modifier.background(c.accent) else Modifier.border(1.5.dp, c.divider))
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) LucideIcon(R.drawable.ic_lucide_check, null, size = 16.dp, tint = c.bg)
    }
}

// ---------------------------------------------------------------- motion

/** Skeleton bar: divider colour pulsing 0.35 -> 0.8 over 1200ms, ease-in-out, with a stagger offset. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, height: Dp = 13.dp, staggerMs: Int = 0) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = androidx.compose.animation.core.EaseInOut),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(staggerMs),
        ),
        label = "skeletonAlpha",
    )
    Box(modifier.height(height).alpha(alpha).background(snapColors.divider))
}

/** 16dp accent arc rotating once per 700ms, linear. */
@Composable
fun Spinner(modifier: Modifier = Modifier, size: Dp = 16.dp, color: Color = snapColors.accent) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing)),
        label = "spinnerAngle",
    )
    Canvas(modifier.size(size).rotate(angle)) {
        val stroke = 2.dp.toPx()
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(this.size.width - stroke, this.size.height - stroke),
            style = Stroke(width = stroke),
        )
    }
}

// ---------------------------------------------------------------- misc

@Composable
fun LoadingBox(modifier: Modifier = Modifier, label: String? = null) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spinner()
            if (label != null) {
                Box(Modifier.width(10.dp))
                MicroLabel(label, alpha = 0.65f, style = SnapType.fieldLabel)
            }
        }
    }
}

@Composable
fun ErrorText(message: String?, modifier: Modifier = Modifier) {
    if (!message.isNullOrBlank()) {
        Text(message, color = snapColors.accent700, style = SnapType.bodySmall, modifier = modifier)
    }
}

/** Heavy body text helper used by hero copy. */
@Composable
fun BodyText(text: String, modifier: Modifier = Modifier, alpha: Float = 1f, style: TextStyle = SnapType.body, weight: FontWeight? = null, maxLines: Int = Int.MAX_VALUE) {
    Text(text, style = if (weight != null) style.copy(fontWeight = weight) else style, color = snapColors.text.copy(alpha = alpha), modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** Fills the parent Box; used to anchor overlays. */
@Composable
fun BoxScope.Scrim(color: Color = snapColors.scrim, onClick: (() -> Unit)? = null) {
    Box(
        Modifier
            .matchParentSize()
            .background(color)
            .then(if (onClick != null) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick) else Modifier),
    )
}

/**
 * Modernist slider: a 1dp divider track with a 3dp accent active segment and
 * a 20dp square accent thumb. Built on M3 Slider's state machinery so touch
 * targets and accessibility come for free.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SnapSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val c = snapColors
    androidx.compose.material3.Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier.fillMaxWidth().height(32.dp),
        thumb = { Box(Modifier.size(20.dp).background(c.accent)) },
        track = { state ->
            val fraction = ((state.value - state.valueRange.start) / (state.valueRange.endInclusive - state.valueRange.start)).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(3.dp)) {
                Box(Modifier.fillMaxWidth().height(1.dp).align(Alignment.Center).background(c.divider))
                Box(Modifier.fillMaxWidth(fraction).height(3.dp).background(c.accent))
            }
        },
    )
}
