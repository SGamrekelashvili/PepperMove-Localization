package ge.gis.tbcbank

import com.aldebaran.qi.sdk.`object`.geometry.Quaternion

internal object NavUtils {
    private const val TAG = "MSI_MapLocalizeAndMove"

    /**
     * Get the "yaw" (or "theta") angle from a quaternion (the only angle relevant for navigation).
     */
    fun getYawFromQuaternion(q: Quaternion): Double {
        // yaw (z-axis rotation)
        val x = q.x
        val y = q.y
        val z = q.z
        val w = q.w
        val sinYaw = 2.0 * (w * z + x * y)
        val cosYaw = 1.0 - 2.0 * (y * y + z * z)
        return Math.atan2(sinYaw, cosYaw)
    }
}