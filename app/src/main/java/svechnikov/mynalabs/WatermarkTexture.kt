package svechnikov.mynalabs

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class WatermarkTexture(bitmap: Bitmap) {

    val texId: Int

    init {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        Utils.checkGlError("glGenTextures")

        texId = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        Utils.checkGlError("glBindTexture $texId")

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_NEAREST,
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR,
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE,
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE,
        )
        Utils.checkGlError("glTexParameter")
    }
}

class WatermarkTextureProgram {

    private val program: Int

    private val uMVPMatrixLoc: Int
    private val uTexMatrixLoc: Int
    private val aPositionLoc: Int
    private val aTextureCoordLoc: Int
    private val alphaLoc: Int

    init {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        program = GLES20.glCreateProgram()
        Utils.checkGlError("glCreateProgram")
        GLES20.glAttachShader(program, vertexShader)
        Utils.checkGlError("glAttachShader")
        GLES20.glAttachShader(program, fragmentShader)
        Utils.checkGlError("glAttachShader")
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            throw RuntimeException()
        }

        aPositionLoc = getAttribLocation("aPosition")
        aTextureCoordLoc = getAttribLocation("aTextureCoord")
        uMVPMatrixLoc = getUniformLocation("uMVPMatrix")
        uTexMatrixLoc = getUniformLocation("uTexMatrix")
        alphaLoc = getUniformLocation("alpha")

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        Utils.checkGlError("glGenTextures")
    }

    fun draw(texId: Int, alpha: Float) {
        Utils.checkGlError("draw start")

        // Select the program.
        GLES20.glUseProgram(program)
        Utils.checkGlError("glUseProgram")

        // Set the texture.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)

        // Copy the model / view / projection matrix over.
        GLES20.glUniformMatrix4fv(uMVPMatrixLoc, 1, false, IDENTITY_MATRIX, 0)
        Utils.checkGlError("glUniformMatrix4fv")

        // Copy the texture transformation matrix over.
        GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, IDENTITY_MATRIX, 0)
        Utils.checkGlError("glUniformMatrix4fv")

        // Copy the texture transformation matrix over.
        GLES20.glUniform1f(alphaLoc, alpha)
        Utils.checkGlError("glUniform1f")

        // Enable the "aPosition" vertex attribute.
        GLES20.glEnableVertexAttribArray(aPositionLoc)
        Utils.checkGlError("glEnableVertexAttribArray")

        // Connect vertexBuffer to "aPosition".
        GLES20.glVertexAttribPointer(
            aPositionLoc, 2,
            GLES20.GL_FLOAT, false, 2 * Utils.SIZE_OF_FLOAT, VERTEX_BUFFER
        )
        Utils.checkGlError("glVertexAttribPointer")

        // Enable the "aTextureCoord" vertex attribute.
        GLES20.glEnableVertexAttribArray(aTextureCoordLoc)
        Utils.checkGlError("glEnableVertexAttribArray")

        // Connect texBuffer to "aTextureCoord".
        GLES20.glVertexAttribPointer(
            aTextureCoordLoc, 2,
            GLES20.GL_FLOAT, false, 2 * Utils.SIZE_OF_FLOAT, TEX_BUFFER
        )
        Utils.checkGlError("glVertexAttribPointer")

        // Draw the rect.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        Utils.checkGlError("glDrawArrays")

        // Done -- disable vertex array, texture, and program.
        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aTextureCoordLoc)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glUseProgram(0)
    }

    private fun getAttribLocation(name: String): Int {
        val location = GLES20.glGetAttribLocation(program, name)
        if (location < 0) {
            throw RuntimeException()
        }
        return location
    }

    private fun getUniformLocation(name: String): Int {
        val location = GLES20.glGetUniformLocation(program, name)
        if (location < 0) {
            throw RuntimeException()
        }
        return location
    }

    private fun loadShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        Utils.checkGlError("glCreateShader type=$type")

        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            throw RuntimeException(GLES20.glGetShaderInfoLog(shader))
        }
        return shader
    }

    private companion object {
        const val VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uTexMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTextureCoord = (uTexMatrix * aTextureCoord).xy;
            }
            """

        const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D sTexture;
            uniform float alpha;
            void main() {
                vec4 tc = texture2D(sTexture, vTextureCoord);
                if(tc.a == 0.0)
                    discard;
                gl_FragColor = vec4(tc.rgb, alpha);
            }"""

        val IDENTITY_MATRIX = FloatArray(16).apply {
            Matrix.setIdentityM(this, 0)
        }

        val VERTEX_BUFFER = createBuffer(
            floatArrayOf(
                -0.8f, 0.8f, // left top
                0.8f, 0.8f, // right top
                -0.8f, -0.8f, // left bottom
                0.8f, -0.8f, // right bottom
            )
        )

        val TEX_BUFFER = createBuffer(
            floatArrayOf(
                0.0f, 0.0f, // 0 bottom left
                1.0f, 0.0f, // 1 bottom right
                0.0f, 1.0f, // 2 top left
                1.0f, 1.0f, // 3 top right
            )
        )

        fun createBuffer(coords: FloatArray): FloatBuffer = ByteBuffer.allocateDirect(
            coords.size * Utils.SIZE_OF_FLOAT,
        ).run {
            order(ByteOrder.nativeOrder())
            val fb = asFloatBuffer()
            fb.put(coords)
            fb.position(0)
            fb
        }
    }
}