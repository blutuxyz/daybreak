package com.bdersolutions.daybreak.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * A white coffee cup with a red heart, in the style of the Ko-fi button: the same drawing as
 * `ic_kofi` in the Android app. It keeps its own colours, so show it with `tint = Color.Unspecified`.
 */
val KofiIcon: ImageVector by lazy {
    fun nodes(data: String) = PathParser().parsePathString(data).toNodes()
    ImageVector.Builder(
        name = "Kofi",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = nodes("M2.5,6.5H17.5V12.5A5.5,5.5 0 0 1 12,18H8A5.5,5.5 0 0 1 2.5,12.5Z"),
            fill = SolidColor(Color.White),
        )
        addPath(
            pathData = nodes("M17.5,8.7H19A2.3,2.3 0 0 1 19,13.3H16.8"),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
        )
        addPath(
            pathData = nodes(
                "M10,15.6L9.7,15.3C7.6,13.4 6.5,12.4 6.5,11.1C6.5,10.1 7.3,9.3 8.3,9.3C8.9,9.3 9.6,9.6 10,10.2" +
                    "C10.4,9.6 11.1,9.3 11.7,9.3C12.7,9.3 13.5,10.1 13.5,11.1C13.5,12.4 12.4,13.4 10.3,15.3Z",
            ),
            fill = SolidColor(Color(0xFFFF5E5B)),
        )
    }.build()
}
