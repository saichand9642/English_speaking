package com.speak.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The ten icons this app uses, defined here rather than pulled from a library.
 *
 * `material-icons-extended` was costing about 40 MB of dex in the debug APK --
 * more than the speech model and the native library put together -- to supply ten
 * glyphs. Since this is an APK that gets sideloaded and copied around by hand,
 * that size is worth caring about, so the set is defined directly.
 *
 * Every path is drawn on a 24x24 viewport and filled, never stroked, so they
 * tint correctly from the Compose colour that is passed down.
 */
object SpeakIcons {

    val Mic: ImageVector by lazy {
        icon("Mic") {
            // Capsule body.
            moveTo(12f, 14f)
            curveTo(13.66f, 14f, 15f, 12.66f, 15f, 11f)
            verticalLineTo(5f)
            curveTo(15f, 3.34f, 13.66f, 2f, 12f, 2f)
            reflectiveCurveTo(9f, 3.34f, 9f, 5f)
            verticalLineToRelative(6f)
            curveTo(9f, 12.66f, 10.34f, 14f, 12f, 14f)
            close()
            // Cradle and stand.
            moveTo(17f, 11f)
            curveTo(17f, 13.76f, 14.76f, 16f, 12f, 16f)
            reflectiveCurveTo(7f, 13.76f, 7f, 11f)
            horizontalLineTo(5f)
            curveTo(5f, 14.53f, 7.61f, 17.43f, 11f, 17.92f)
            verticalLineTo(21f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-3.08f)
            curveTo(16.39f, 17.43f, 19f, 14.53f, 19f, 11f)
            close()
        }
    }

    val Stop: ImageVector by lazy {
        icon("Stop") {
            moveTo(8f, 6f)
            horizontalLineToRelative(8f)
            curveTo(17.1f, 6f, 18f, 6.9f, 18f, 8f)
            verticalLineToRelative(8f)
            curveTo(18f, 17.1f, 17.1f, 18f, 16f, 18f)
            horizontalLineTo(8f)
            curveTo(6.9f, 18f, 6f, 17.1f, 6f, 16f)
            verticalLineTo(8f)
            curveTo(6f, 6.9f, 6.9f, 6f, 8f, 6f)
            close()
        }
    }

