package com.tomessa.miff.ui.onboarding

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path

/**
 * The pale green organic shapes behind the welcome illustrations, TRACED from the PM's mockups ("Welcoming screen",
 * "-16", "-17", design/exports/onboarding-original/): the pale green is isolated by color in the exports, the parts
 * hidden by the illustrations are continued smoothly, and the outline is simplified to a few dozen points.
 *
 * Coordinates are in the units of the mockups (320 wide, y measured from the top of the screen). They are drawn as
 * smooth curves through those points, so the shapes stay sharp at any size (no bitmap). Each slide has its own shapes.
 */
internal object WelcomeBlobs {
    /** Width of the mockups, in units: the scale of a screen is its width divided by this. */
    const val UNITS_WIDTH = 320f

    /** Height of the area covered by the shapes (and by the illustrations), in units. */
    const val UNITS_HEIGHT = 480f

    private val SLIDES: List<List<String>> =
        listOf(
            listOf(
                "21.0,49.8 -0.4,61.0 -0.4,138.0 16.0,150.3 35.0,154.3 50.0,147.3 64.0,129.7 73.3,109.0 75.4,86.0 " +
                    "69.4,73.0 55.0,59.7 37.0,50.9",
                "235.0,91.9 210.0,95.7 190.0,101.6 169.0,110.7 150.1,122.0 151.8,127.0 156.0,130.7 171.0,134.1 " +
                    "189.0,132.7 205.0,125.3 230.0,125.1 235.0,127.0 237.3,134.0 237.5,161.0 237.3,193.0 234.9,200.0 " +
                    "233.0,200.8 229.2,189.0 221.1,177.0 205.0,164.9 187.0,159.8 155.0,157.7 131.0,161.7 109.0,172.8 " +
                    "93.6,190.0 89.3,207.0 92.5,224.0 101.7,238.0 116.0,247.3 134.0,251.2 160.0,249.3 193.0,241.2 " +
                    "214.0,232.2 226.8,223.0 233.0,213.7 235.6,215.0 237.4,225.0 237.6,394.0 238.5,401.0 242.0,404.2 " +
                    "284.0,357.0 319.4,309.0 319.5,109.0 302.0,100.8 280.0,94.7 257.0,91.7",
                "77.0,172.9 33.5,216.0 15.5,240.0 -0.3,267.0 -0.4,428.0 23.0,451.0 52.0,468.2 84.0,477.2 " +
                    "118.0,477.4 146.0,471.0 175.0,458.3 201.0,441.3 222.4,422.0 215.0,411.3 199.0,404.7 166.0,397.3 " +
                    "136.0,401.8 110.0,400.9 94.0,395.9 83.0,386.1 82.0,377.8 94.2,358.0 97.7,340.0 91.7,314.0 " +
                    "95.3,294.0 94.8,285.0 81.3,257.0 80.8,180.0 79.8,175.0",
                "310.0,382.8 304.0,384.7 287.8,396.0 280.7,406.0 277.7,416.0 278.7,429.0 282.6,437.0 292.3,446.0 " +
                    "302.0,449.6 311.0,449.1 319.5,444.0 319.5,384.0",
            ),
            listOf(
                "21.0,49.8 -0.4,61.0 -0.4,138.0 16.0,150.3 35.0,154.3 50.0,147.3 64.0,129.7 73.3,109.0 75.4,86.0 " +
                    "69.4,73.0 55.0,59.7 37.0,50.9",
                "235.0,91.9 210.0,95.7 190.0,101.6 169.0,110.7 148.6,123.0 151.0,126.7 157.0,128.2 209.0,126.1 " +
                    "222.0,129.2 233.0,127.1 236.5,131.0 237.2,182.0 235.2,195.0 237.5,212.0 237.5,335.0 235.1,354.0 " +
                    "237.4,368.0 236.8,398.0 238.2,402.0 242.0,404.2 284.0,357.0 319.4,309.0 319.5,109.0 302.0,100.8 " +
                    "280.0,94.7 257.0,91.7",
                "77.0,172.9 33.5,216.0 15.5,240.0 -0.3,267.0 -0.4,428.0 23.0,451.0 52.0,468.2 84.0,477.2 " +
                    "118.0,477.4 146.0,471.0 175.0,458.3 201.0,441.3 221.4,423.0 215.0,415.8 190.0,408.4 177.0,397.3 " +
                    "162.0,404.4 151.0,406.1 120.0,397.7 117.4,400.0 115.8,410.0 109.0,416.2 90.0,417.4 82.2,413.0 " +
                    "80.9,182.0 79.8,175.0",
                "310.0,382.8 304.0,384.7 287.8,396.0 280.7,406.0 277.7,416.0 278.7,429.0 282.6,437.0 292.3,446.0 " +
                    "302.0,449.6 311.0,449.1 319.5,444.0 319.5,384.0",
            ),
            listOf(
                "21.0,49.8 -0.4,61.0 -0.4,138.0 16.0,150.3 35.0,154.3 50.0,147.3 64.0,129.7 73.3,109.0 75.4,86.0 " +
                    "69.4,73.0 55.0,59.7 37.0,50.9",
                "235.0,91.9 202.0,97.7 173.0,108.7 94.0,159.5 88.1,164.0 95.6,192.0 93.3,201.0 80.1,209.0 " +
                    "25.0,226.9 -0.3,267.0 -0.4,428.0 21.0,449.5 42.0,463.4 74.0,475.3 107.0,478.3 142.0,472.3 " +
                    "175.0,458.3 212.0,432.4 251.8,394.0 245.8,366.0 249.3,357.0 267.0,348.4 294.0,344.2 319.4,309.0 " +
                    "319.5,109.0 302.0,100.8 280.0,94.7 257.0,91.7",
                "310.0,382.8 304.0,384.7 287.8,396.0 280.7,406.0 277.7,416.0 278.7,429.0 282.6,437.0 292.3,446.0 " +
                    "302.0,449.6 311.0,449.1 319.5,444.0 319.5,384.0",
            ),
        )

    /** The outlines of slide [page] (0 to 2), already parsed. */
    fun outlines(page: Int): List<List<Offset>> = SLIDES[page].map(::parse)

    /** The shapes of slide [page] as one path, scaled by [scale] (pixels per unit). */
    fun path(
        page: Int,
        scale: Float,
    ): Path {
        val path = Path()
        outlines(page).forEach { points ->
            // Quadratic curves between the middles of the sides: a smooth closed outline through the traced points.
            fun mid(
                a: Offset,
                b: Offset,
            ) = Offset((a.x + b.x) / 2f * scale, (a.y + b.y) / 2f * scale)
            val start = mid(points.last(), points.first())
            path.moveTo(start.x, start.y)
            points.indices.forEach { i ->
                val control = points[i]
                val end = mid(control, points[(i + 1) % points.size])
                path.quadraticTo(control.x * scale, control.y * scale, end.x, end.y)
            }
            path.close()
        }
        return path
    }

    internal fun parse(outline: String): List<Offset> =
        outline.trim().split(' ').map { pair ->
            val (x, y) = pair.split(',')
            Offset(x.toFloat(), y.toFloat())
        }
}
