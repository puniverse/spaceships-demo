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
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.media.opengl.GL3;
import javax.media.opengl.GLException;

/**
 *
 * @author pron
 */
public class ProgramState {

    private final ShaderProgram shader;
    private final Map<String, Integer> attributes = Collections.synchronizedMap(new HashMap<String, Integer>());
    private final Map<String, UBO> uniformBlocks = Collections.synchronizedMap(new HashMap<String, UBO>());

    public ProgramState(GL3 gl, ShaderProgram shader) {
        this.shader = shader;
    }

    public void bind(GL3 gl) {
        shader.useProgram(gl, true);
    }

    public void unbind(GL3 gl) {
        shader.useProgram(gl, false);
    }

    public VAO createVAO(GL3 gl) {
        return new VAO(gl, shader);
    }
    
    public UBO createUBO(GL3 gl, String uniformBlockName) {
        final UBO ubo = new UBO(gl, shader, uniformBlockName);
        uniformBlocks.put(uniformBlockName, ubo);
        ubo.bind(gl);
        return ubo;
    }
    
    public void setUBO(GL3 gl, String uniformBlockName, UBO ubo) {
        ubo.attachProgram(gl, shader, uniformBlockName);
        uniformBlocks.put(uniformBlockName, ubo);
    }
    
    public UBO getUBO(String uniformBlockName) {
        return uniformBlocks.get(uniformBlockName);
    }
    
    private int getLocation(GL3 gl, String attributeName) {
        Integer index = attributes.get(attributeName);
        if (index == null) {
            index = gl.glGetUniformLocation(shader.program(), attributeName);
            if (index < 0)
                throw new GLException("Attribute " + attributeName + " not found");
            attributes.put(attributeName, index);
        }
        return index;
    }

    public void setUniform(GL3 gl, String attributeName, int value) {
        gl.glUniform1i(getLocation(gl, attributeName), value);
    }

    public void setUniform(GL3 gl, String attributeName, int v0, int v1) {
        gl.glUniform2i(getLocation(gl, attributeName), v0, v1);
    }

    public void setUniform(GL3 gl, String attributeName, int v0, int v1, int v2) {
        gl.glUniform3i(getLocation(gl, attributeName), v0, v1, v2);
    }

    public void setUniform(GL3 gl, String attributeName, int v0, int v1, int v2, int v3) {
        gl.glUniform4i(getLocation(gl, attributeName), v0, v1, v2, v3);
    }

    public void setUniform(GL3 gl, String attributeName, float value) {
        gl.glUniform1f(getLocation(gl, attributeName), value);
    }

    public void setUniform(GL3 gl, String attributeName, float v0, float v1) {
        gl.glUniform2f(getLocation(gl, attributeName), v0, v1);
    }

    public void setUniform(GL3 gl, String attributeName, float v0, float v1, float v2) {
        gl.glUniform3f(getLocation(gl, attributeName), v0, v1, v2);
    }

    public void setUniform(GL3 gl, String attributeName, float v0, float v1, float v2, float v3) {
        gl.glUniform4f(getLocation(gl, attributeName), v0, v1, v2, v3);
    }

    public void setUniform(GL3 gl, String attributeName, int components, IntBuffer buffer) {
        final int location = getLocation(gl, attributeName);
        final int elements = buffer.remaining() / components;
        switch (components) {
            case 1:
                gl.glUniform1iv(location, elements, buffer);
                break;
            case 2:
                gl.glUniform2iv(location, elements, buffer);
                break;
            case 3:
                gl.glUniform3iv(location, elements, buffer);
                break;
            case 4:
                gl.glUniform4iv(location, elements, buffer);
                break;
            default:
                throw new GLException("glUniform vector only available for 1iv 2iv, 3iv and 4iv");
        }
    }

    public void setUniform(GL3 gl, String attributeName, int components, FloatBuffer buffer) {
        final int location = getLocation(gl, attributeName);
        final int elements = buffer.remaining() / components;
        switch (components) {
            case 1:
                gl.glUniform1fv(location, elements, buffer);
                break;
            case 2:
                gl.glUniform2fv(location, elements, buffer);
                break;
            case 3:
                gl.glUniform3fv(location, elements, buffer);
                break;
            case 4:
                gl.glUniform4fv(location, elements, buffer);
                break;
            default:
                throw new GLException("glUniform vector only available for 1fv 2fv, 3fv and 4fv");
        }
    }

    public void setUniform(GL3 gl, String attributeName, int rows, int columns, FloatBuffer buffer) {
        final int location = getLocation(gl, attributeName);
        final int elements = buffer.remaining() / (rows * columns);
        switch (columns) {
            case 2:
                gl.glUniformMatrix2fv(location, elements, false, buffer);
                break;
            case 3:
                gl.glUniformMatrix3fv(location, elements, false, buffer);
                break;
            case 4:
                gl.glUniformMatrix4fv(location, elements, false, buffer);
                break;
            default:
                throw new GLException("glUniformMatrix only available for 2fv, 3fv and 4fv");
        }
    }

    public void destroy(GL3 gl) {
        shader.useProgram(gl, false);
        shader.release(gl, true);
    }

}
