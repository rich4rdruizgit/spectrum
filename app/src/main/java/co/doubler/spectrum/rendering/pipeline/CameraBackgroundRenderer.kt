package co.doubler.spectrum.rendering.pipeline

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.util.Log
import co.doubler.spectrum.R
import co.doubler.spectrum.rendering.shader.ShaderProgram
import co.doubler.spectrum.util.Constants
import com.google.ar.core.Frame
import com.google.ar.core.Session
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Renders the ARCore camera feed as the background layer.
 *
 * Creates an OES external texture, binds it to the ARCore session via
 * [Session.setCameraTextureName], then draws a fullscreen quad each frame
 * using the camera's transformed UV coordinates.
 *
 * All methods MUST be called on the GL thread.
 */
class CameraBackgroundRenderer(private val context: Context) {

    /** The GL texture ID for the camera OES texture. Valid after [onSurfaceCreated]. */
    var cameraTextureId: Int = -1
        private set

    private var shaderProgram: ShaderProgram? = null
    private var vertexVbo: Int = 0
    private var texCoordVbo: Int = 0

    private var uVignetteStrengthLoc: Int = -1
    private var uContrastLoc: Int = -1
    private var uSaturationLoc: Int = -1
    private var uTintColorLoc: Int = -1
    private var uTrackingLostLoc: Int = -1

    @Volatile private var postFxTint: FloatArray = floatArrayOf(1f, 1f, 1f)
    @Volatile private var postFxTrackingLost: Boolean = false

    private val quadVertexBuffer: FloatBuffer = createFloatBuffer(Constants.FULLSCREEN_QUAD_COORDS)
    private val sourceUvBuffer: FloatBuffer = createFloatBuffer(Constants.FULLSCREEN_QUAD_TEX_COORDS)
    private val transformedUvBuffer: FloatBuffer = createFloatBuffer(Constants.FULLSCREEN_QUAD_TEX_COORDS)

    /**
     * Create OES texture, compile camera shaders, set up fullscreen quad VBOs.
     * Must be called on GL surface creation and on every GL context recreation.
     *
     * @param session The active ARCore session to bind the camera texture to
     */
    fun onSurfaceCreated(session: Session) {
        // 1. Generate OES texture
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        cameraTextureId = textures[0]

        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_MIN_FILTER,
            GLES30.GL_LINEAR
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_MAG_FILTER,
            GLES30.GL_LINEAR
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_WRAP_S,
            GLES30.GL_CLAMP_TO_EDGE
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_WRAP_T,
            GLES30.GL_CLAMP_TO_EDGE
        )

        // 2. Bind texture to ARCore session
        session.setCameraTextureName(cameraTextureId)

        // 3. Compile camera shader program
        shaderProgram = ShaderProgram.create(
            context,
            R.raw.camera_vertex,
            R.raw.camera_fragment
        )

        val prog = shaderProgram!!
        uVignetteStrengthLoc = prog.getUniformLocation("u_VignetteStrength")
        uContrastLoc         = prog.getUniformLocation("u_Contrast")
        uSaturationLoc       = prog.getUniformLocation("u_Saturation")
        uTintColorLoc        = prog.getUniformLocation("u_TintColor")
        uTrackingLostLoc     = prog.getUniformLocation("u_TrackingLost")
        if (uVignetteStrengthLoc == -1 || uContrastLoc == -1 || uSaturationLoc == -1 ||
            uTintColorLoc == -1 || uTrackingLostLoc == -1) {
            Log.w(TAG, "One or more postfx uniform locations not found — check shader uniform names")
        }

        // 4. Create VBOs
        val vbos = IntArray(2)
        GLES30.glGenBuffers(2, vbos, 0)
        vertexVbo = vbos[0]
        texCoordVbo = vbos[1]

        // Upload vertex positions (static — never changes)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexVbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            Constants.FULLSCREEN_QUAD_COORDS.size * Constants.FLOAT_SIZE_BYTES,
            quadVertexBuffer,
            GLES30.GL_STATIC_DRAW
        )

        // Upload default tex coords (will be updated per-frame)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, texCoordVbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            Constants.FULLSCREEN_QUAD_TEX_COORDS.size * Constants.FLOAT_SIZE_BYTES,
            transformedUvBuffer,
            GLES30.GL_DYNAMIC_DRAW
        )

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun setPostFxParams(tintColor: FloatArray, trackingLost: Boolean) {
        postFxTint = tintColor
        postFxTrackingLost = trackingLost
    }

    /**
     * Draw the camera background for the current frame.
     *
     * Disables depth test/write during draw, restores afterward. Updates UV
     * coordinates from [Frame.transformDisplayUvCoords] when display geometry changes.
     *
     * @param frame The current ARCore frame
     */
    fun draw(frame: Frame) {
        // 1. Update UV coords when display geometry changes
        if (frame.hasDisplayGeometryChanged()) {
            sourceUvBuffer.rewind()
            frame.transformDisplayUvCoords(sourceUvBuffer, transformedUvBuffer)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, texCoordVbo)
            GLES30.glBufferSubData(
                GLES30.GL_ARRAY_BUFFER,
                0,
                Constants.FULLSCREEN_QUAD_TEX_COORDS.size * Constants.FLOAT_SIZE_BYTES,
                transformedUvBuffer
            )
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        }

        // 2. Disable depth test and depth write
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)

        // 3. Use camera shader
        val program = shaderProgram ?: return
        program.use()

        GLES30.glUniform1f(uVignetteStrengthLoc, 0.55f)
        GLES30.glUniform1f(uContrastLoc, 1.1f)
        GLES30.glUniform1f(uSaturationLoc, 0.85f)
        GLES30.glUniform3fv(uTintColorLoc, 1, postFxTint, 0)
        GLES30.glUniform1i(uTrackingLostLoc, if (postFxTrackingLost) 1 else 0)

        // 4. Bind OES texture
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + Constants.CAMERA_TEXTURE_UNIT)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES30.glUniform1i(
            program.getUniformLocation("u_Texture"),
            Constants.CAMERA_TEXTURE_UNIT
        )

        // 5. Bind vertex position VBO → attribute 0
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexVbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(
            0,
            Constants.COORDS_PER_VERTEX,
            GLES30.GL_FLOAT,
            false,
            0,
            0
        )

        // 6. Bind tex coord VBO → attribute 1
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, texCoordVbo)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(
            1,
            Constants.TEX_COORDS_PER_VERTEX,
            GLES30.GL_FLOAT,
            false,
            0,
            0
        )

        // 7. Draw fullscreen quad as triangle strip
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        // 8. Cleanup GL state
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)

        // 9. Restore depth test and depth write
        GLES30.glDepthMask(true)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    /** Delete GL resources (texture, shader program, VBOs). */
    fun cleanup() {
        shaderProgram?.delete()
        shaderProgram = null

        if (cameraTextureId != -1) {
            GLES30.glDeleteTextures(1, intArrayOf(cameraTextureId), 0)
            cameraTextureId = -1
        }

        val vbos = intArrayOf(vertexVbo, texCoordVbo).filter { it != 0 }.toIntArray()
        if (vbos.isNotEmpty()) {
            GLES30.glDeleteBuffers(vbos.size, vbos, 0)
        }
        vertexVbo = 0
        texCoordVbo = 0
    }

    private companion object {

        const val TAG = "CameraBackgroundRenderer"

        fun createFloatBuffer(data: FloatArray): FloatBuffer {
            return ByteBuffer.allocateDirect(data.size * Constants.FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(data)
                    position(0)
                }
        }
    }
}
