package com.alexivanov.snapsell.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.alexivanov.snapsell.R

/** Archivo, bundled as static instances at 400 / 600 / 800. */
val Archivo: FontFamily = FontFamily(
    Font(R.font.archivo_regular, FontWeight.Normal),
    Font(R.font.archivo_semibold, FontWeight.SemiBold),
    Font(R.font.archivo_extrabold, FontWeight.ExtraBold),
)

private fun archivo(size: TextUnit, weight: FontWeight, tracking: TextUnit = TextUnit.Unspecified, lineHeight: TextUnit = TextUnit.Unspecified) =
    TextStyle(fontFamily = Archivo, fontSize = size, fontWeight = weight, letterSpacing = tracking, lineHeight = lineHeight)

/**
 * The named roles from the handoff's type table. Opacity is applied at the
 * use site (a colour, not a style), except where noted in the role's name.
 */
object SnapType {
    val displayPrice = archivo(46.sp, FontWeight.ExtraBold, (-0.03).em, 48.sp)
    val screenHero = archivo(30.sp, FontWeight.ExtraBold, (-0.025).em, 34.sp)
    val bundlePrice = archivo(34.sp, FontWeight.ExtraBold, (-0.02).em, 38.sp)
    val soldRange = archivo(30.sp, FontWeight.ExtraBold, (-0.02).em, 34.sp)
    val cardPrice = archivo(28.sp, FontWeight.ExtraBold, lineHeight = 32.sp)
    val sectionHead = archivo(21.sp, FontWeight.ExtraBold, lineHeight = 26.sp)
    val appBarTitle = archivo(18.sp, FontWeight.ExtraBold)
    val rowPrice = archivo(19.sp, FontWeight.ExtraBold)
    val statValue = archivo(20.sp, FontWeight.ExtraBold)
    val rowTitle = archivo(15.sp, FontWeight.ExtraBold, lineHeight = 20.sp)
    val tabLabel = archivo(14.sp, FontWeight.ExtraBold)
    val buttonLabel = archivo(15.sp, FontWeight.ExtraBold)
    val body = archivo(14.sp, FontWeight.Normal, lineHeight = 20.sp)
    val bodySmall = archivo(13.sp, FontWeight.Normal, lineHeight = 18.sp)
    val bodyLarge = archivo(15.sp, FontWeight.Normal, lineHeight = 22.sp)
    /** 12sp / 400; draw at 70% opacity. */
    val fieldLabel = archivo(12.sp, FontWeight.Normal)
    /** 10sp / 400 / +0.10em, UPPERCASE; draw at 72% opacity. */
    val microLabel = archivo(10.sp, FontWeight.Normal, 0.10.em)
    /** 9.5sp variant for the stat strip; draw at 55%. */
    val statLabel = archivo(9.5.sp, FontWeight.Normal, 0.09.em)
    /** 10.5sp / 400 / +0.08em, UPPERCASE. */
    val tag = archivo(10.5.sp, FontWeight.Normal, 0.08.em)
    val wordmark = archivo(46.sp, FontWeight.ExtraBold, (-0.035).em, 50.sp)
    val chipLabel = archivo(12.5.sp, FontWeight.Normal)
    val chipLabelSelected = archivo(12.5.sp, FontWeight.ExtraBold)
    val fieldValue = archivo(15.sp, FontWeight.Normal)
    val fieldValueBold = archivo(19.sp, FontWeight.ExtraBold)
}

/** M3 defaults re-cut in Archivo so anything not using [SnapType] still matches. */
val SnapTypography: Typography = Typography().let { d ->
    Typography(
        displayLarge = d.displayLarge.copy(fontFamily = Archivo),
        displayMedium = d.displayMedium.copy(fontFamily = Archivo),
        displaySmall = d.displaySmall.copy(fontFamily = Archivo),
        headlineLarge = d.headlineLarge.copy(fontFamily = Archivo),
        headlineMedium = d.headlineMedium.copy(fontFamily = Archivo),
        headlineSmall = d.headlineSmall.copy(fontFamily = Archivo),
        titleLarge = d.titleLarge.copy(fontFamily = Archivo, fontWeight = FontWeight.ExtraBold),
        titleMedium = d.titleMedium.copy(fontFamily = Archivo, fontWeight = FontWeight.ExtraBold),
        titleSmall = d.titleSmall.copy(fontFamily = Archivo, fontWeight = FontWeight.ExtraBold),
        bodyLarge = d.bodyLarge.copy(fontFamily = Archivo),
        bodyMedium = d.bodyMedium.copy(fontFamily = Archivo),
        bodySmall = d.bodySmall.copy(fontFamily = Archivo),
        labelLarge = d.labelLarge.copy(fontFamily = Archivo, fontWeight = FontWeight.ExtraBold),
        labelMedium = d.labelMedium.copy(fontFamily = Archivo),
        labelSmall = d.labelSmall.copy(fontFamily = Archivo),
    )
}
