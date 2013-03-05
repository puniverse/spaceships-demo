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
import com.jogamp.opengl.util.glsl.ShaderProgram;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.media.opengl.GL3;
import javax.media.opengl.GLException;

/**
 * See: 
 *   http://www.lighthouse3d.com/tutorials/glsl-core-tutorial/3490-2/
 *   http://www.arcsynthesis.org/gltut/Positioning/Tut07%20Shared%20Uniforms.html
 *   http://www.jotschi.de/?p=427
 * 
 * @author pron
 */
public class UBO {
    private final AtomicInteger BINDING_POINT_GEN = new AtomicInteger(0);
    
    private final ShaderProgram shader;
    private final String blockName;
    private final int ubo;
    private final int bindingPoint;
    private final ByteBuffer buffer;
    private final Map<String, Info> attributes = Collections.synchronizedMap(new HashMap<String, Info>());

    public UBO(GL3 gl, ShaderProgram shader, String blockName) {
        this.shader = shader;

        int[] tmp = new int[1];
        
        gl.glGenBuffers(1, tmp, 0);
        this.ubo = tmp[0];
        
        this.blockName = blockName;
        final int blockIndex = gl.glGetUniformBlockIndex(shader.program(), blockName);
        if(blockIndex < 0)
            throw new GLException("Uniform block " + blockName + " not found in program " + shader.program());
        gl.glGetActiveUniformBlockiv(shader.program(), blockIndex, gl.GL_UNIFORM_BLOCK_DATA_SIZE, tmp, 0);
        final int blockSize = tmp[0];
        
        gl.glBindBuffer(gl.GL_UNIFORM_BUFFER, ubo);
        gl.glBufferData(gl.GL_UNIFORM_BUFFER, blockSize, null, gl.GL_DYNAMIC_DRAW);
        this.buffer = GLBuffers.newDirectByteBuffer(blockSize);
        buffer.limit(blockSize);
        
        this.bindingPoint = BINDING_POINT_GEN.getAndIncrement();
        
        gl.glBindBufferBase(gl.GL_UNIFORM_BUFFER, bindingPoint, ubo);
        
        attachProgram(gl, shader, blockName);
    }

    public int getBindingPoint() {
        return bindingPoint;
    }

    public final void attachProgram(GL3 gl, ShaderProgram shader, String blockName) {
        final int blockIndex  = gl.glGetUniformBlockIndex(shader.program(), blockName);
        gl.glUniformBlockBinding(shader.program(), blockIndex, bindingPoint);
    }
    public UBO bind(GL3 gl) {
        gl.glBindBuffer(gl.GL_UNIFORM_BUFFER, ubo);
        return this;
    }

    public void unbind(GL3 gl) {
        gl.glBindBuffer(gl.GL_UNIFORM_BUFFER, 0);
    }

    private Info getInfo(GL3 gl, String uniformName) {
        Info info = attributes.get(uniformName);
        if (info == null) {
            int[] tmp = new int[1];
            gl.glGetUniformIndices(shader.program(), 1, new String[]{blockName + "." + uniformName}, tmp, 0);
            final int index = tmp[0];
            if (index < 0)
                throw new GLException("Uniform " + blockName + "." + uniformName + " not found");
            
            final int[] indices = new int[]{index};
            gl.glGetActiveUniformsiv(shader.program(), 1, indices, 0, gl.GL_UNIFORM_OFFSET, tmp, 0);
            final int offset = tmp[0];
            gl.glGetActiveUniformsiv(shader.program(), 1, indices, 0, gl.GL_UNIFORM_TYPE, tmp, 0);
            final int type = tmp[0];
            gl.glGetActiveUniformsiv(shader.program(), 1, indices, 0, gl.GL_UNIFORM_SIZE, tmp, 0);
            final int size = tmp[0];
            
            info = new Info(offset, type, size);
            attributes.put(uniformName, info);
        }
        return info;
    }

    private void write(GL3 gl, Info info) {
        final int size = info.size * BO.sizeOfGLType(info.type);
        gl.glBufferSubData(gl.GL_UNIFORM_BUFFER, info.offset, size, Buffers.slice(buffer, info.offset, size));
    }
    
    private Info verifySize(GL3 gl, String uniformName, int size) {
        final Info info = getInfo(gl, uniformName);
        final int requiredSize = info.size * BO.sizeOfGLType(info.type);
        if(requiredSize != size)
            throw new GLException("Trying to write " + size + " bytes into uniform " + uniformName + " which is of size " + requiredSize);
        return info;
    }
    
