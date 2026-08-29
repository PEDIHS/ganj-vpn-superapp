package com.ganj.vpn.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

@Composable
internal fun GanjNavigationIcon(
    destination: GanjDestination,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val strokeWidth = 1.9.dp.toPx()
        val stroke = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        val w = size.width
        val h = size.height

        when (destination) {
            GanjDestination.Home -> {
                val roof = Path().apply {
                    moveTo(w * 0.16f, h * 0.46f)
                    lineTo(w * 0.50f, h * 0.18f)
                    lineTo(w * 0.84f, h * 0.46f)
                }
                drawPath(roof, tint, style = stroke)
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.25f, h * 0.42f),
                    size = Size(w * 0.50f, h * 0.40f),
                    cornerRadius = CornerRadius(w * 0.07f),
                    style = stroke,
                )
            }

            GanjDestination.Servers -> {
                listOf(0.22f, 0.46f, 0.70f).forEach { y ->
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(w * 0.16f, h * y),
                        size = Size(w * 0.68f, h * 0.14f),
                        cornerRadius = CornerRadius(h * 0.07f),
                        style = stroke,
                    )
                    drawCircle(
                        color = tint,
                        radius = strokeWidth * 0.75f,
                        center = Offset(w * 0.28f, h * (y + 0.07f)),
                    )
                }
            }

            GanjDestination.Connect -> {
                drawArc(
                    color = tint,
                    startAngle = -42f,
                    sweepAngle = 264f,
                    useCenter = false,
                    topLeft = Offset(w * 0.19f, h * 0.19f),
                    size = Size(w * 0.62f, h * 0.62f),
                    style = stroke,
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.50f, h * 0.12f),
                    end = Offset(w * 0.50f, h * 0.48f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            GanjDestination.Store -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.20f, h * 0.32f),
                    size = Size(w * 0.60f, h * 0.52f),
                    cornerRadius = CornerRadius(w * 0.08f),
                    style = stroke,
                )
                drawArc(
                    color = tint,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(w * 0.34f, h * 0.14f),
                    size = Size(w * 0.32f, h * 0.34f),
                    style = stroke,
                )
            }

            GanjDestination.Account -> {
                drawCircle(
                    color = tint,
                    radius = w * 0.16f,
                    center = Offset(w * 0.50f, h * 0.32f),
                    style = stroke,
                )
                drawArc(
                    color = tint,
                    startAngle = 198f,
                    sweepAngle = 144f,
                    useCenter = false,
                    topLeft = Offset(w * 0.18f, h * 0.48f),
                    size = Size(w * 0.64f, h * 0.42f),
                    style = stroke,
                )
            }
        }
    }
}

@Composable
internal fun GanjSecurityIcon(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val strokeWidth = 1.9.dp.toPx()
        val stroke = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        val w = size.width
        val h = size.height
        val shield = Path().apply {
            moveTo(w * 0.50f, h * 0.12f)
            lineTo(w * 0.80f, h * 0.24f)
            lineTo(w * 0.77f, h * 0.56f)
            cubicTo(w * 0.75f, h * 0.72f, w * 0.64f, h * 0.84f, w * 0.50f, h * 0.91f)
            cubicTo(w * 0.36f, h * 0.84f, w * 0.25f, h * 0.72f, w * 0.23f, h * 0.56f)
            lineTo(w * 0.20f, h * 0.24f)
            close()
        }
        drawPath(shield, color = tint, style = stroke)
        drawLine(
            color = tint,
            start = Offset(w * 0.36f, h * 0.51f),
            end = Offset(w * 0.46f, h * 0.61f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.46f, h * 0.61f),
            end = Offset(w * 0.66f, h * 0.39f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}
