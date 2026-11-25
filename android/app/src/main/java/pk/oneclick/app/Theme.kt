package pk.oneclick.app

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * One Click design system. Structure follows a calm-fintech DNA (semantic colour roles, one bright accent,
 * Inter in two working weights, pill buttons, 16dp surfaces, floating sheets, short "you" copy).
 * Brand is One Click's own: gold accent, deep-emerald ink, emerald "forest" theme for brand moments.
 * Never pick a hex in a screen — pick a role from [OC].
 */
object OC {
    // content
    val ink = Color(0xFF0E1512)            // content.primary
    val text = Color(0xFF454A47)           // content.secondary
    val muted = Color(0xFF6A6E6B)          // content.tertiary
    val link = Color(0xFF0B3D2E)           // content.link
    // interactive
    val deep = Color(0xFF0B3D2E)           // interactive.primary + control (text on the accent)
    val gold = Color(0xFFE8B84A)           // interactive.accent — the one bright thing per screen
    val goldSoft = Color(0xFFFBF1DA)       // interactive.neutral — secondary button fill
    val inactive = Color(0xFF868685)       // interactive.secondary
    // background
    val bg = Color(0xFFFFFFFF)             // background.screen
    val card = Color(0xFFEDF0EE)           // background.neutral (brand ink at ~7.5%)
    val cardStrong = Color(0xFFE1E6E3)     // background.overlay
    val line = Color(0xFFE2E3E2)           // border.neutral (ink at 12%)
    val scrim = Color(0x800E1512)
    // forest theme (brand moments: balance, splash, success)
    val forest = Color(0xFF0B3D2E)
    val forestElevated = Color(0xFF134C3A)
    val forestText = Color(0xFFE8EBE6)
    val forestMuted = Color(0xFFC9D3CE)
    // sentiments: content / surface
    val negative = Color(0xFFCB272F); val negativeSurface = Color(0xFFFBEAEA)
    val positive = Color(0xFF054D28); val positiveSurface = Color(0xFFE2F6D5)
    val warning = Color(0xFF4A3B1C); val warningFill = Color(0xFFFFD11A); val warningSurface = Color(0xFFFFF7D7)
    val proposition = Color(0xFF054D4D); val propositionSurface = Color(0xFFE0F7F7)
    // kept names used across files
    val green = positive
    val red = negative
}

/** Spacing by relationship; scales with the user's font size so the rhythm never collapses. */
object Gap {
    @Composable private fun s(v: Int): Dp = (v * LocalDensity.current.fontScale).dp
    val betweenText @Composable get() = s(8)
    val textToComponent @Composable get() = s(16)
    val component @Composable get() = s(16)
    val contentToButton @Composable get() = s(24)
    val sections @Composable get() = s(32)
    val cards @Composable get() = s(12)
    val chips @Composable get() = s(8)
    val gutter = 16.dp
}
object Space { val xs = 4.dp; val sm = 8.dp; val md = 12.dp; val lg = 16.dp; val xl = 24.dp; val xxl = 32.dp }
object Radius {
    val sm = RoundedCornerShape(10.dp); val md = RoundedCornerShape(16.dp); val lg = RoundedCornerShape(16.dp)
    val xl = RoundedCornerShape(24.dp); val xxl = RoundedCornerShape(32.dp)
    val sheet = RoundedCornerShape(32.dp)
}
object Motion {
    val standard = CubicBezierEasing(0.25f, 0f, 0.5f, 1f)
    val enter = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)
    val exit = CubicBezierEasing(0.9f, 0f, 0.7f, 1f)
    val overshoot = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)   // money moments only
    val launch = CubicBezierEasing(0.8f, 0f, 1f, 1f)
    val emphasized = enter
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal), Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold), Font(R.font.inter_bold, FontWeight.Bold),
)
/** Heavy display face, used in ALL CAPS once per hero moment — never in lists. */
val DisplayFace = FontFamily(Font(R.font.archivo_black, FontWeight.Normal))

