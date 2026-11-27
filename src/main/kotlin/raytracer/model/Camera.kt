package raytracer.model

import java.awt.Point
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

/**
 * Класс, представляющий камеру в трёхмерном пространстве.
 * Камера определяется её положением, направлением взгляда и настройками проекции.
 *
 * @param origin Точка, из которой смотрит камера.
 * @param target Точка, на которую смотрит камера.
 * @param vup Вектор, направленный вверх от камеры.
 * @param fov Вертикальный угол обзора камеры в градусах.
 * @param aperture  Диаметр апертуры камеры.
 * @param focusDist  Фокусное расстояние от камеры до объекта съёмки.
 */
class Camera(
    origin: Point3,
    target: Point3,
    vup: Point3,
    fov: Double,
    var aperture: Double,
    focusDist: Double,
    var imageWidth: Int,
    var imageHeight: Int,
) {
    private var _origin = origin
    private var _target = target
    private var _vup = vup

    var vup: Point3 = _vup
        set(value) {
            field = value
            updateCamera()
        }
    var fov: Double = fov
        set(value) {
            field = value
            updateProjection()
        }
    var focusDist: Double = focusDist
        set(value) {
            field = value
            updateProjection()
        }

    var samplesPerPixel = 5
    val aspectRatio: Double get() = imageWidth.toDouble() / imageHeight.toDouble()
    val lensRadius: Double get() = aperture / 2
    var movementSpeed: Double = 0.1

    private var v: Point3 = Point3.ZERO
    private var u: Point3 = Point3.ZERO
    private var w: Point3 = Point3.ZERO
    private var lowerLeftCorner: Point3 = Point3.ZERO
    private var horizontal: Point3 = Point3.ZERO
    private var vertical: Point3 = Point3.ZERO

    private var yaw = 0.0
    private var pitch = 0.0
    private val maxPitch = Math.PI / 2 - 0.01

    init {
        updateCamera()
    }

    fun adjustYaw(delta: Double) {
        yaw += delta
        updateCamera()
    }

    fun adjustPitch(delta: Double) {
        pitch = (pitch + delta).coerceIn(-maxPitch, maxPitch)
        updateCamera()
    }

    private fun moveInDirection(x: Double, y: Double, z: Double) {
        _origin += Point3(x, y, z) * movementSpeed
        updateCamera()
    }

    fun moveForward() = moveInDirection(cos(pitch) * cos(yaw), sin(pitch), cos(pitch) * sin(yaw))
    fun moveBackward() = moveInDirection(-cos(pitch) * cos(yaw), -sin(pitch), -cos(pitch) * sin(yaw))
    fun moveLeft() = moveInDirection(sin(yaw), 0.0, -cos(yaw))
    fun moveRight() = moveInDirection(-sin(yaw), 0.0, cos(yaw))
    fun moveUp() = run { _origin += _vup * movementSpeed; updateCamera() }
    fun moveDown() = run { _origin -= _vup * movementSpeed; updateCamera() }

    fun move(move: CameraMove) = when (move) {
        CameraMove.FORWARD -> moveForward()
        CameraMove.BACKWARD -> moveBackward()
        CameraMove.LEFT -> moveLeft()
        CameraMove.RIGHT -> moveRight()
        CameraMove.UP -> moveUp()
        CameraMove.DOWN -> moveDown()
        CameraMove.NONE -> Unit
    }

    /** Rotates the view by a mouse drag of [dxPixels]×[dyPixels] over the frame. */
    fun rotateByDrag(dxPixels: Int, dyPixels: Int) {
        adjustYaw(dxPixels * 2 * Math.PI / imageWidth * DRAG_SENSITIVITY)
        adjustPitch(-(dyPixels * 2 * Math.PI / imageHeight * DRAG_SENSITIVITY))
    }

    /** Frames of 50 px or less are not rendered after a settings change. */
    fun hasRenderableSize(): Boolean = imageWidth > MIN_RENDERED_SIZE && imageHeight > MIN_RENDERED_SIZE

    private fun updateCamera() {
        val forward = Point3(cos(pitch) * cos(yaw), sin(pitch), cos(pitch) * sin(yaw))
        _target = _origin + forward
        w = (_origin - _target).unit()
        u = _vup.cross(w).unit()
        v = w.cross(u)
        updateProjection()
    }

    private fun updateProjection() {
        val theta = Math.toRadians(fov)
        val halfHeight = tan(theta / 2)
        val halfWidth = aspectRatio * halfHeight
        lowerLeftCorner = _origin - (u * halfWidth + v * halfHeight + w) * focusDist
        horizontal = u * 2.0 * halfWidth * focusDist
        vertical = v * 2.0 * halfHeight * focusDist
    }

    fun ray(s: Double, t: Double, random: Random): Ray {
        val rd = Point3.randomInUnitDisk(random) * lensRadius
        val offset = u * rd.x + v * rd.y
        return Ray(
            _origin + offset,
            lowerLeftCorner + horizontal * s + vertical * t - _origin - offset
        )
    }

    fun project(point: Point3): Point? {
        val cameraSpacePoint = point - _origin
        val x = cameraSpacePoint.dot(u)
        val y = cameraSpacePoint.dot(v)
        val z = cameraSpacePoint.dot(w)

        if (z >= 0) {
            return null
        }

        val theta = Math.toRadians(fov)
        val halfHeight = tan(theta / 2)
        val halfWidth = aspectRatio * halfHeight

        val screenX = (x / (-z * halfWidth)) * (imageWidth / 2) + (imageWidth / 2)
        val screenY = (y / (-z * halfHeight)) * (imageHeight / 2) + (imageHeight / 2)

        return Point(screenX.toInt(), (imageHeight - screenY.toInt()).toInt())
    }

    companion object {
        private const val DRAG_SENSITIVITY = 0.1
        private const val MIN_RENDERED_SIZE = 50

        fun default() = Camera(
            origin = Point3.ONE,
            target = Point3.ZERO,
            vup = Point3(0.0, 1.0, 0.0),
            fov = 90.0,
            aperture = 0.1,
            focusDist = 10.0,
            imageWidth = 400,
            imageHeight = 400
        )
    }
}

enum class CameraMove { FORWARD, BACKWARD, LEFT, RIGHT, UP, DOWN, NONE }
