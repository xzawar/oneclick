package pk.oneclick.app

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
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

/**
 * One Click design tokens. Calm and flat: white screens, tinted cards, no shadows on content, one loud action per
 * screen. Brand primitives: gold #E8B84A (the one primary button per screen), deep emerald #0B3D2E (text on gold,
 * controls, the brand-moment canvas), ink #0E1512 (titles). Everything else derives from them.
 */
object OC {
    val bg = Color(0xFFFFFFFF)
    val card = Color(0xFFEEF3F0)          // deep emerald at ~7% on white
    val cardStrong = Color(0xFFE1EAE5)
    val line = Color(0xFFE3E8E5)
    val ink = Color(0xFF0E1512)
    val text = Color(0xFF46524C)
    val muted = Color(0xFF6A7570)
    val faint = Color(0xFFA9B2AD)
    val gold = Color(0xFFE8B84A)
    val goldSoft = Color(0xFFFBF1DA)
    val deep = Color(0xFF0B3D2E)
    val deep2 = Color(0xFF14634A)
    val green = Color(0xFF1E7A4C)
    val red = Color(0xFFB3261E)
    val scrim = Color(0x660E1512)
}

object Space { val xs = 4.dp; val sm = 8.dp; val md = 12.dp; val lg = 16.dp; val xl = 24.dp; val xxl = 32.dp }
object Radius {
    val sm = RoundedCornerShape(10.dp); val md = RoundedCornerShape(12.dp); val lg = RoundedCornerShape(16.dp)
    val xl = RoundedCornerShape(24.dp); val sheet = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
}
object Motion {
    val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    val launch = CubicBezierEasing(0.8f, 0f, 1f, 1f)
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal), Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold), Font(R.font.inter_bold, FontWeight.Bold),
)
val DisplayFace = FontFamily(Font(R.font.archivo_black, FontWeight.Normal))

/** Type ramp: every screen's one title is the heavy display face; section titles and numbers are Inter Bold. */
object T {
    val display = TextStyle(fontFamily = DisplayFace, fontSize = 40.sp, lineHeight = 40.sp, letterSpacing = (-0.4).sp, color = OC.ink)
    val h1 = TextStyle(fontFamily = DisplayFace, fontSize = 32.sp, lineHeight = 35.sp, letterSpacing = (-0.3).sp, color = OC.ink)
    val h2 = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 25.sp, letterSpacing = (-0.3).sp, color = OC.ink)
    val title = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.4).sp, color = OC.ink)
    val number = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.45).sp, color = OC.ink)
    val body = TextStyle(fontFamily = Inter, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = (-0.18).sp, color = OC.text)
    val bodyStrong = body.copy(fontWeight = FontWeight.SemiBold, color = OC.ink, lineHeight = 22.sp)
    val small = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = (-0.08).sp, color = OC.text)
    val tiny = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp, color = OC.muted)
    val label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp, color = OC.ink)
}

@Composable
fun OneClickTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = OC.deep, onPrimary = Color.White, secondary = OC.gold, background = OC.bg, surface = OC.bg,
        onSurface = OC.ink, onBackground = OC.ink, surfaceVariant = OC.card, onSurfaceVariant = OC.muted,
        error = OC.red, outline = OC.line,
    )
    MaterialTheme(colorScheme = scheme, typography = Typography(bodyLarge = T.body, bodyMedium = T.small, labelLarge = T.label)) {
        CompositionLocalProvider(LocalContentColor provides OC.ink, LocalTextStyle provides T.body, content = content)
    }
}

// ---------------- modifiers ----------------
fun Modifier.tap(onClick: () -> Unit) = composed { clickable(remember { MutableInteractionSource() }, null, onClick = onClick) }

// ---------------- Layout ----------------

/** Scrolling page: optional back chip above the one bold display title, then content with 16dp gaps. */
@Composable
fun Screen(
    title: String? = null, subtitle: String? = null, onBack: (() -> Unit)? = null, right: (@Composable () -> Unit)? = null,
    bottomPad: Dp = 96.dp, content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(OC.bg).verticalScroll(rememberScrollState())
            .statusBarsPadding().padding(horizontal = Space.lg).padding(top = Space.lg, bottom = bottomPad),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        if (onBack != null) BackChip(onBack)
        if (title != null) Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = if (onBack == null) Space.xl else 0.dp)) {
                Text(title, style = T.h1)
                if (subtitle != null) Text(subtitle, style = T.body, modifier = Modifier.padding(top = Space.sm))
            }
            right?.invoke()
        }
        content()
    }
}