/** Type ramp. Two working weights: Regular for reading, SemiBold for anything to be found. */
object T {
    private fun inter(size: Int, line: Double, w: FontWeight, track: Double, color: Color = OC.ink) =
        TextStyle(fontFamily = Inter, fontWeight = w, fontSize = size.sp, lineHeight = line.sp, letterSpacing = track.em, color = color)
    val screenTitle = inter(30, 34.5, FontWeight.SemiBold, -0.021)
    val sectionTitle = inter(26, 31.7, FontWeight.SemiBold, -0.02)
    val subsectionTitle = inter(22, 27.5, FontWeight.SemiBold, -0.018)
    val bodyTitle = inter(18, 24.3, FontWeight.SemiBold, -0.014)
    val groupTitle = inter(14, 19.6, FontWeight.Medium, -0.006)
    val largeBody = inter(16, 24.0, FontWeight.Normal, -0.011, OC.text)
    val defaultBody = inter(14, 21.7, FontWeight.Normal, -0.006, OC.text)
    val largeBodyBold = inter(16, 24.0, FontWeight.SemiBold, -0.011)
    val bodyBold = inter(14, 21.7, FontWeight.SemiBold, -0.006)
    val link = inter(14, 22.0, FontWeight.SemiBold, -0.006, OC.link).copy(textDecoration = TextDecoration.Underline)
    val largeLink = inter(16, 24.0, FontWeight.SemiBold, -0.011, OC.link).copy(textDecoration = TextDecoration.Underline)
    val nav = inter(12, 18.0, FontWeight.Normal, 0.0, OC.muted)
    val navBold = inter(12, 18.0, FontWeight.SemiBold, 0.0)
    val number = inter(40, 44.0, FontWeight.SemiBold, -0.022)
    val displayMedium = TextStyle(fontFamily = DisplayFace, fontSize = 52.sp, lineHeight = 44.sp, color = OC.ink)
    val displaySmall = TextStyle(fontFamily = DisplayFace, fontSize = 40.sp, lineHeight = 34.sp, color = OC.ink)
    // aliases kept for older call sites
    val title = subsectionTitle
    val h1 = screenTitle
    val h2 = bodyTitle
    val body = largeBody
    val small = defaultBody
    val tiny = inter(13, 18.0, FontWeight.Normal, 0.0, OC.muted)
    val label = bodyBold
    val display = displaySmall
}

@Composable
fun OneClickTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = OC.deep, onPrimary = Color.White, secondary = OC.gold, background = OC.bg, surface = OC.bg,
        onSurface = OC.ink, onBackground = OC.ink, surfaceVariant = OC.card, onSurfaceVariant = OC.muted,
        error = OC.negative, outline = OC.line,
    )
    MaterialTheme(colorScheme = scheme, typography = Typography(bodyLarge = T.largeBody, bodyMedium = T.defaultBody, labelLarge = T.largeBodyBold)) {
        CompositionLocalProvider(LocalContentColor provides OC.ink, LocalTextStyle provides T.largeBody, content = content)
    }
}

fun Modifier.tap(onClick: () -> Unit) = composed { clickable(remember { MutableInteractionSource() }, null, onClick = onClick) }

// ---------------- Layout ----------------

/**
 * The default screen: 72dp app bar (round back button), left-aligned title ≤ 7 words, ≤ 1 supporting line,
 * one main block, and an optional sticky footer for the primary action.
 */
@Composable
fun Screen(
    title: String? = null, subtitle: String? = null, onBack: (() -> Unit)? = null, closeIcon: Boolean = false,
    right: (@Composable () -> Unit)? = null, bottomPad: Dp = 96.dp, footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(OC.bg).statusBarsPadding()) {
        if (onBack != null || right != null) Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = Gap.gutter),
            verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconCircle(if (closeIcon) Icons.Outlined.Close else Icons.Outlined.ArrowBack, if (closeIcon) "Close" else "Back", onBack)
            Spacer(Modifier.weight(1f))
            right?.invoke()
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Gap.gutter)
            .padding(top = if (onBack == null && right == null) Space.xl else 0.dp, bottom = if (footer == null) bottomPad else Space.lg)) {
            if (title != null) {
                Text(title, style = T.screenTitle)
                if (subtitle != null) { Spacer(Modifier.height(Gap.betweenText)); Text(subtitle, style = T.largeBody) }
                Spacer(Modifier.height(Gap.sections))
            }
            Column(verticalArrangement = Arrangement.spacedBy(Gap.component)) { content() }
        }
        if (footer != null) Column(Modifier.fillMaxWidth().background(OC.bg).navigationBarsPadding()
            .padding(horizontal = Gap.gutter).padding(top = Space.sm, bottom = Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm), content = footer)
    }
}

