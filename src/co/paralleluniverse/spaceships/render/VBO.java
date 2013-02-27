/*
 * Copyright (C) 2013 Parallel Universe Software Co.
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package co.paralleluniverse.spaceships.render;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.util.GLBuffers;
import java.nio.Buffer;
import javax.media.opengl.GL;
import javax.media.opengl.GL2ES2;
import javax.media.opengl.GLException;

/**
 *
 * @author pron
 */
public class VBO {
    private static final int vboTarget = GL.GL_ARRAY_BUFFER;
    private final Buffer buffer;
    private final int vbo;
    private final int usage;
    private final int components;
    private final int componentType;
    private final boolean normalized;
    private final int stride;
    //private boolean written;

    /**
     * Create a VBO, using a custom GLSL array attribute name and starting with a new created Buffer object with
     * initialElementCount size
     *
     * @param components The array component number
     * @param componentType The array index GL data type
     * @param normalized Whether the data shall be normalized
     * @param numElements
     * @param vboUsage {@link GL2ES2#GL_STREAM_DRAW}, {@link GL#GL_STATIC_DRAW} or {@link GL#GL_DYNAMIC_DRAW}
     */
    public VBO(GL gl, int components, int componentType, boolean normalized, int numElements, int vboUsage) throws GLException {
        switch (componentType) {
            case GL.GL_BYTE:
            case GL.GL_UNSIGNED_BYTE:
            case GL.GL_SHORT:
            case GL.GL_UNSIGNED_SHORT:
            case GL.GL_FIXED:
                break;
            default:
                normalized = false;
        }
        this.normalized = normalized;

        switch (vboUsage) {
            case 0: // nop
            case GL.GL_STATIC_DRAW:
            case GL.GL_DYNAMIC_DRAW:
            case GL2ES2.GL_STREAM_DRAW:
                break;
            default:
                throw new GLException("invalid vboUsage: " + vboUsage + ":\n\t" + this);
        }
        this.usage = vboUsage;

        switch (vboTarget) {
            case 0: // nop
            case GL.GL_ARRAY_BUFFER:
            case GL.GL_ELEMENT_ARRAY_BUFFER:
                break;
            default:
                throw new GLException("invalid vboTarget: " + vboTarget + ":\n\t" + this);
        }

        final int componentByteSize = GLBuffers.sizeOfGLType(componentType);
        if (componentByteSize < 0)
            throw new GLException("Given componentType not supported: " + componentType + ":\n\t" + this);
        this.componentType = componentType;

        if (components <= 0)
            throw new GLException("Invalid number of components: " + components);

        this.components = components;
        this.stride = components * componentByteSize;

        this.buffer = GLBuffers.newDirectGLBuffer(componentType, components * numElements);
        buffer.clear();

        int[] tmp = new int[1];
        gl.glGenBuffers(1, tmp, 0);
        this.vbo = tmp[0];

        bind(gl);
        gl.glBufferData(vboTarget, stride * numElements, null, usage);
    }

    public Buffer getBuffer() {
        return buffer;
    }

    public boolean isNormalized() {
        return normalized;
    }

    public int getStride() {
        return stride;
    }

    public int getComponentType() {
        return componentType;
    }

    public int getComponents() {
        return components;
    }

    public void bind(GL gl) {
        gl.glBindBuffer(vboTarget, vbo);
    }

    public void write(GL gl) {
        bind(gl);

        buffer.rewind();
        gl.glBufferSubData(vboTarget, 0, buffer.remaining() * GLBuffers.sizeOfGLType(componentType), buffer);
    }

    public void write(GL gl, int offset, int elements) {
        bind(gl);
        gl.glBufferSubData(vboTarget, offset * stride, elements * stride, Buffers.slice(buffer, offset * components, elements * components));
    }

    public final int getSizeInBytes() {
        final int componentByteSize = GLBuffers.sizeOfGLType(componentType);
        return componentByteSize * (buffer.position() == 0 ? buffer.limit() : buffer.position());
    }

    public void clear() {
        buffer.clear();
    }

    public void rewind() {
        buffer.rewind();
    }

    public void flip() {
        buffer.flip();
    }

    public void position(int position) {
        buffer.position(position * components);
    }

    public void destroy(GL gl) {
        final int[] tmp = new int[]{vbo};
        gl.glDeleteBuffers(1, tmp, 0);
    }
}
