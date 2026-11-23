package pk.oneclick.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The One Click mark, drawn on a 24-unit grid (same geometry as res/drawable/ic_launcher_foreground.xml):
 * a heavy gold "1" and a hand whose index finger taps beside it, with two ripple arcs — "one click".
 */
object MarkGeometry {
    const val ONE = "M3.6,8.4 L8.2,5.0 L11.0,5.0 L11.0,19.0 L7.6,19.0 L7.6,9.6 L5.0,11.2 Z"
    // Hand: index finger up, palm below (stylised, rounded).
    const val HAND = "M15.0,9.2 C15.0,8.4 15.6,7.8 16.4,7.8 C17.2,7.8 17.8,8.4 17.8,9.2 L17.8,13.4 " +
        "L20.6,14.0 C21.5,14.2 22.1,15.0 22.0,15.9 L21.5,19.6 C21.4,20.4 20.7,21.0 19.9,21.0 L15.6,21.0 " +
        "C15.1,21.0 14.7,20.8 14.4,20.4 L11.9,17.0 C11.5,16.5 11.6,15.8 12.1,15.4 C12.6,15.0 13.3,15.1 13.7,15.5 L15.0,16.9 Z"
    val onePath: Path by lazy { PathParser().parsePathString(ONE).toPath() }
    val handPath: Path by lazy { PathParser().parsePathString(HAND).toPath() }
}

/** The mark on its emerald tile. [tile] false draws just the glyphs (e.g. on the curtain). */
@Composable
fun OneClickMark(size: Dp, modifier: Modifier = Modifier, tile: Boolean = true, ripple: Float = 1f) {
    Box(modifier.size(size).then(if (tile) Modifier.clip(RoundedCornerShape(size * .28f)).background(OC.deep) else Modifier)) {
        Canvas(Modifier.fillMaxSize()) {
            val s = this.size.minDimension / 24f
            scale(s, s, pivot = Offset.Zero) {
                drawPath(MarkGeometry.onePath, OC.gold)
                // ripple arcs around the fingertip (16.4, 7.8)
                val stroke = Stroke(width = 1.1f, cap = StrokeCap.Round)
                for (i in 1..2) {
                    val r = (1.6f + i * 1.3f) * ripple
                    drawArc(OC.gold.copy(alpha = if (i == 1) 1f else .6f), startAngle = 200f, sweepAngle = 140f, useCenter = false,
                        topLeft = Offset(16.4f - r, 8.6f - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2), style = stroke)
                }
                drawPath(MarkGeometry.handPath, Color.White)
            }
        }
    }
}

/** Mark + wordmark, used on sign-in and about. */
@Composable
fun Wordmark(markSize: Dp = 40.dp, dark: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OneClickMark(markSize)
        Spacer(Modifier.width(10.dp))
        androidx.compose.material3.Text("One Click", style = T.title.copy(fontFamily = DisplayFace, color = if (dark) Color.White else OC.ink))
    }
}

/**
 * Opening moment, picked up where the native splash (emerald background) leaves off: the ripples pulse once,
 * the mark lifts and grows on an ease-in for 1s, and at 800ms the curtain fades over 200ms to reveal the app.
 */
@Composable
fun LaunchCurtain(onDone: () -> Unit) {
    val grow = remember { Animatable(0f) }
    val curtain = remember { Animatable(1f) }
    val ripple = remember { Animatable(.6f) }
    LaunchedEffect(Unit) {
        launch { ripple.animateTo(1f, tween(500, easing = Motion.emphasized)) }
        delay(350)
        launch { grow.animateTo(1f, tween(1000, easing = Motion.launch)) }
        delay(800)
        curtain.animateTo(0f, tween(200, easing = LinearEasing))
        onDone()
    }
    Box(Modifier.fillMaxSize().alpha(curtain.value).background(OC.deep), contentAlignment = Alignment.Center) {
        OneClickMark(120.dp, Modifier.scale(0.8f + grow.value * 0.9f).offset(y = (-24 * grow.value).dp), tile = false, ripple = ripple.value)
    }
}