/** Round 40dp icon button on the neutral tint (back, close, app-bar actions). */
@Composable
fun IconCircle(icon: ImageVector, description: String, onClick: () -> Unit, size: Dp = 40.dp, onDark: Boolean = false, tint: Color? = null) {
    Surface(onClick = onClick, shape = CircleShape, color = if (onDark) Color.White.copy(alpha = .14f) else OC.card, modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, Modifier.size(24.dp), tint = tint ?: if (onDark) Color.White else OC.ink) }
    }
}
@Composable fun BackChip(onClick: () -> Unit) = IconCircle(Icons.Outlined.ArrowBack, "Back", onClick)

/** Section header: title + optional underlined action link. */
@Composable
fun Header(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = Space.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = T.bodyTitle, modifier = Modifier.weight(1f))
        if (action != null) Text(action, style = T.link, modifier = Modifier.clip(Radius.sm).clickable(onClick = onAction).padding(4.dp))
    }
}

/** Neutral-tint container, radius 16, no shadow. */
@Composable
fun Card(modifier: Modifier = Modifier, title: String? = null, action: (@Composable () -> Unit)? = null,
         color: Color = OC.card, padding: Dp = Space.lg, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(Radius.md).background(color).padding(padding)
        .animateContentSize(tween(300, easing = Motion.standard)), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        if (title != null || action != null) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title ?: "", style = T.bodyTitle, modifier = Modifier.weight(1f)); action?.invoke()
        }
        content()
    }
}

/** White card with a 12% border — for items that sit on white (offers, requests). */
@Composable
fun OutlineCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(Radius.md).border(1.dp, OC.line, Radius.md).background(OC.bg).padding(Space.lg)
        .animateContentSize(tween(300, easing = Motion.standard)), verticalArrangement = Arrangement.spacedBy(Space.md), content = content)
}

@Composable fun Hr() = Box(Modifier.fillMaxWidth().height(1.dp).background(OC.line))

/** Avatar: initials or a 24dp icon on the neutral tint; 40–72dp. */
@Composable
fun Avatar(name: String, size: Dp = 48.dp, ring: Boolean = false, dark: Boolean = false, icon: ImageVector? = null, tint: Color = OC.card) {
    Box(Modifier.size(size).clip(CircleShape).background(if (dark) OC.forest else tint)
        .then(if (ring) Modifier.border(2.dp, OC.gold, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
        if (icon != null) Icon(icon, null, Modifier.size(24.dp), tint = if (dark) OC.gold else OC.ink)
        else Text(initials(name), style = T.bodyBold.copy(fontSize = (size.value * .34f).sp, color = if (dark) OC.gold else OC.deep))
    }
}
fun initials(name: String) = name.split(" ").mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("")

/** List row: leading avatar, title (≤ 2 lines) + 1 subtitle line, trailing value / chevron / switch / badge. */
@Composable
fun ListRow(
    title: String, modifier: Modifier = Modifier, subtitle: String? = null, icon: ImageVector? = null,
    left: (@Composable () -> Unit)? = null, right: (@Composable () -> Unit)? = null, chevron: Boolean = false,
    titleColor: Color = OC.ink, avatarTint: Color = OC.card, onClick: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().clip(Radius.md).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .heightIn(min = 72.dp).padding(vertical = Space.md), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Avatar("", 48.dp, icon = icon, tint = avatarTint); Spacer(Modifier.width(Space.lg)) }
        if (left != null) { left(); Spacer(Modifier.width(Space.lg)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = T.largeBodyBold.copy(color = titleColor), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = T.defaultBody, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (right != null) { Spacer(Modifier.width(Space.sm)); right() }
        if (chevron) Icon(Icons.Outlined.ChevronRight, null, Modifier.padding(start = Space.sm).size(24.dp), tint = OC.ink)
    }
}

/** Label ↔ value row for receipts and quotes. */
@Composable
fun ReceiptRow(label: String, value: String, strong: Boolean = false, valueColor: Color = OC.ink) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = if (strong) T.largeBodyBold else T.largeBody, modifier = Modifier.weight(1f))
        Text(value, style = (if (strong) T.largeBodyBold else T.largeBody).copy(color = valueColor))
    }
}

enum class Btn { Primary, Secondary, SecondaryNeutral, Tertiary, PrimaryNegative, SecondaryNegative, Soft, Danger, Deep, Ghost }

/**
 * Full-width pill, 48dp (16h / 12v padding, 16/24 SemiBold label), no elevation, no outline.
 * PRIMARY = gold with deep-emerald label: one per screen. While loading the label becomes a skeleton — nothing jumps.
 */