    public void set(GL3 gl, String attributeName, int value) {
        final Info info = verifySize(gl, attributeName, Buffers.SIZEOF_INT);
        buffer.position(info.offset);
        buffer.putInt(value);
        buffer.rewind();
        write(gl, info);
    }

    public void set(GL3 gl, String attributeName, int v0, int v1) {
        final Info info = verifySize(gl, attributeName, 2 * Buffers.SIZEOF_INT);
        buffer.position(info.offset);
        buffer.putInt(v0);
        buffer.putInt(v1);
        buffer.rewind();
        write(gl, info);
    }

    public void set(GL3 gl, String attributeName, int v0, int v1, int v2) {
        final Info info = verifySize(gl, attributeName, 3 * Buffers.SIZEOF_INT);
        buffer.position(info.offset);
        buffer.putInt(v0);
        buffer.putInt(v1);
        buffer.putInt(v2);
        buffer.rewind();
        write(gl, info);
    }

    public void set(GL3 gl, String attributeName, int v0, int v1, int v2, int v3) {
        final Info info = verifySize(gl, attributeName, 4 * Buffers.SIZEOF_INT);
        buffer.position(info.offset);
        buffer.putInt(v0);
        buffer.putInt(v1);
        buffer.putInt(v2);
        buffer.putInt(v3);
        buffer.rewind();
        write(gl, info);
    }

    public void set(GL3 gl, String attributeName, float value) {
        final Info info = verifySize(gl, attributeName, Buffers.SIZEOF_FLOAT);
        buffer.position(info.offset);
        buffer.putFloat(value);
        buffer.rewind();
        write(gl, info);
    }

    public void set(GL3 gl, String attributeName, float v0, float v1) {
        final Info info = verifySize(gl, attributeName, 2 * Buffers.SIZEOF_FLOAT);
        buffer.position(info.offset);
        buffer.putFloat(v0);
        buffer.putFloat(v1);
        buffer.rewind();
        write(gl, info);
    }

    public void set(GL3 gl, String attributeName, float v0, float v1, float v2) {
        final Info info = verifySize(gl, attributeName, 3 * Buffers.SIZEOF_FLOAT);
        buffer.position(info.offset);
        buffer.putFloat(v0);
        buffer.putFloat(v1);
        buffer.putFloat(v2);
        buffer.rewind();
        write(gl, info);
    }

    public void set(GL3 gl, String attributeName, float v0, float v1, float v2, float v3) {
        final Info info = verifySize(gl, attributeName, 4 * Buffers.SIZEOF_FLOAT);
        buffer.position(info.offset);
        buffer.putFloat(v0);
        buffer.putFloat(v1);
        buffer.putFloat(v2);
        buffer.putFloat(v3);
        buffer.rewind();
        write(gl, info);
    }

    public void set(GL3 gl, String attributeName, int components, IntBuffer buffer) {
        final Info info = verifySize(gl, attributeName, buffer.remaining() * Buffers.SIZEOF_INT);
        this.buffer.position(info.offset);
        final int pos = buffer.position();
        this.buffer.asIntBuffer().put(buffer);
        buffer.position(pos);
        this.buffer.rewind();
        write(gl, info);
    }

    public void set(GL3 gl, String attributeName, int components, FloatBuffer buffer) {
        final Info info = verifySize(gl, attributeName, buffer.remaining() * Buffers.SIZEOF_INT);
        this.buffer.position(info.offset);
        final int pos = buffer.position();
        this.buffer.asFloatBuffer().put(buffer);
        buffer.position(pos);
        this.buffer.rewind();
        write(gl, info);
    }

    public void set(GL3 gl, String attributeName, int rows, int columns, FloatBuffer buffer) {
        final Info info = verifySize(gl, attributeName, buffer.remaining() * Buffers.SIZEOF_INT);
        this.buffer.position(info.offset);
        final int pos = buffer.position();
        this.buffer.asFloatBuffer().put(buffer);
        buffer.position(pos);
        this.buffer.rewind();
        write(gl, info);
    }

    public void destroy(GL3 gl) {
        gl.glBindBufferBase(gl.GL_UNIFORM_BUFFER, bindingPoint, 0);
        gl.glBindBuffer(gl.GL_UNIFORM_BUFFER, ubo);
        gl.glDeleteBuffers(1, new int[]{ubo}, 0);
        gl.glBindBuffer(gl.GL_UNIFORM_BUFFER, 0);
    }

    private static class Info {
        final int offset;
        final int type;
        final int size;

        public Info(int offset, int type, int size) {
            this.offset = offset;
            this.type = type;
            this.size = size;
        }
    }
}
