package app.spliitclient.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Icone disegnate a mano per non includere l'intera libreria "extended" (APK più leggero). */
object AppIcons {
    val Chart: ImageVector by lazy {
        ImageVector.Builder(
            name = "Chart",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4f, 13f); lineTo(8f, 13f); lineTo(8f, 20f); lineTo(4f, 20f); close()
                moveTo(10f, 4f); lineTo(14f, 4f); lineTo(14f, 20f); lineTo(10f, 20f); close()
                moveTo(16f, 9f); lineTo(20f, 9f); lineTo(20f, 20f); lineTo(16f, 20f); close()
            }
        }.build()
    }

    val Receipt: ImageVector by lazy {
        ImageVector.Builder(
            name = "Receipt",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(5f, 2f); lineTo(19f, 2f); lineTo(19f, 22f)
                lineTo(16.5f, 20.5f); lineTo(14f, 22f); lineTo(12f, 20.5f)
                lineTo(10f, 22f); lineTo(7.5f, 20.5f); lineTo(5f, 22f); close()
                moveTo(8f, 7f); lineTo(8f, 9f); lineTo(16f, 9f); lineTo(16f, 7f); close()
                moveTo(8f, 11f); lineTo(8f, 13f); lineTo(16f, 13f); lineTo(16f, 11f); close()
                moveTo(8f, 15f); lineTo(8f, 17f); lineTo(13f, 17f); lineTo(13f, 15f); close()
            }
        }.build()
    }

    val Scale: ImageVector by lazy {
        ImageVector.Builder(
            name = "Scale",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // asta e base
                moveTo(11f, 4f); lineTo(13f, 4f); lineTo(13f, 19f); lineTo(17f, 19f); lineTo(17f, 21f)
                lineTo(7f, 21f); lineTo(7f, 19f); lineTo(11f, 19f); close()
                // braccio
                moveTo(4f, 6f); lineTo(20f, 6f); lineTo(20f, 8f); lineTo(4f, 8f); close()
                // piatti
                moveTo(2f, 13f); lineTo(5f, 8f); lineTo(8f, 13f); close()
                moveTo(16f, 13f); lineTo(19f, 8f); lineTo(22f, 13f); close()
            }
        }.build()
    }
}
