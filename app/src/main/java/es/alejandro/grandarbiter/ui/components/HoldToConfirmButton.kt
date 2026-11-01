package es.alejandro.grandarbiter.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import es.alejandro.grandarbiter.ui.theme.softened

/** What the button is doing, for a caption next to it ("Keep holding…", "Hold to confirm"). */
enum class HoldState { IDLE, HOLDING, TAPPED }

/**
 * A button that acts only after being held for [holdMillis]: for actions an accidental tap must
 * not trigger (skipping a pick mid-draft). While held it fills left to right and its ring closes;
 * released early, it empties and nothing happens. Completion gives a haptic tick.
 * Accessibility services get a normal click action (TalkBack users can't "hold").
 */
@Composable
fun HoldToConfirmButton(
    text: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    ringAtEnd: Boolean = false,
    holdMillis: Int = DEFAULT_HOLD_MILLIS,
    onStateChange: (HoldState) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val gold = colors.primary
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val confirm by rememberUpdatedState(onConfirm)
    val stateChange by rememberUpdatedState(onStateChange)

    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .drawBehind { drawRect(gold.copy(alpha = 0.35f), size = Size(size.width * progress.value, size.height)) }
            .border(1.dp, gold.softened(0.6f), CircleShape)
            .semantics {
                role = Role.Button
                onClick(label = text) { confirm(); true }
            }
            .pointerInput(holdMillis) {
                awaitEachGesture {
                    awaitFirstDown()
                    stateChange(HoldState.HOLDING)
                    val hold = scope.launch {
                        progress.animateTo(1f, tween(holdMillis, easing = LinearEasing))
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        confirm()
                    }
                    waitForUpOrCancellation()
                    if (hold.isActive) {
                        // Released before the end: nothing happens. A quick tap teaches the gesture.
                        val wasTap = progress.value < 0.25f
                        hold.cancel()
                        scope.launch { progress.animateTo(0f, tween(150)) }
                        stateChange(if (wasTap) HoldState.TAPPED else HoldState.IDLE)
                    } else {
                        scope.launch { progress.snapTo(0f) }
                        stateChange(HoldState.IDLE)
                    }
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!ringAtEnd) HoldRing(progress.value)
        Text(text, style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium), color = gold, maxLines = 1)
        if (ringAtEnd) HoldRing(progress.value)
    }
}

/** Faint full ring + the part already held: says "hold me" at rest, shows progress while held. */
@Composable
private fun HoldRing(progress: Float) {
    val gold = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(14.dp)) {
        val stroke = Stroke(width = 1.5.dp.toPx())
        drawCircle(gold.copy(alpha = 0.35f), style = stroke)
        drawArc(gold, startAngle = -90f, sweepAngle = 360f * progress, useCenter = false, style = stroke)
    }
}

const val DEFAULT_HOLD_MILLIS = 600
