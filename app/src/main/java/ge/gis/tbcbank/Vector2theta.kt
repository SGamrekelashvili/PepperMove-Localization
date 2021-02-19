package ge.gis.tbcbank

import androidx.core.app.NavUtils
import com.aldebaran.qi.sdk.`object`.actuation.Frame
import com.aldebaran.qi.sdk.`object`.geometry.Quaternion
import com.aldebaran.qi.sdk.`object`.geometry.Transform
import com.aldebaran.qi.sdk.`object`.geometry.Vector3
import com.aldebaran.qi.sdk.builder.TransformBuilder
import ge.gis.tbcbank.NavUtils.getYawFromQuaternion


class Vector2theta private constructor(
    private val x: Double,
    private val y: Double,
    private val theta: Double
) {
    /**
     * Returns a transform representing the translation described by this Vector2theta
     * @return the transform
     */
    fun createTransform(): Transform {
        // this.theta is the radian angle to appy taht was serialized
        return TransformBuilder.create().from2DTransform(x, y, theta)
    }

    /***************** Add here automatic Parcelable implementaion (hidden for readability)  */
    companion object {

        fun betweenFrames(frameDestination: Frame, frameOrigin: Frame): Vector2theta {
            // Compute the transform to go from "frameOrigin" to "frameDestination"
            val transform: Transform =
                frameOrigin.async().computeTransform(frameDestination).getValue().getTransform()

            // Extract translation from the transform
            val translation: Vector3 = transform.getTranslation()
            // Extract quaternion from the transform
            val quaternion: Quaternion = transform.getRotation()

            // Extract the 2 coordinates from the translation and orientation angle from quaternion
            return Vector2theta(
                translation.x,
                translation.y,
                getYawFromQuaternion(quaternion)
            )
        }
    }
}