@Composable
fun PillButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth(), kind: Btn = Btn.Primary,
    icon: ImageVector? = null, enabled: Boolean = true, loading: Boolean = false, height: Dp = 48.dp, progress: Float? = null,
    onDark: Boolean = false,
) {
    val k = when (kind) { Btn.Soft -> Btn.SecondaryNeutral; Btn.Danger -> Btn.SecondaryNegative; Btn.Deep -> Btn.Secondary; Btn.Ghost -> Btn.Tertiary; else -> kind }
    val (bg, fg) = when (k) {
        Btn.Primary -> OC.gold to OC.deep
        Btn.Secondary -> OC.goldSoft to OC.deep
        Btn.SecondaryNeutral -> OC.card to OC.deep
        Btn.Tertiary -> Color.Transparent to if (onDark) OC.forestText else OC.deep
        Btn.PrimaryNegative -> OC.negative to Color.White
        else -> OC.negativeSurface to OC.negative
    }
    Surface(onClick = onClick, enabled = enabled && !loading, shape = CircleShape, color = bg, contentColor = fg,
        modifier = modifier.heightIn(min = height).alpha(if (enabled) 1f else .45f)) {
        Box(contentAlignment = Alignment.Center) {
            if (progress != null && enabled) {
                Box(Modifier.matchParentSize().background(Color.White.copy(alpha = .35f)))
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(bg).align(Alignment.CenterStart))
            }
            if (loading) TextSkeleton(Modifier.width(96.dp), fg.copy(alpha = .25f))
            else Row(Modifier.padding(horizontal = Space.lg, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(Space.sm)) }
                Text(text, style = T.largeBodyBold.copy(color = fg, textDecoration = if (k == Btn.Tertiary) TextDecoration.Underline else null),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Round action: 56dp neutral circle + 24dp icon + 12sp label under it (Book · Add money · …). */
@Composable
fun ActionButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, highlighted: Boolean = false) {
    Column(modifier.clip(Radius.md).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(if (highlighted) OC.gold else OC.card), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(24.dp), tint = OC.deep)
        }
        Spacer(Modifier.height(Space.sm))
        Text(label, style = T.navBold, maxLines = 1)
    }
}

/** Choice pill, 8dp apart. Selected = brand ink fill. */
@Composable
fun Chip(text: String, active: Boolean = false, icon: ImageVector? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = if (active) OC.deep else OC.card) {
        Row(Modifier.heightIn(min = 40.dp).padding(horizontal = Space.lg), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, Modifier.size(18.dp), tint = if (active) OC.gold else OC.deep); Spacer(Modifier.width(6.dp)) }
            Text(text, style = T.bodyBold.copy(color = if (active) Color.White else OC.ink))
        }
    }
}

enum class Tone { Neutral, Good, Bad, Gold, Deep, Info, Warn }

@Composable
fun Badge(text: String, tone: Tone = Tone.Neutral) {
    val (fg, bg) = when (tone) {
        Tone.Good -> OC.positive to OC.positiveSurface; Tone.Bad -> OC.negative to OC.negativeSurface
        Tone.Warn, Tone.Gold -> OC.warning to OC.warningSurface; Tone.Info -> OC.proposition to OC.propositionSurface
        Tone.Deep, Tone.Neutral -> OC.ink to OC.card
    }
    Text(text, style = T.bodyBold.copy(fontSize = 12.sp, lineHeight = 18.sp, color = fg),
        modifier = Modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 2.dp), maxLines = 1)
}

@Composable
fun StatusPill(status: String) {
    val (label, tone) = when (status) {
        "pending" -> "Finding pros" to Tone.Info; "accepted" -> "Confirmed" to Tone.Neutral; "en_route" -> "On the way" to Tone.Neutral
        "arrived" -> "Arrived" to Tone.Neutral; "in_progress" -> "In progress" to Tone.Neutral; "completed" -> "Done" to Tone.Good
        "disputed" -> "Reported" to Tone.Bad; else -> "Cancelled" to Tone.Neutral
    }
    Badge(label, tone)
}

