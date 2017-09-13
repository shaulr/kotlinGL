package com.danefinlay.opengltesting

/**
 * Created by shaulr on 13/09/2017.
 */

/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//package org.gs.components.graphics;

import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.CharBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer

import javax.microedition.khronos.opengles.GL10
import javax.microedition.khronos.opengles.GL11
import javax.microedition.khronos.opengles.GL11Ext

/**
 * This is the OpenGL ES version of a sprite.  It is more complicated than the
 * CanvasSprite class because it can be used in more than one way.  This class
 * can draw using a grid of verts, a grid of verts stored in VBO objects, or
 * using the DrawTexture extension.
 */
internal class GLSprite(// The id of the original resource that mTextureName is based on.
        var resourceId: Int) : Renderable() {
    // The OpenGL ES texture handle to draw.
    var textureName: Int = 0
    // If drawing with verts or VBO verts, the grid object defining those verts.
    var grid: Grid? = null

    fun draw(gl: GL10) {
        gl.glBindTexture(GL10.GL_TEXTURE_2D, textureName)

        if (grid == null) {
            // Draw using the DrawTexture extension.
            (gl as GL11Ext).glDrawTexfOES(x, y, z, width, height)
        } else {
            // Draw using verts or VBO verts.
            gl.glPushMatrix()
            gl.glLoadIdentity()
            gl.glTranslatef(
                    x,
                    y,
                    z)

            grid!!.draw(gl, true, false)

            gl.glPopMatrix()
        }
    }
}


/**
 * A 2D rectangular mesh. Can be drawn textured or untextured.
 * This version is modified from the original Grid.java (found in
 * the SpriteText package in the APIDemos Android sample) to support hardware
 * vertex buffers.
 */
internal class Grid(private val mW: Int, private val mH: Int, useFixedPoint: Boolean) {
    private var mFloatVertexBuffer: FloatBuffer? = null
    private var mFloatTexCoordBuffer: FloatBuffer? = null
    private var mFloatColorBuffer: FloatBuffer? = null
    private var mFixedVertexBuffer: IntBuffer? = null
    private var mFixedTexCoordBuffer: IntBuffer? = null
    private var mFixedColorBuffer: IntBuffer? = null

    private val mIndexBuffer: CharBuffer

    private var mVertexBuffer: Buffer? = null
    private var mTexCoordBuffer: Buffer? = null
    private var mColorBuffer: Buffer? = null
    private var mCoordinateSize: Int = 0
    private var mCoordinateType: Int = 0
    val indexCount: Int
    private var mUseHardwareBuffers: Boolean = false
    // These functions exposed to patch Grid info into native code.
    var vertexBuffer: Int = 0
        private set
    var indexBuffer: Int = 0
        private set
    var textureBuffer: Int = 0
        private set
    var colorBuffer: Int = 0
        private set

