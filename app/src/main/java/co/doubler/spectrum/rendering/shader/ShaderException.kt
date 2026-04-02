package co.doubler.spectrum.rendering.shader

/**
 * Exception thrown when shader compilation or program linking fails.
 * Includes the GLSL info log from the driver for debugging.
 */
class ShaderException(
    message: String,
    val infoLog: String = ""
) : RuntimeException("$message\nInfo log: $infoLog")