    val VolumeUp: ImageVector by lazy {
        icon("VolumeUp") {
            // Speaker.
            moveTo(4f, 9f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(4f)
            lineToRelative(5f, 5f)
            verticalLineTo(4f)
            lineTo(8f, 9f)
            close()
            // Inner wave.
            moveTo(16.5f, 12f)
            curveTo(16.5f, 10.23f, 15.48f, 8.71f, 14f, 7.97f)
            verticalLineToRelative(8.05f)
            curveTo(15.48f, 15.29f, 16.5f, 13.77f, 16.5f, 12f)
            close()
            // Outer wave.
            moveTo(14f, 3.23f)
            verticalLineToRelative(2.06f)
            curveTo(16.89f, 6.15f, 19f, 8.83f, 19f, 12f)
            reflectiveCurveTo(16.89f, 17.85f, 14f, 18.71f)
            verticalLineToRelative(2.06f)
            curveTo(18.01f, 19.86f, 21f, 16.28f, 21f, 12f)
            reflectiveCurveTo(18.01f, 4.14f, 14f, 3.23f)
            close()
        }
    }

    val SkipNext: ImageVector by lazy {
        icon("SkipNext") {
            moveTo(6f, 18f)
            lineToRelative(8.5f, -6f)
            lineTo(6f, 6f)
            close()
            moveTo(16f, 6f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(12f)
            horizontalLineToRelative(-2f)
            close()
        }
    }

    val Refresh: ImageVector by lazy {
        icon("Refresh") {
            moveTo(17.65f, 6.35f)
            curveTo(16.2f, 4.9f, 14.21f, 4f, 12f, 4f)
            curveTo(7.58f, 4f, 4f, 7.58f, 4f, 12f)
            reflectiveCurveTo(7.58f, 20f, 12f, 20f)
            curveTo(15.73f, 20f, 18.84f, 17.45f, 19.73f, 14f)
            horizontalLineToRelative(-2.08f)
            curveTo(16.83f, 16.33f, 14.61f, 18f, 12f, 18f)
            curveTo(8.69f, 18f, 6f, 15.31f, 6f, 12f)
            reflectiveCurveTo(8.69f, 6f, 12f, 6f)
            curveTo(13.66f, 6f, 15.14f, 6.69f, 16.22f, 7.78f)
            lineTo(13f, 11f)
            horizontalLineToRelative(7f)
            verticalLineTo(4f)
            close()
        }
    }

    val ArrowBack: ImageVector by lazy {
        icon("ArrowBack") {
            moveTo(20f, 11f)
            horizontalLineTo(7.83f)
            lineToRelative(5.59f, -5.59f)
            lineTo(12f, 4f)
            lineToRelative(-8f, 8f)
            lineToRelative(8f, 8f)
            lineToRelative(1.41f, -1.41f)
            lineTo(7.83f, 13f)
            horizontalLineTo(20f)
            close()
        }
    }

    val Repeat: ImageVector by lazy {
        icon("Repeat") {
            moveTo(7f, 7f)
            horizontalLineToRelative(10f)
            verticalLineToRelative(3f)
            lineToRelative(4f, -4f)
            lineToRelative(-4f, -4f)
            verticalLineToRelative(3f)
            horizontalLineTo(5f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(2f)
            close()
            moveTo(17f, 17f)
            horizontalLineTo(7f)
            verticalLineToRelative(-3f)
            lineToRelative(-4f, 4f)
            lineToRelative(4f, 4f)
            verticalLineToRelative(-3f)
            horizontalLineToRelative(12f)
            verticalLineToRelative(-6f)
            horizontalLineToRelative(-2f)
            close()
        }
    }

    /** A rising bar chart: the progress screens. */
    val Insights: ImageVector by lazy {
        icon("Insights") {
            moveTo(4f, 14f)
            horizontalLineToRelative(3f)
            verticalLineToRelative(6f)
            horizontalLineTo(4f)
            close()
            moveTo(10.5f, 8f)
            horizontalLineToRelative(3f)
            verticalLineToRelative(12f)
            horizontalLineToRelative(-3f)
            close()
            moveTo(17f, 11f)
            horizontalLineToRelative(3f)
            verticalLineToRelative(9f)
            horizontalLineToRelative(-3f)
            close()
        }
    }

    /** An open book: read-aloud practice. */
    val MenuBook: ImageVector by lazy {
        icon("MenuBook") {
            moveTo(11f, 6.5f)
            curveTo(9.5f, 5.3f, 7f, 4.7f, 5f, 5.2f)
            verticalLineToRelative(13f)
            curveTo(7f, 17.7f, 9.5f, 18.3f, 11f, 19.5f)
            close()
            moveTo(13f, 6.5f)
            curveTo(14.5f, 5.3f, 17f, 4.7f, 19f, 5.2f)
            verticalLineToRelative(13f)
            curveTo(17f, 17.7f, 14.5f, 18.3f, 13f, 19.5f)
            close()
        }
    }

    /**
     * Two sliders rather than a gear. A gear at 24 px is a smudge of teeth;
     * sliders stay legible, which is the point of an icon.
     */
    val Settings: ImageVector by lazy {
        icon("Settings") {
            moveTo(3f, 7f)
            horizontalLineToRelative(18f)
            verticalLineToRelative(2f)
            horizontalLineTo(3f)
            close()
            circle(centreX = 9f, centreY = 8f, radius = 3.2f)
            moveTo(3f, 15f)
            horizontalLineToRelative(18f)
            verticalLineToRelative(2f)
            horizontalLineTo(3f)
            close()
            circle(centreX = 16f, centreY = 16f, radius = 3.2f)
        }
    }

    // -----------------------------------------------------------------------

    private fun icon(name: String, pathBlock: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = SolidColor(Color.Black),
            pathBuilder = pathBlock
        ).build()

    /** A filled circle, drawn as two half arcs. */
    private fun PathBuilder.circle(centreX: Float, centreY: Float, radius: Float) {
        moveTo(centreX - radius, centreY)
        arcToRelative(radius, radius, 0f, true, true, radius * 2, 0f)
        arcToRelative(radius, radius, 0f, true, true, -radius * 2, 0f)
        close()
    }
}
