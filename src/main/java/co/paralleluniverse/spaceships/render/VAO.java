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

import com.jogamp.opengl.util.glsl.ShaderProgram;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.media.opengl.GL3;
import javax.media.opengl.GLException;

/**
 *
 * @author pron
 */
public class VAO {

    private final ShaderProgram shader;
    private final int vao;
    private final Map<String, Integer> attributes = Collections.synchronizedMap(new HashMap<String, Integer>());

    public VAO(GL3 gl, ShaderProgram shader) {
        this.shader = shader;

        int[] vao1 = new int[1];
        gl.glGenVertexArrays(1, vao1, 0);
        this.vao = vao1[0];
    }

    public void bind(GL3 gl) {
        gl.glBindVertexArray(vao);
    }

    public void unbind(GL3 gl) {
        gl.glBindVertexArray(0);
    }

    private int getLocation(GL3 gl, String attributeName) {
        Integer index = attributes.get(attributeName);
        if (index == null) {
            index = gl.glGetAttribLocation(shader.program(), attributeName);
            if (index < 0)
                throw new GLException("Attribute " + attributeName + " not found");
            attributes.put(attributeName, index);
        }
        return index;
    }

    public void setVertex(GL3 gl, String attributeName, VBO vbo) {
        vbo.bind(gl);
        final int location = getLocation(gl, attributeName);
        gl.glEnableVertexAttribArray(location);
        if (vbo.getBuffer() instanceof FloatBuffer)
            gl.glVertexAttribPointer(location, vbo.getComponents(), vbo.getComponentType(), vbo.isNormalized(), vbo.getStride(), 0);
        else if (vbo.getBuffer() instanceof IntBuffer)
            gl.glVertexAttribIPointer(location, vbo.getComponents(), vbo.getComponentType(), vbo.getStride(), 0);
        else if (vbo.getBuffer() instanceof DoubleBuffer)
            gl.glVertexAttribLPointer(location, vbo.getComponents(), vbo.getComponentType(), vbo.getStride(), 0);
        else
            throw new GLException("Unrecognized buffer type: " + vbo.getBuffer().getClass().getName());
    }

    public void destroy(GL3 gl) {
        for (int i : attributes.values())
            gl.glDisableVertexAttribArray(i);

        gl.glBindVertexArray(0);
        gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
    }

}
