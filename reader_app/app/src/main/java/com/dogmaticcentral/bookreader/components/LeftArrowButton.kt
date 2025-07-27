package com.dogmaticcentral.bookreader.components


import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp


/**
 * -------------------------------------------------------------------
 * 1) POINTS THAT MAKE UP A LEFT–POINTING ARROW  (logical 100 × 60 box)
 * -------------------------------------------------------------------
 *
 *  (100,20)  (tail-top) ──────┐
 *             ▲               │
 *             │               │
 *  (40,20)  base-top          │
 *     ▲                       │   width = 100
 *     │                       │   height = 60
 *  (40, 0)  head-top          │
 *      \                      │
 *       \                     │
 *        (0,30) tip ◄─────────┤
 *       /                     │
 *      /                      │
 *  (40,60) head-bottom        │
 *     │                       │
 *  (40,40) base-bottom        │
 *             ▼               │
 *  (100,40) tail-bottom ──────┘
 */
private val arrowPoints = listOf(
    Offset(100f, 20f), // 0 tail-top
    Offset( 30f, 20f), // 1 base-top
    Offset( 30f,  0f), // 2 head-top
    Offset(  0f, 30f), // 3 tip
    Offset( 30f, 60f), // 4 head-bottom
    Offset( 30f, 40f), // 5 base-bottom
    Offset(100f, 40f)  // 6 tail-bottom
)

fun LeftArrowPath(size: Int = 240,): Path {
    // scale logical points to the real canvas size
    val scaleX = size / 100f
    val scaleY = size / 60f

    val path = Path().apply {
        val p0 = arrowPoints[0]
        moveTo(p0.x * scaleX, p0.y * scaleY)
        for (i in 1 until arrowPoints.size) {
            val p = arrowPoints[i]
            lineTo(p.x * scaleX, p.y * scaleY)
        }
        close()
    }
    return path
}

/**
 * -------------------------------------------------------------------
 * 2) COMPOSABLE THAT DRAWS THE ARROW
 * -------------------------------------------------------------------
 */
@Composable
fun LeftArrowButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Black,
    size: Int = 96,
    cornerRadius: Dp = 2.dp // Added cornerRadius parameter
) {
    Canvas(
            modifier = modifier
                // keep the arrow’s aspect ratio (width : height = 100 : 60 = 5 : 3)
                .aspectRatio(5f / 3f)
                .clickable(onClick = onClick)
        ) {

            drawPath(
                path = LeftArrowPath(size),
                color = tint,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 38f,
                    pathEffect = PathEffect.cornerPathEffect(cornerRadius.toPx()) // Apply rounded corners
                )
            )

         }


}


/**
 * -------------------------------------------------------------------
 * 4) ANDROID-STUDIO PREVIEW
 * -------------------------------------------------------------------
 */

@Preview(showBackground = true)
@Composable
fun LeftArrowButtonPreview() {
    MaterialTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.LightGray),
            contentAlignment = Alignment.Center
        ) {
            LeftArrowButton(
                onClick = { /* Handle click */ },
                 tint = Color.Blue,

            )
        }
    }
}