/** Inline alert: filled status icon in the sentiment colour + text, on the neutral tint (not a coloured box). */
@Composable
fun Alert(text: String, tone: Tone = Tone.Info, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {},
          onDismiss: (() -> Unit)? = null) {
    val (icon, tint) = when (tone) {
        Tone.Good -> Icons.Filled.CheckCircle to OC.positive; Tone.Bad -> Icons.Filled.Error to OC.negative
        Tone.Warn, Tone.Gold -> Icons.Filled.Warning to Color(0xFFB8860B); else -> Icons.Filled.Info to OC.proposition
    }
    Row(modifier.fillMaxWidth().clip(Radius.md).background(OC.card).padding(Space.lg), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(24.dp), tint = tint)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(text, style = T.defaultBody.copy(color = OC.ink))
            if (action != null) Text(action, style = T.link, modifier = Modifier.padding(top = 4.dp).clickable(onClick = onAction))
        }
        if (onDismiss != null) Icon(Icons.Outlined.Close, "Dismiss", Modifier.padding(start = Space.sm).size(20.dp).clickable(onClick = onDismiss), tint = OC.muted)
    }
}
@Composable fun Note(text: String, modifier: Modifier = Modifier, gold: Boolean = false, action: String? = null, onAction: () -> Unit = {}) =
    Alert(text, if (gold) Tone.Warn else Tone.Info, modifier, action, onAction)

/** Input: label above, white field with a 12% border, 2dp ink border on focus, 16dp radius. */
@Composable
fun Field(
    value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, label: String? = null,
    number: Boolean = false, phone: Boolean = false, error: String? = null, onTint: Boolean = false, prefix: String? = null,
    singleLine: Boolean = true, minHeight: Dp = 56.dp,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        if (label != null) Text(label, style = T.groupTitle)
        BasicTextField(value, onChange, singleLine = singleLine, textStyle = T.largeBody.copy(color = OC.ink), cursorBrush = SolidColor(OC.deep),
            keyboardOptions = KeyboardOptions(keyboardType = when { phone -> KeyboardType.Phone; number -> KeyboardType.Number; else -> KeyboardType.Text }),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Row(Modifier.fillMaxWidth().heightIn(min = minHeight).clip(Radius.md).background(OC.bg)
                    .border(BorderStroke(if (focused || error != null) 2.dp else 1.dp, when { error != null -> OC.negative; focused -> OC.ink; else -> OC.line }), Radius.md)
                    .padding(horizontal = Space.lg, vertical = 14.dp), verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top) {
                    if (prefix != null) { Text(prefix, style = T.largeBodyBold); Spacer(Modifier.width(Space.sm)) }
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) Text(placeholder, style = T.largeBody.copy(color = OC.muted))
                        inner()
                    }
                }
            })
        if (error != null) Text(error, style = T.defaultBody.copy(color = OC.negative))
    }
}

@Composable
fun ProgressLine(fraction: Float?, modifier: Modifier = Modifier, onDark: Boolean = false) {
    val track = if (onDark) Color.White.copy(alpha = .2f) else OC.card
    val fill = if (onDark) OC.gold else OC.deep
    if (fraction == null) LinearProgressIndicator(modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = fill, trackColor = track)
    else Box(modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).clip(CircleShape).background(fill))
    }
}
@Composable fun CountdownBar(fraction: Float, modifier: Modifier = Modifier) = ProgressLine(fraction, modifier)

/** Text-shaped shimmer placeholder. */
@Composable
fun TextSkeleton(modifier: Modifier = Modifier, color: Color = OC.cardStrong, height: Dp = 14.dp) {
    val a by rememberInfiniteTransition(label = "sk").animateFloat(.45f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing),
        androidx.compose.animation.core.RepeatMode.Reverse), label = "a")
    Box(modifier.height(height).clip(CircleShape).background(color.copy(alpha = color.alpha * a)))
}

/** Empty state: invitation, not absence. */
@Composable
fun Empty(title: String, body: String, icon: ImageVector) {
    Column(Modifier.fillMaxWidth().padding(vertical = Space.xxl), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Avatar("", 72.dp, icon = icon)
        Spacer(Modifier.height(Space.sm))
        Text(title, style = T.bodyTitle)
        Text(body, style = T.defaultBody)
    }
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: (() -> Unit)? = null) {
    Column(modifier.clip(Radius.md).background(OC.card).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(Space.lg), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (icon != null) { Icon(icon, null, Modifier.size(24.dp), tint = OC.ink); Spacer(Modifier.height(Space.sm)) }
        Text(value, style = T.bodyTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = T.defaultBody, maxLines = 1)
    }
}
