package pk.oneclick.app

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------- Tokens (calm, map-first: one saturated brand colour, ink text, warm greys) ----------------
@Immutable
data class DnaColors(
    val bgPrimary: Color, val bgSecondary: Color, val bgTertiary: Color, val brand: Color, val onBrand: Color,
    val accent: Color, val control: Color, val onControl: Color, val surface: Color, val textPrimary: Color,
    val textSecondary: Color, val textDisabled: Color, val success: Color, val warning: Color, val error: Color,
    val dark: Boolean,
)

val LightDna = DnaColors(
    bgPrimary = Color(0xFFFFFFFF), bgSecondary = Color(0xFFF2F1EB), bgTertiary = Color(0xFFDFDEDB),
    brand = Color(0xFFC1F11D), onBrand = Color(0xFF181818), accent = Color(0xFF3F62FF),
    control = Color(0xFF181818), onControl = Color.White, surface = Color.White, textPrimary = Color(0xFF181818),
    textSecondary = Color(0xFF6C6C6C), textDisabled = Color(0xFFB2B1A9), success = Color(0xFF04973C),
    warning = Color(0xFFFFC13C), error = Color(0xFFFE003B), dark = false,
)
val DarkDna = DnaColors(
    bgPrimary = Color(0xFF1C1C1C), bgSecondary = Color(0xFF303030), bgTertiary = Color(0xFF7A7978),
    brand = Color(0xFFC1F11D), onBrand = Color(0xFF181818), accent = Color(0xFF4980FF),
    control = Color(0xFFC1F11D), onControl = Color(0xFF181818), surface = Color(0xFF242424), textPrimary = Color.White,
    textSecondary = Color(0xFFB2B1A9), textDisabled = Color(0xFF535353), success = Color(0xFFC1F11D),
    warning = Color(0xFFFF9900), error = Color(0xFFFF7074), dark = true,
)
val LocalDna = staticCompositionLocalOf { LightDna }
val dna: DnaColors @Composable get() = LocalDna.current

object DnaShape {
    val xs = RoundedCornerShape(8.dp); val s = RoundedCornerShape(12.dp); val m = RoundedCornerShape(16.dp)
    val l = RoundedCornerShape(20.dp); val sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
}
object DnaMotion {
    val standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    const val follow = 150; const val fade = 200; const val popup = 300; const val nav = 350
}

// Two weights only: Book for everything, Semibold for emphasis. Wide display face only for heroes.
val Body = FontFamily(Font(R.font.inter_book, FontWeight.Normal), Font(R.font.inter_semibold, FontWeight.SemiBold))
val Display = FontFamily(Font(R.font.archivo_expanded_bold, FontWeight.Bold))

object DnaType {
    val largeNumbers = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 72.sp, lineHeight = 72.sp)
    val heading1 = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = 0.5.sp)
    val heading2 = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = 0.4.sp)
    val heading3 = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp)
    val heading4 = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 20.sp)
    val bodyParagraph = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)
    val mainBody = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 20.sp)
    val compactBody = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 16.sp)
    val caption = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)
}

@Composable
fun OneClickTheme(content: @Composable () -> Unit) {
    val c = if (isSystemInDarkTheme()) DarkDna else LightDna
    val scheme = (if (c.dark) darkColorScheme() else lightColorScheme()).copy(
        primary = c.control, onPrimary = c.onControl, background = c.bgPrimary, surface = c.surface,
        onSurface = c.textPrimary, onBackground = c.textPrimary, surfaceVariant = c.bgSecondary,
        onSurfaceVariant = c.textSecondary, error = c.error, outline = c.bgTertiary,
    )
    val type = Typography(
        bodyLarge = DnaType.mainBody, bodyMedium = DnaType.compactBody, bodySmall = DnaType.caption,
        titleLarge = DnaType.heading3, titleMedium = DnaType.heading4, labelLarge = DnaType.heading4.copy(fontSize = 16.sp),
    )
    CompositionLocalProvider(LocalDna provides c) {
        MaterialTheme(colorScheme = scheme, typography = type) {
            CompositionLocalProvider(LocalContentColor provides c.textPrimary, LocalTextStyle provides DnaType.mainBody, content = content)
        }
    }
}

// ---------------- Components ----------------
enum class BtnKind { Primary, Secondary, Accent, Inverse, ErrorLow, ErrorHigh, Tertiary }
enum class BtnSize(val height: Dp, val text: Int) { L(56.dp, 20), M(48.dp, 16), S(40.dp, 16) }

/** Flat 12dp button, no elevation, single line. Spinner replaces the label while loading (no layout jump). */
@Composable
fun DnaButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, kind: BtnKind = BtnKind.Primary,
    size: BtnSize = BtnSize.L, enabled: Boolean = true, loading: Boolean = false, icon: ImageVector? = null,
    progress: Float? = null,
) {
    val c = dna
    val (bg, fg) = when (kind) {
        BtnKind.Primary -> c.brand to c.onBrand
        BtnKind.Secondary -> c.bgSecondary to c.textPrimary
        BtnKind.Accent -> c.accent to Color.White
        BtnKind.Inverse -> c.control to c.onControl
        BtnKind.ErrorLow -> c.bgSecondary to c.error
        BtnKind.ErrorHigh -> c.error to Color.White
        BtnKind.Tertiary -> Color.White to Color(0xFF181818)
    }
    Surface(
        onClick = onClick, enabled = enabled && !loading, shape = DnaShape.s,
        color = if (enabled) bg else c.bgSecondary, contentColor = if (enabled) fg else c.textDisabled,
        modifier = modifier.height(size.height),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (progress != null && enabled) {
                // Progress button: the fill sweeps away as time runs out.
                Box(Modifier.fillMaxHeight().fillMaxWidth().background(bg.copy(alpha = .55f)))
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(bg).align(Alignment.CenterStart))
            }
            if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = fg, strokeWidth = 2.dp)
            else Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
                Text(text, style = DnaType.heading4.copy(fontSize = size.text.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Floating oval map control: 56dp, white, elevation 6. */
@Composable
fun RoundButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 56.dp, floating: Boolean = true, tint: Color? = null) {
    val c = dna
    Surface(onClick = onClick, shape = CircleShape, color = if (floating) c.surface else c.bgSecondary,
        shadowElevation = if (floating) 6.dp else 0.dp, modifier = modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, Modifier.size(24.dp), tint = tint ?: c.textPrimary) }
    }
}