/** Back button: a circular chip on the neutral tint. */
@Composable
fun BackChip(onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = OC.card, modifier = Modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.ArrowBack, "Back", Modifier.size(22.dp), tint = OC.ink) }
    }
}

/** Tinted card: radius 16, padding 16, optional title row with an action. */
@Composable
fun Card(modifier: Modifier = Modifier, title: String? = null, action: (@Composable () -> Unit)? = null,
         color: Color = OC.card, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(Radius.lg).background(color).padding(Space.lg)
        .animateContentSize(tween(220, easing = Motion.emphasized)), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        if (title != null || action != null) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title ?: "", style = T.h2, modifier = Modifier.weight(1f))
            action?.invoke()
        }
        content()
    }
}

@Composable
fun SeeAll(label: String = "See all", onClick: () -> Unit) {
    Row(Modifier.clip(Radius.sm).tap(onClick).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = T.label.copy(color = OC.deep))
        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = OC.deep)
    }
}

/** Stat tile: label, big number, optional icon. */
@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: (() -> Unit)? = null) {
    val inner: @Composable () -> Unit = {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            if (icon != null) Icon(icon, null, Modifier.size(22.dp), tint = OC.deep)
            Text(value, style = if (value.length > 6) T.number.copy(fontSize = 18.sp, lineHeight = 30.sp) else T.number, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = T.tiny, maxLines = 1)
        }
    }
    if (onClick != null) Surface(onClick = onClick, color = OC.card, shape = Radius.lg, modifier = modifier) { inner() }
    else Box(modifier.clip(Radius.lg).background(OC.card)) { inner() }
}

/** List row: flush, title SemiBold, subtitle in body colour, optional leading and trailing items. */
@Composable
fun ListRow(
    title: String, modifier: Modifier = Modifier, subtitle: String? = null, icon: ImageVector? = null,
    left: (@Composable () -> Unit)? = null, right: (@Composable () -> Unit)? = null, chevron: Boolean = false,
    titleColor: Color = OC.ink, onClick: (() -> Unit)? = null,
) {
    val body: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(vertical = Space.md, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(OC.bg), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(22.dp), tint = OC.deep)
                }
                Spacer(Modifier.width(Space.md))
            }
            if (left != null) { left(); Spacer(Modifier.width(Space.md)) }
            Column(Modifier.weight(1f)) {
                Text(title, style = T.bodyStrong.copy(color = titleColor), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = T.small, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (right != null) { Spacer(Modifier.width(Space.sm)); right() }
            if (chevron) Icon(Icons.Outlined.ChevronRight, null, Modifier.size(22.dp), tint = OC.muted)
        }
    }
    if (onClick != null) Box(modifier.clip(Radius.lg).clickable(onClick = onClick)) { body() } else Box(modifier) { body() }
}

@Composable
fun Hr() = Box(Modifier.fillMaxWidth().height(1.dp).background(OC.line))

enum class Btn { Primary, Deep, Soft, Danger, Ghost }

/** Pill button, 52dp, flat. Primary = gold with deep text: the one loud action per screen. */
@Composable
fun PillButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, kind: Btn = Btn.Primary, icon: ImageVector? = null,
    enabled: Boolean = true, loading: Boolean = false, height: Dp = 52.dp, progress: Float? = null,
) {
    val (bg, fg) = when (kind) {
        Btn.Primary -> OC.gold to OC.deep
        Btn.Deep -> OC.deep to Color.White
        Btn.Soft -> OC.card to OC.ink
        Btn.Danger -> OC.card to OC.red
        Btn.Ghost -> Color.Transparent to OC.deep
    }
    Surface(onClick = onClick, enabled = enabled && !loading, shape = CircleShape, color = bg,
        contentColor = fg, modifier = modifier.height(height).then(if (!enabled) Modifier.background(Color.Transparent) else Modifier)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.then(if (!enabled) Modifier.background(Color.White.copy(alpha = .55f)) else Modifier)) {
            if (progress != null && enabled) {
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .35f)))
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(bg).align(Alignment.CenterStart))
            }
            if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = fg, strokeWidth = 2.dp)
            else Row(Modifier.padding(horizontal = Space.xl), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(Space.sm)) }
                Text(text, style = T.label.copy(fontSize = 16.sp, color = fg), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Filter/option pill. Active = ink fill. */
@Composable
fun Chip(text: String, active: Boolean = false, icon: ImageVector? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = if (active) OC.ink else OC.card) {
        Row(Modifier.height(36.dp).padding(horizontal = Space.lg), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, Modifier.size(16.dp), tint = if (active) Color.White else OC.deep); Spacer(Modifier.width(6.dp)) }
            Text(text, style = T.label.copy(color = if (active) Color.White else OC.ink))
        }
    }
}

