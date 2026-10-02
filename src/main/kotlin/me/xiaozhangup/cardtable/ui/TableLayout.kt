package me.xiaozhangup.cardtable.ui

import me.xiaozhangup.cardtable.table.TableConfig
import org.bukkit.Location
import org.bukkit.util.Vector
import org.joml.Quaternionf
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Ground-level table origin, shared by furniture, players, avatars and private controls. */
object TableLayout {
    const val HAND_PAGE_SIZE = 20
    const val CARD_SCALE = 0.50f
    const val CARD_TILT = 0.95f
    const val CARD_WIDTH = 0.625 * CARD_SCALE
    const val CARD_HEIGHT = 0.9375 * CARD_SCALE
    const val HAND_DISTANCE = 0.60
    const val HAND_HEIGHT = 0.80
    const val HAND_LIFT = 0.08
    const val HAND_SPAN = 1.70
    const val TABLE_HEIGHT = 0.66
    const val SEAT_HEIGHT = 0.60

    fun radius(count: Int): Double = max(2.8, count * 0.46)

    // Leave space for a stool even when a large room has a seat beside a square-table corner.
    fun tableHalfSize(count: Int): Double = min(radius(count) - 1.25, (radius(count) - 0.60) / sqrt(2.0))

    fun radial(table: TableConfig, seat: Int, count: Int): Vector {
        val angle = Math.toRadians(table.center.yaw + 360.0 * seat / count)
        return Vector(sin(angle), 0.0, cos(angle))
    }

    fun seatLocation(table: TableConfig, seat: Int, count: Int): Location {
        val outward = radial(table, seat, count)
        return table.center.clone().add(outward.clone().multiply(radius(count))).apply {
            yaw = Math.toDegrees(atan2(outward.x, -outward.z)).toFloat()
            pitch = 12f
        }
    }

    /** Card fronts are local -Z; positive X tilt turns the face towards the seated eye. */
    fun facing(outward: Vector, tilt: Float = CARD_TILT): Quaternionf =
        Quaternionf().rotationY(atan2(-outward.x, -outward.z).toFloat()).rotateX(tilt)
}
