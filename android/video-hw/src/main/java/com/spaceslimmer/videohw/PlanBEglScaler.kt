package com.spaceslimmer.videohw

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Copies decoder output (OES texture) onto the encoder input surface at [outW]x[outH].
 * Used when Plan B recipe size differs from the source.
 */
internal class PlanBEglScaler(
    private val encoderSurface: Surface,
    private val outW: Int,
    private val outH: Int,
    srcW: Int,
    srcH: Int,
) : SurfaceTexture.OnFrameAvailableListener {
    val decoderSurface: Surface

    private val lock = Object()
    private var frameAvailable = false
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var textureId = 0
    private var program = 0
    private var aPos = 0
    private var aTex = 0
    private lateinit var surfaceTexture: SurfaceTexture
    private val vertexBuf: FloatBuffer
    private val texBuf: FloatBuffer

    init {
        val verts = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val tex = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
        vertexBuf = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertexBuf.put(verts).position(0)
        texBuf = ByteBuffer.allocateDirect(tex.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        texBuf.put(tex).position(0)

        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize" }
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0)
        val cfg = configs[0] ?: error("eglChooseConfig")
        context = EGL14.eglCreateContext(
            display, cfg, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
        )
        eglSurface = EGL14.eglCreateWindowSurface(
            display, cfg, encoderSurface,
            intArrayOf(EGL14.EGL_NONE), 0,
        )
        check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) { "eglMakeCurrent" }

        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        textureId = texIds[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        program = buildProgram(VERTEX, FRAGMENT)
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aTex = GLES20.glGetAttribLocation(program, "aTex")

        surfaceTexture = SurfaceTexture(textureId)
        surfaceTexture.setDefaultBufferSize(srcW.coerceAtLeast(2), srcH.coerceAtLeast(2))
        surfaceTexture.setOnFrameAvailableListener(this)
        decoderSurface = Surface(surfaceTexture)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        synchronized(lock) {
            frameAvailable = true
            lock.notifyAll()
        }
    }

    fun drawFrame(presentationTimeUs: Long) {
        synchronized(lock) {
            val deadline = System.currentTimeMillis() + 1_000
            while (!frameAvailable) {
                val wait = deadline - System.currentTimeMillis()
                if (wait <= 0) break
                lock.wait(wait)
            }
            frameAvailable = false
        }
        surfaceTexture.updateTexImage()
        GLES20.glViewport(0, 0, outW, outH)
        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, vertexBuf)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, texBuf)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)
        EGLExt.eglPresentationTimeANDROID(display, eglSurface, presentationTimeUs * 1_000)
        EGL14.eglSwapBuffers(display, eglSurface)
    }

    fun release() {
        runCatching { decoderSurface.release() }
        runCatching { surfaceTexture.release() }
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }

    private fun buildProgram(vertex: String, fragment: String): Int {
        val vs = compile(GLES20.GL_VERTEX_SHADER, vertex)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        val link = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, link, 0)
        check(link[0] != 0) { GLES20.glGetProgramInfoLog(prog) }
        return prog
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        check(compiled[0] != 0) { GLES20.glGetShaderInfoLog(shader) }
        return shader
    }

    companion object {
        private const val EGL_RECORDABLE_ANDROID = 0x3142
        private const val VERTEX = """
            attribute vec4 aPos;
            attribute vec2 aTex;
            varying vec2 vTex;
            void main() {
                gl_Position = aPos;
                vTex = aTex;
            }
        """
        private const val FRAGMENT = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTex;
            uniform samplerExternalOES sTex;
            void main() {
                gl_FragColor = texture2D(sTex, vTex);
            }
        """
    }
}