enum class Tone { Neutral, Good, Bad, Gold, Deep }

@Composable
fun Badge(text: String, tone: Tone = Tone.Neutral) {
    val fg = when (tone) { Tone.Good -> OC.green; Tone.Bad -> OC.red; Tone.Gold -> OC.deep; Tone.Deep -> OC.deep; Tone.Neutral -> OC.ink }
    val bg = when (tone) { Tone.Gold -> OC.goldSoft; else -> OC.cardStrong }
    Row(Modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        if (tone == Tone.Gold) { Box(Modifier.size(6.dp).clip(CircleShape).background(OC.gold)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 18.sp, color = fg))
    }
}

@Composable
fun StatusPill(status: String) {
    val (label, tone) = when (status) {
        "pending" -> "Searching" to Tone.Gold; "accepted" -> "Confirmed" to Tone.Deep; "en_route" -> "On the way" to Tone.Deep
        "arrived" -> "Arrived" to Tone.Deep; "in_progress" -> "In progress" to Tone.Deep; "completed" -> "Done" to Tone.Good
        "disputed" -> "Reported" to Tone.Bad; else -> "Cancelled" to Tone.Neutral
    }
    Badge(label, tone)
}

/** Initials avatar on the neutral tint; optional gold ring for verified people. */
@Composable
fun Avatar(name: String, size: Dp = 44.dp, ring: Boolean = false, dark: Boolean = false) {
    Box(Modifier.size(size).clip(CircleShape).background(if (dark) OC.deep else OC.cardStrong)
        .then(if (ring) Modifier.border(2.dp, OC.gold, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
        Text(initials(name), style = T.label.copy(fontSize = (size.value * .36f).sp, color = if (dark) OC.gold else OC.deep))
    }
}
fun initials(name: String) = name.split(" ").mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("")

/** Input: white field inside cards / tint field on white screens; 2dp deep stroke on focus. */
@Composable
fun Field(
    value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, label: String? = null,
    number: Boolean = false, phone: Boolean = false, error: String? = null, onTint: Boolean = false, prefix: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) Text(label, style = T.label)
        BasicTextField(value, onChange, singleLine = true, textStyle = T.body.copy(color = OC.ink), cursorBrush = SolidColor(OC.deep),
            keyboardOptions = KeyboardOptions(keyboardType = when { phone -> KeyboardType.Phone; number -> KeyboardType.Number; else -> KeyboardType.Text }),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(Radius.md).background(if (onTint) OC.bg else OC.card)
                    .border(BorderStroke(2.dp, when { error != null -> OC.red; focused -> OC.deep; else -> Color.Transparent }), Radius.md)
                    .padding(horizontal = Space.lg), verticalAlignment = Alignment.CenterVertically) {
                    if (prefix != null) { Text(prefix, style = T.body.copy(color = OC.muted)); Spacer(Modifier.width(Space.sm)) }
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) Text(placeholder, style = T.body.copy(color = OC.faint))
                        inner()
                    }
                }
            })
        if (error != null) Text(error, style = T.tiny.copy(color = OC.red))
    }
}

/** One-line note on the tint, optional action link. */
@Composable
fun Note(text: String, modifier: Modifier = Modifier, gold: Boolean = false, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth().clip(Radius.md).background(if (gold) OC.goldSoft else OC.card).padding(horizontal = Space.md, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (gold) { Box(Modifier.size(6.dp).clip(CircleShape).background(OC.gold)); Spacer(Modifier.width(Space.sm)) }
        Text(text, style = T.small.copy(color = OC.ink), modifier = Modifier.weight(1f))
        if (action != null) Text(action, style = T.label.copy(color = OC.deep), modifier = Modifier.padding(start = Space.sm).tap(onAction))
    }
}

@Composable
fun Empty(title: String, body: String, icon: ImageVector) {
    Column(Modifier.fillMaxWidth().padding(vertical = Space.xxl), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(OC.card), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(28.dp), tint = OC.deep)
        }
        Text(title, style = T.h2)
        Text(body, style = T.small.copy(color = OC.muted))
    }
}

@Composable
fun CountdownBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(OC.cardStrong)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).clip(CircleShape).background(OC.deep))
    }
}
