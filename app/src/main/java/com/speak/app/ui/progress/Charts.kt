package com.speak.app.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/** A point on a trend chart. */
data class ChartPoint(val x: Float, val y: Float)

/**
 * A minimal line chart drawn directly on a Canvas.
 *
 * Hand-drawn rather than pulled from a charting library: the app needs three
 * simple trends, and adding a dependency for that would mean shipping a general
 * charting engine, its theming, and its release cadence for no gain.
 *
 * A single measurement is shown as a dot rather than a line, because one point is
 * not a trend and drawing it as one would imply progress that has not happened.
 */
@Composable
fun TrendChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 120.dp,
    /** Draw a reference band, e.g. the natural speaking-pace range. */
    referenceBand: ClosedFloatingPointRange<Float>? = null
) {
    val lineColour = MaterialTheme.colorScheme.primary
    val fillColour = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val gridColour = MaterialTheme.colorScheme.outlineVariant
    val bandColour = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)

    Canvas(modifier.fillMaxWidth().height(height)) {
        if (points.isEmpty()) return@Canvas

        val minY = min(points.minOf { it.y }, referenceBand?.start ?: Float.MAX_VALUE)
        val maxY = max(points.maxOf { it.y }, referenceBand?.endInclusive ?: Float.MIN_VALUE)
        // Never divide by zero, and give a flat series some vertical room.
        val span = (maxY - minY).takeIf { it > 0.001f } ?: max(maxY, 1f)
        val padded = span * 1.15f
        val base = minY - (padded - span) / 2f

        fun yPixel(value: Float) = size.height - ((value - base) / padded) * size.height

        // ---- grid ----
        for (fraction in listOf(0f, 0.5f, 1f)) {
            val y = size.height * fraction
            drawLine(gridColour, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }

        // ---- reference band ----
        referenceBand?.let { band ->
            val top = yPixel(band.endInclusive)
            val bottom = yPixel(band.start)
            drawRect(
                color = bandColour,
                topLeft = Offset(0f, top),
                size = androidx.compose.ui.geometry.Size(size.width, (bottom - top).coerceAtLeast(1f))
            )
        }

        if (points.size == 1) {
            drawCircle(
                color = lineColour,
                radius = 6f,
                center = Offset(size.width / 2f, yPixel(points.first().y))
            )
            return@Canvas
        }

        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val xSpan = (maxX - minX).takeIf { it > 0f } ?: 1f
        fun xPixel(value: Float) = ((value - minX) / xSpan) * size.width

        val linePath = Path()
        val fillPath = Path()
        points.forEachIndexed { index, point ->
            val x = xPixel(point.x)
            val y = yPixel(point.y)
            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(xPixel(points.last().x), size.height)
        fillPath.close()

        drawPath(fillPath, fillColour)
        drawPath(
            linePath,
            lineColour,
            style = Stroke(width = 3.5f, cap = StrokeCap.Round)
        )
        points.forEach { point ->
            drawCircle(lineColour, radius = 3.5f, center = Offset(xPixel(point.x), yPixel(point.y)))
        }
    }
}

/** A labelled trend with its current value and range. */
@Composable
fun TrendSection(
    title: String,
    currentValue: String,
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    caption: String? = null,
    referenceBand: ClosedFloatingPointRange<Float>? = null
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                currentValue,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (caption != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                caption,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))
        if (points.isEmpty()) {
            Text(
                "Not enough practice yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            TrendChart(points = points, referenceBand = referenceBand)
        }
    }
}

/** Horizontal bars, used for the mistake-category breakdown. */
@Composable
fun CategoryBars(
    entries: List<Pair<String, Int>>,
    modifier: Modifier = Modifier
) {
    val total = entries.sumOf { it.second }.coerceAtLeast(1)
    val barColour = MaterialTheme.colorScheme.primary
    val trackColour = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier.fillMaxWidth()) {
        entries.forEach { (label, count) ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "$count",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                drawRoundRect(
                    color = trackColour,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                )
                drawRoundRect(
                    color = barColour,
                    size = androidx.compose.ui.geometry.Size(
                        size.width * (count.toFloat() / total),
                        size.height
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                )
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}