    init {
        if (mW < 0 || mW >= 65536) {
            throw IllegalArgumentException("vertsAcross")
        }
        if (mH < 0 || mH >= 65536) {
            throw IllegalArgumentException("vertsDown")
        }
        if (mW * mH >= 65536) {
            throw IllegalArgumentException("vertsAcross * vertsDown >= 65536")
        }

        mUseHardwareBuffers = false
        val size = mW * mH
        val FLOAT_SIZE = 4
        val FIXED_SIZE = 4
        val CHAR_SIZE = 2

        if (useFixedPoint) {
            mFixedVertexBuffer = ByteBuffer.allocateDirect(FIXED_SIZE * size * 3)
                    .order(ByteOrder.nativeOrder()).asIntBuffer()
            mFixedTexCoordBuffer = ByteBuffer.allocateDirect(FIXED_SIZE * size * 2)
                    .order(ByteOrder.nativeOrder()).asIntBuffer()
            mFixedColorBuffer = ByteBuffer.allocateDirect(FIXED_SIZE * size * 4)
                    .order(ByteOrder.nativeOrder()).asIntBuffer()

            mVertexBuffer = mFixedVertexBuffer
            mTexCoordBuffer = mFixedTexCoordBuffer
            mColorBuffer = mFixedColorBuffer
            mCoordinateSize = FIXED_SIZE
            mCoordinateType = GL10.GL_FIXED

        } else {
            mFloatVertexBuffer = ByteBuffer.allocateDirect(FLOAT_SIZE * size * 3)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer()
            mFloatTexCoordBuffer = ByteBuffer.allocateDirect(FLOAT_SIZE * size * 2)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer()
            mFloatColorBuffer = ByteBuffer.allocateDirect(FLOAT_SIZE * size * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer()


            mVertexBuffer = mFloatVertexBuffer
            mTexCoordBuffer = mFloatTexCoordBuffer
            mColorBuffer = mFloatColorBuffer
            mCoordinateSize = FLOAT_SIZE
            mCoordinateType = GL10.GL_FLOAT
        }


        val quadW = mW - 1
        val quadH = mH - 1
        val quadCount = quadW * quadH
        val indexCount = quadCount * 6
        this.indexCount = indexCount
        mIndexBuffer = ByteBuffer.allocateDirect(CHAR_SIZE * indexCount)
                .order(ByteOrder.nativeOrder()).asCharBuffer()

        /*
         * Initialize triangle list mesh.
         *
         *     [0]-----[  1] ...
         *      |    /   |
         *      |   /    |
         *      |  /     |
         *     [w]-----[w+1] ...
         *      |       |
         *
         */

        run {
            var i = 0
            for (y in 0..quadH - 1) {
                for (x in 0..quadW - 1) {
                    val a = (y * mW + x).toChar()
                    val b = (y * mW + x + 1).toChar()
                    val c = ((y + 1) * mW + x).toChar()
                    val d = ((y + 1) * mW + x + 1).toChar()

                    mIndexBuffer.put(i++, a)
                    mIndexBuffer.put(i++, b)
                    mIndexBuffer.put(i++, c)

                    mIndexBuffer.put(i++, b)
                    mIndexBuffer.put(i++, c)
                    mIndexBuffer.put(i++, d)
                }
            }
        }

        vertexBuffer = 0
    }

    operator fun set(i: Int, j: Int, x: Float, y: Float, z: Float, u: Float, v: Float, color: FloatArray?) {
        if (i < 0 || i >= mW) {
            throw IllegalArgumentException("i")
        }
        if (j < 0 || j >= mH) {
            throw IllegalArgumentException("j")
        }

        val index = mW * j + i

        val posIndex = index * 3
        val texIndex = index * 2
        val colorIndex = index * 4

        if (mCoordinateType == GL10.GL_FLOAT) {
            mFloatVertexBuffer!!.put(posIndex, x)
            mFloatVertexBuffer!!.put(posIndex + 1, y)
            mFloatVertexBuffer!!.put(posIndex + 2, z)

            mFloatTexCoordBuffer!!.put(texIndex, u)
            mFloatTexCoordBuffer!!.put(texIndex + 1, v)

            if (color != null) {
                mFloatColorBuffer!!.put(colorIndex, color[0])
                mFloatColorBuffer!!.put(colorIndex + 1, color[1])
                mFloatColorBuffer!!.put(colorIndex + 2, color[2])
                mFloatColorBuffer!!.put(colorIndex + 3, color[3])
            }
        } else {
            mFixedVertexBuffer!!.put(posIndex, (x * (1 shl 16)).toInt())
            mFixedVertexBuffer!!.put(posIndex + 1, (y * (1 shl 16)).toInt())
            mFixedVertexBuffer!!.put(posIndex + 2, (z * (1 shl 16)).toInt())

            mFixedTexCoordBuffer!!.put(texIndex, (u * (1 shl 16)).toInt())
            mFixedTexCoordBuffer!!.put(texIndex + 1, (v * (1 shl 16)).toInt())

            if (color != null) {
                mFixedColorBuffer!!.put(colorIndex, (color[0] * (1 shl 16)).toInt())
                mFixedColorBuffer!!.put(colorIndex + 1, (color[1] * (1 shl 16)).toInt())
                mFixedColorBuffer!!.put(colorIndex + 2, (color[2] * (1 shl 16)).toInt())
                mFixedColorBuffer!!.put(colorIndex + 3, (color[3] * (1 shl 16)).toInt())
            }
        }
    }


    fun draw(gl: GL10, useTexture: Boolean, useColor: Boolean) {
        if (!mUseHardwareBuffers) {
            gl.glVertexPointer(3, mCoordinateType, 0, mVertexBuffer)

            if (useTexture) {
                gl.glTexCoordPointer(2, mCoordinateType, 0, mTexCoordBuffer)
            }

            if (useColor) {
                gl.glColorPointer(4, mCoordinateType, 0, mColorBuffer)
            }

            gl.glDrawElements(GL10.GL_TRIANGLES, indexCount,
                    GL10.GL_UNSIGNED_SHORT, mIndexBuffer)
        } else {
            val gl11 = gl as GL11
            // draw using hardware buffers
            gl11.glBindBuffer(GL11.GL_ARRAY_BUFFER, vertexBuffer)
            gl11.glVertexPointer(3, mCoordinateType, 0, 0)

            if (useTexture) {
                gl11.glBindBuffer(GL11.GL_ARRAY_BUFFER, textureBuffer)
                gl11.glTexCoordPointer(2, mCoordinateType, 0, 0)
            }

            if (useColor) {
                gl11.glBindBuffer(GL11.GL_ARRAY_BUFFER, colorBuffer)
                gl11.glColorPointer(4, mCoordinateType, 0, 0)
            }

            gl11.glBindBuffer(GL11.GL_ELEMENT_ARRAY_BUFFER, indexBuffer)
            gl11.glDrawElements(GL11.GL_TRIANGLES, indexCount,
                    GL11.GL_UNSIGNED_SHORT, 0)

            gl11.glBindBuffer(GL11.GL_ARRAY_BUFFER, 0)
            gl11.glBindBuffer(GL11.GL_ELEMENT_ARRAY_BUFFER, 0)


        }
    }

    fun usingHardwareBuffers(): Boolean {
        return mUseHardwareBuffers
    }

    /**
     * When the OpenGL ES device is lost, GL handles become invalidated.
     * In that case, we just want to "forget" the old handles (without
     * explicitly deleting them) and make new ones.
     */
    fun invalidateHardwareBuffers() {
        vertexBuffer = 0
        indexBuffer = 0
        textureBuffer = 0
        colorBuffer = 0
        mUseHardwareBuffers = false
    }

    /**
     * Deletes the hardware buffers allocated by this object (if any).
     */
    fun releaseHardwareBuffers(gl: GL10) {
        if (mUseHardwareBuffers) {
            if (gl is GL11) {
                val buffer = IntArray(1)
                buffer[0] = vertexBuffer
                gl.glDeleteBuffers(1, buffer, 0)

                buffer[0] = textureBuffer
                gl.glDeleteBuffers(1, buffer, 0)

                buffer[0] = colorBuffer
                gl.glDeleteBuffers(1, buffer, 0)

                buffer[0] = indexBuffer
                gl.glDeleteBuffers(1, buffer, 0)
            }

            invalidateHardwareBuffers()
        }
    }

    /**
     * Allocates hardware buffers on the graphics card and fills them with
     * data if a buffer has not already been previously allocated.  Note that
     * this function uses the GL_OES_vertex_buffer_object extension, which is
     * not guaranteed to be supported on every device.
     * @param gl  A pointer to the OpenGL ES context.
     */
    fun generateHardwareBuffers(gl: GL10) {
        if (!mUseHardwareBuffers) {
            if (gl is GL11) {
                val buffer = IntArray(1)

                // Allocate and fill the vertex buffer.
                gl.glGenBuffers(1, buffer, 0)
                vertexBuffer = buffer[0]
                gl.glBindBuffer(GL11.GL_ARRAY_BUFFER, vertexBuffer)
                val vertexSize = mVertexBuffer!!.capacity() * mCoordinateSize
                gl.glBufferData(GL11.GL_ARRAY_BUFFER, vertexSize,
                        mVertexBuffer, GL11.GL_STATIC_DRAW)

                // Allocate and fill the texture coordinate buffer.
                gl.glGenBuffers(1, buffer, 0)
                textureBuffer = buffer[0]
                gl.glBindBuffer(GL11.GL_ARRAY_BUFFER,
                        textureBuffer)
                val texCoordSize = mTexCoordBuffer!!.capacity() * mCoordinateSize
                gl.glBufferData(GL11.GL_ARRAY_BUFFER, texCoordSize,
                        mTexCoordBuffer, GL11.GL_STATIC_DRAW)

                // Allocate and fill the color buffer.
                gl.glGenBuffers(1, buffer, 0)
                colorBuffer = buffer[0]
                gl.glBindBuffer(GL11.GL_ARRAY_BUFFER,
                        colorBuffer)
                val colorSize = mColorBuffer!!.capacity() * mCoordinateSize
                gl.glBufferData(GL11.GL_ARRAY_BUFFER, colorSize,
                        mColorBuffer, GL11.GL_STATIC_DRAW)

                // Unbind the array buffer.
                gl.glBindBuffer(GL11.GL_ARRAY_BUFFER, 0)

                // Allocate and fill the index buffer.
                gl.glGenBuffers(1, buffer, 0)
                indexBuffer = buffer[0]
                gl.glBindBuffer(GL11.GL_ELEMENT_ARRAY_BUFFER,
                        indexBuffer)
                // A char is 2 bytes.
                val indexSize = mIndexBuffer.capacity() * 2
                gl.glBufferData(GL11.GL_ELEMENT_ARRAY_BUFFER, indexSize, mIndexBuffer,
                        GL11.GL_STATIC_DRAW)

                // Unbind the element array buffer.
                gl.glBindBuffer(GL11.GL_ELEMENT_ARRAY_BUFFER, 0)

                mUseHardwareBuffers = true

                assert(vertexBuffer != 0)
                assert(textureBuffer != 0)
                assert(indexBuffer != 0)
                assert(gl.glGetError() == 0)


            }
        }
    }

    val fixedPoint: Boolean
        get() = mCoordinateType == GL10.GL_FIXED

    companion object {

        fun beginDrawing(gl: GL10, useTexture: Boolean, useColor: Boolean) {
            gl.glEnableClientState(GL10.GL_VERTEX_ARRAY)

            if (useTexture) {
                gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY)
                gl.glEnable(GL10.GL_TEXTURE_2D)
            } else {
                gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY)
                gl.glDisable(GL10.GL_TEXTURE_2D)
            }

            if (useColor) {
                gl.glEnableClientState(GL10.GL_COLOR_ARRAY)
            } else {
                gl.glDisableClientState(GL10.GL_COLOR_ARRAY)
            }
        }

        fun endDrawing(gl: GL10) {
            gl.glDisableClientState(GL10.GL_VERTEX_ARRAY)
        }
    }

}

/**
 * Base class defining the core set of information necessary to render (and move
 * an object on the screen.  This is an abstract type and must be derived to
 * add methods to actually draw (see CanvasSprite and GLSprite).
 */
internal abstract class Renderable {
    // Position.
    var x: Float = 0.toFloat()
    var y: Float = 0.toFloat()
    var z: Float = 0.toFloat()

    // Velocity.
    var velocityX: Float = 0.toFloat()
    var velocityY: Float = 0.toFloat()
    var velocityZ: Float = 0.toFloat()

    // Size.
    var width: Float = 0.toFloat()
    var height: Float = 0.toFloat()
}
