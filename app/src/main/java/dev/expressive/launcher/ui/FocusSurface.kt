@file:OptIn(ExperimentalFoundationApi::class)

package dev.expressive.launcher.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

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
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val calm = LocalCalmMotion.current
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val longHandled = remember { booleanArrayOf(false) }

    LaunchedEffect(pressed) {
        if (pressed) longHandled[0] = false
    }

    val scale = animateFloatAsState(
        targetValue = if (pressed) 0.95f else if (focused) focusedScale else 1f,
        animationSpec = if (calm) {
            spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
        } else {
            spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "scale"
    )
    val morph = animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = if (calm) {
            spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
        } else {
            spring<Float>(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)
        },
        label = "morph"
    )

    Box(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                val s = scale.value
                scaleX = s
                scaleY = s
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
                onFocusChange(state.isFocused)
            }
            .onKeyEvent { event ->
                if (onLongClick != null && event.type == KeyEventType.KeyUp &&
                    (event.key == Key.Menu || event.key == Key.Info)
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
