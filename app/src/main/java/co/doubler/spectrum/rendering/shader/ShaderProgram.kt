package co.doubler.spectrum.rendering.shader

import android.content.Context
import android.opengl.GLES30
import androidx.annotation.RawRes
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Utility for compiling and linking GLSL shader programs.
 *
 * Loads GLSL source from res/raw/ resources or raw strings, compiles vertex + fragment
 * shaders, and links into a program. Throws [ShaderException] with glGetShaderInfoLog
 * on failure.
 *
 * All GL calls use [GLES30] (OpenGL ES 3.0).
 */
class ShaderProgram private constructor(val programId: Int) {

    companion object {

        /**
         * Load, compile, and link a shader program from raw resources.
         *
         * @param context Android context for resource access
         * @param vertexShaderRes R.raw resource ID for the vertex shader
         * @param fragmentShaderRes R.raw resource ID for the fragment shader
         * @return A ready-to-use [ShaderProgram]
         * @throws ShaderException if compilation or linking fails
         */
        fun create(
            context: Context,
            @RawRes vertexShaderRes: Int,
            @RawRes fragmentShaderRes: Int
        ): ShaderProgram {
            val vertexSource = loadRawResource(context, vertexShaderRes)
            val fragmentSource = loadRawResource(context, fragmentShaderRes)
            return create(vertexSource, fragmentSource)
        }

        /**
         * Compile and link a shader program from source strings.
         *
         * @param vertexSource GLSL source code for the vertex shader
         * @param fragmentSource GLSL source code for the fragment shader
         * @return A ready-to-use [ShaderProgram]
         * @throws ShaderException if compilation or linking fails
         */
        fun create(
            vertexSource: String,
            fragmentSource: String
        ): ShaderProgram {
            val vertexShaderId = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
            val fragmentShaderId = try {
                compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
            } catch (e: ShaderException) {
                GLES30.glDeleteShader(vertexShaderId)
                throw e
            }
            val programId = linkProgram(vertexShaderId, fragmentShaderId)
            // Shaders are linked into the program; they can be detached and deleted
            GLES30.glDetachShader(programId, vertexShaderId)
            GLES30.glDeleteShader(vertexShaderId)
            GLES30.glDetachShader(programId, fragmentShaderId)
            GLES30.glDeleteShader(fragmentShaderId)
            return ShaderProgram(programId)
        }

        /**
         * Compile a single shader (vertex or fragment).
         *
         * @param type [GLES30.GL_VERTEX_SHADER] or [GLES30.GL_FRAGMENT_SHADER]
         * @param source GLSL source code
         * @return The compiled shader ID
         * @throws ShaderException if compilation fails
         */
        private fun compileShader(type: Int, source: String): Int {
            val shaderId = GLES30.glCreateShader(type)
            if (shaderId == 0) {
                throw ShaderException("glCreateShader failed for type $type")
            }

            GLES30.glShaderSource(shaderId, source)
            GLES30.glCompileShader(shaderId)

            val compileStatus = IntArray(1)
            GLES30.glGetShaderiv(shaderId, GLES30.GL_COMPILE_STATUS, compileStatus, 0)

            if (compileStatus[0] == 0) {
                val infoLog = GLES30.glGetShaderInfoLog(shaderId)
                GLES30.glDeleteShader(shaderId)
                val shaderTypeName = if (type == GLES30.GL_VERTEX_SHADER) "vertex" else "fragment"
                throw ShaderException("Failed to compile $shaderTypeName shader", infoLog)
            }

            return shaderId
        }

        /**
         * Link vertex and fragment shaders into a program.
         *
         * @param vertexShaderId Compiled vertex shader ID
         * @param fragmentShaderId Compiled fragment shader ID
         * @return The linked program ID
         * @throws ShaderException if linking fails
         */
        private fun linkProgram(vertexShaderId: Int, fragmentShaderId: Int): Int {
            val programId = GLES30.glCreateProgram()
            if (programId == 0) {
                throw ShaderException("glCreateProgram failed")
            }

            GLES30.glAttachShader(programId, vertexShaderId)
            GLES30.glAttachShader(programId, fragmentShaderId)
            GLES30.glLinkProgram(programId)

            val linkStatus = IntArray(1)
            GLES30.glGetProgramiv(programId, GLES30.GL_LINK_STATUS, linkStatus, 0)

            if (linkStatus[0] == 0) {
                val infoLog = GLES30.glGetProgramInfoLog(programId)
                GLES30.glDeleteProgram(programId)
                throw ShaderException("Failed to link shader program", infoLog)
            }

            return programId
        }

        /**
         * Read a raw resource file as a String.
         *
         * @param context Android context for resource access
         * @param resId R.raw resource ID
         * @return The file contents as a String
         */
        private fun loadRawResource(context: Context, @RawRes resId: Int): String {
            return context.resources.openRawResource(resId).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            }
        }
    }

    /** Activate this shader program for subsequent GL draw calls. */
    fun use() {
        GLES30.glUseProgram(programId)
    }

    /**
     * Get the location of a uniform variable.
     *
     * @param name The uniform variable name as declared in the GLSL source
     * @return The uniform location, or -1 if not found
     */
    fun getUniformLocation(name: String): Int {
        return GLES30.glGetUniformLocation(programId, name)
    }

    /**
     * Get the location of an attribute variable.
     *
     * @param name The attribute variable name as declared in the GLSL source
     * @return The attribute location, or -1 if not found
     */
    fun getAttribLocation(name: String): Int {
        return GLES30.glGetAttribLocation(programId, name)
    }

    /** Delete the shader program and release GL resources. */
    fun delete() {
        GLES30.glDeleteProgram(programId)
    }
}
