@file:OptIn(ExperimentalFoundationApi::class)

package dev.expressive.launcher.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

private val DefaultOne: () -> Float = { 1f }
private val DefaultZero: () -> Float = { 0f }

fun FocusRequester.tryFocus(): Boolean {
    val result = runCatching { requestFocus() }
    val value: Any? = result.getOrNull()
    return result.isSuccess && value != false
}

@Composable
fun FocusSurface(
    modifier: Modifier = Modifier,
    shapeAt: ShapeAt,
    containerColor: Color,
    ringColor: Color,
    focusedContainerColor: Color = containerColor,
    ringWidth: Dp = 2.5.dp,
    focusedScale: Float = 1.06f,
    focusRequester: FocusRequester? = null,
    onFocusChange: (Boolean) -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    extraScale: () -> Float = DefaultOne,
    extraAlpha: () -> Float = DefaultOne,
    extraRotation: () -> Float = DefaultZero,
    extraTranslationX: () -> Float = DefaultZero,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val motion = LocalMotion.current
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val longHandled = remember { booleanArrayOf(false) }
    val downSeen = remember { booleanArrayOf(false) }

    LaunchedEffect(pressed) {
        if (pressed) longHandled[0] = false
    }

    val scale = animateFloatAsState(
        targetValue = if (motion == 2) 1f else if (pressed) 0.95f else if (focused) focusedScale else 1f,
        animationSpec = when (motion) {
            2 -> snap<Float>()
            1 -> spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
            else -> spring<Float>(dampingRatio = 0.6f, stiffness = 900f)
        },
        label = "scale"
    )
    val morph = animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = when (motion) {
            2 -> snap<Float>()
            1 -> spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
            else -> spring<Float>(dampingRatio = 0.75f, stiffness = 1200f)
        },
        label = "morph"
    )

    Box(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                val s = scale.value * extraScale()
                scaleX = s
                scaleY = s
                alpha = extraAlpha()
                rotationZ = extraRotation()
                translationX = extraTranslationX()
                compositingStrategy = CompositingStrategy.ModulateAlpha
                shape = shapeAt(morph.value, this)
                clip = true
            }
            .drawBehind {
                drawRect(lerp(containerColor, focusedContainerColor, morph.value.coerceIn(0f, 1f)))
            }
            .drawWithContent {
                drawContent()
                val m = morph.value.coerceIn(0f, 1f)
                if (m > 0.02f && ringWidth > 0.dp) {
                    val outline = shapeAt(m, this).createOutline(size, layoutDirection, this)
                    drawPath(
                        path = outline.toPath(),
                        color = ringColor.copy(alpha = m),
                        style = Stroke(width = ringWidth.toPx() * 2f)
                    )
                }
            }
            .onFocusChanged { state ->
                focused = state.isFocused
                if (!state.isFocused) downSeen[0] = false
                onFocusChange(state.isFocused)
            }
            .onPreviewKeyEvent { event ->
                val isOk = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                if (!isOk) {
                    false
                } else {
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            if (event.nativeKeyEvent.repeatCount == 0) {
                                downSeen[0] = true
                                false
                            } else {
                                !downSeen[0]
                            }
                        }
                        KeyEventType.KeyUp -> {
                            val had = downSeen[0]
                            downSeen[0] = false
                            !had
                        }
                        else -> false
                    }
                }
            }
            .onKeyEvent { event ->
                if (onLongClick != null && event.type == KeyEventType.KeyUp &&
                    event.key == Key.Info
                ) {
                    onLongClick()
                    true
                } else {
                    false
                }
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onLongClick = if (onLongClick == null) {
                    null
                } else {
                    {
                        longHandled[0] = true
                        onLongClick()
                    }
                },
                onClick = {
                    if (longHandled[0]) longHandled[0] = false else onClick()
                }
            ),
        content = content
    )
}