/** The one sheet that holds the task: 24dp top radius, elevation 16. */
@Composable
fun Sheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = DnaShape.sheet, color = dna.surface, shadowElevation = 16.dp) {
        Column(Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            .animateContentSize(tween(DnaMotion.fade, easing = DnaMotion.standard))) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(32.dp, 4.dp).clip(CircleShape).background(dna.bgTertiary))
            }
            content()
        }
    }
}

/** List row: 16dp padding, 24dp start icon, title 16 Book, subtitle 14 secondary, max 3 lines. No dividers. */
@Composable
fun Cell(title: String, modifier: Modifier = Modifier, subtitle: String? = null, icon: ImageVector? = null,
         lead: (@Composable () -> Unit)? = null, end: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null,
         titleStyle: TextStyle = DnaType.mainBody) {
    val c = dna
    val body: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, Modifier.size(24.dp), tint = c.textPrimary); Spacer(Modifier.width(16.dp)) }
            if (lead != null) { lead(); Spacer(Modifier.width(12.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, style = titleStyle, color = c.textPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = DnaType.compactBody, color = c.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
            if (end != null) { Spacer(Modifier.width(8.dp)); end() }
        }
    }
    if (onClick != null) Surface(onClick = onClick, color = Color.Transparent, shape = DnaShape.s, modifier = modifier) { body() }
    else Box(modifier) { body() }
}

enum class AlertKind { Info, Warning, Error, Brand, Success }

@Composable
fun InlineAlert(text: String, kind: AlertKind = AlertKind.Info, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    val c = dna
    val tint = when (kind) { AlertKind.Info -> c.accent; AlertKind.Warning -> c.warning; AlertKind.Error -> c.error; AlertKind.Brand -> c.brand; AlertKind.Success -> c.success }
    Row(modifier.fillMaxWidth().clip(DnaShape.s).background(tint.copy(alpha = if (c.dark) .22f else .14f)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = DnaType.compactBody, color = c.textPrimary, modifier = Modifier.weight(1f))
        if (action != null) Text(action, style = DnaType.compactBody.copy(fontWeight = FontWeight.SemiBold), color = c.accent,
            modifier = Modifier.clip(DnaShape.xs).clickableNoIndication(onAction).padding(start = 8.dp))
    }
}

/** Initials avatar: light tint background, medium-tone glyph. */
@Composable
fun Avatar(name: String, size: Dp = 48.dp) {
    val c = dna
    Box(Modifier.size(size).clip(CircleShape).background(c.accent.copy(alpha = if (c.dark) .3f else .12f)), contentAlignment = Alignment.Center) {
        Text(name.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString(""), style = DnaType.heading4.copy(fontSize = (size.value * .36f).sp), color = c.accent)
    }
}

@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null) {
    val c = dna
    Surface(onClick = onClick, shape = CircleShape, color = if (selected) c.control else c.bgSecondary,
        contentColor = if (selected) c.onControl else c.textPrimary) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
            Text(text, style = DnaType.compactBody.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

/** Input: min 48dp, radius 8, warm grey fill; 2dp stroke only on focus. */
@Composable
fun DnaField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, label: String? = null, number: Boolean = false) {
    val c = dna
    var focused by remember { mutableStateOf(false) }
    Column(modifier) {
        if (label != null) { Text(label, style = DnaType.compactBody, color = c.textSecondary); Spacer(Modifier.height(8.dp)) }
        BasicTextField(value, onChange, singleLine = true, textStyle = DnaType.mainBody.copy(color = c.textPrimary), cursorBrush = SolidColor(c.textPrimary),
            keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
            modifier = Modifier.fillMaxWidth().onFocus { focused = it },
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(DnaShape.xs).background(c.bgSecondary)
                    .then(if (focused) Modifier.borderDna(c.textPrimary) else Modifier).padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, style = DnaType.mainBody, color = c.textSecondary)
                    inner()
                }
            })
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, style = DnaType.heading3, color = dna.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = modifier)

@Composable
fun Helper(text: String, modifier: Modifier = Modifier) =
    Text(text, style = DnaType.compactBody, color = dna.textSecondary, maxLines = 3, modifier = modifier)

@Composable
fun Tag(text: String, color: Color) {
    Text(text, style = DnaType.caption.copy(fontWeight = FontWeight.SemiBold), color = color,
        modifier = Modifier.clip(DnaShape.xs).background(color.copy(alpha = .14f)).padding(horizontal = 8.dp, vertical = 4.dp))
}

@Composable
fun CountdownBar(fraction: Float, modifier: Modifier = Modifier, color: Color = dna.textPrimary) {
    Box(modifier.fillMaxWidth().height(3.dp).background(dna.bgSecondary)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(color))
    }
}
