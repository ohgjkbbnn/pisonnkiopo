package com.savoo.scclient.compat

import androidx.compose.ui.graphics.Path
import androidx.graphics.shapes.RoundedPolygon

fun RoundedPolygon.toComposePath(path: Path = Path()): Path {
    path.rewind()
    cubics.forEachIndexed { index, cubic ->
        if (index == 0) path.moveTo(cubic.anchor0X, cubic.anchor0Y)
        path.cubicTo(cubic.control0X, cubic.control0Y, cubic.control1X, cubic.control1Y, cubic.anchor1X, cubic.anchor1Y)
    }
    path.close()
    return path
}
