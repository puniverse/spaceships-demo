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

import co.paralleluniverse.spacebase.AABB;
import static co.paralleluniverse.spacebase.AABB.X;
import static co.paralleluniverse.spacebase.AABB.Y;
import co.paralleluniverse.spacebase.MutableAABB;
import co.paralleluniverse.spacebase.SpaceBase;
import co.paralleluniverse.spacebase.SpatialQueries;
import co.paralleluniverse.spacebase.SpatialQuery;
import co.paralleluniverse.spacebase.SpatialToken;
import co.paralleluniverse.spacebase.SpatialVisitor;
import co.paralleluniverse.spaceships.Spaceship;
import co.paralleluniverse.spaceships.Spaceships;
import com.jogamp.newt.awt.NewtCanvasAWT;
import com.jogamp.newt.opengl.GLWindow;
import com.jogamp.opengl.util.FPSAnimator;
import com.jogamp.opengl.util.PMVMatrix;
import com.jogamp.opengl.util.glsl.ShaderCode;
import com.jogamp.opengl.util.glsl.ShaderProgram;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureIO;
import java.awt.Component;
import java.awt.Frame;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.Collection;
import java.util.Collections;
import javax.media.opengl.DebugGL3;
import javax.media.opengl.GL;
import javax.media.opengl.GL2ES2;
import javax.media.opengl.GL3;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLCapabilitiesImmutable;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.GLException;
import javax.media.opengl.GLProfile;
import javax.media.opengl.awt.GLCanvas;
import javax.media.opengl.fixedfunc.GLMatrixFunc;

/**
 * See:
 * http://www.lighthouse3d.com/tutorials/glsl-core-tutorial/3490-2/
 * http://www.arcsynthesis.org/gltut/Positioning/Tut07%20Shared%20Uniforms.html
 * http://www.jotschi.de/?p=427
 *
 * @author pron
 */
public class GLPort implements GLEventListener {
    public static final int WINDOW_WIDTH = 1200;
    public static final int WINDOW_HEIGHT = 700;
    public static final double ZOOM_UNIT = 0.1;
    public static final int ANIMATION_DURATION = 200;
    public static final int SHOOT_DURATION = 100;
    public static final float EXPLOSION_DURATION = 1000f;
    public static final int MAX_EXTRAPOLATION_DURATION = 1000;
    public static final int SB_QUERY_RATE = 250;
    public static final int WIDTH_MARGINS = 800;
    public static final int MAX_PORT_WIDTH = 400;
    public static final String WINDOW_TITLE = "Spaceships";
    private long lastSBQueryTime = 0;
    private Collection<Object> lastSBQueryResult = null;
    private long lastSBCycleStart = 0;
    private Texture spaceshipTex;
    private Texture explosionTex;
    private ShaderProgram shaderProgram;
    private int drawableWidth;
    private int drawableHeight;
    private long portAnimationStartTime = 0;
    private double portMaxXAnimation = 0;
    private double portMinXAnimation = 0;
    private double portMaxYAnimation = 0;
    private double portMinYAnimation = 0;
    private double heading = 0;

    private MutableAABB getCurrentPort(final long ct) {
        if (portMaxXAnimation == 0)
            return port;
        MutableAABB currentPort = MutableAABB.create(2);
        final double width = port.max(X) - port.min(X);
        final double height = port.max(Y) - port.min(Y);
        final double ratio = height / width;
        double animation = Math.min(1.0, (double) (ct - portAnimationStartTime) / ANIMATION_DURATION);
        currentPort.min(X, port.min(X) + animation * portMinXAnimation);
        currentPort.min(Y, port.min(Y) + animation * portMinYAnimation);
        currentPort.max(X, port.max(X) + animation * portMaxXAnimation);
        currentPort.max(Y, port.max(Y) + animation * portMaxYAnimation);
        return currentPort;
    }

    private void fixPort(final long ct, boolean onlyIfFinished) {
        final double width = port.max(X) - port.min(X);
        final double height = port.max(Y) - port.min(Y);
        final double ratio = height / width;
        double animation = Math.min(1.0, (double) (ct - portAnimationStartTime) / ANIMATION_DURATION);
        if (onlyIfFinished & animation < 1.0)
            return;
        port.min(X, port.min(X) + animation * portMinXAnimation);
        port.min(Y, port.min(Y) + animation * portMinYAnimation);
        port.max(X, port.max(X) + animation * portMaxXAnimation);
        port.max(Y, port.max(Y) + animation * portMaxYAnimation);
        portMaxXAnimation -= animation * portMaxXAnimation;
        portMinXAnimation -= animation * portMinXAnimation;
        portMaxYAnimation -= animation * portMaxYAnimation;
        portMinYAnimation -= animation * portMinYAnimation;
        portAnimationStartTime = ct;
    }

    public enum Toolkit {
        NEWT, NEWT_CANVAS, AWT
    };
    private final Toolkit TOOLKIT;
    //
    private static final float KEY_PRESS_TRANSLATE = 10.0f;
    private final Object window;
    private final int maxItems;
    private final SpaceBase<Spaceship> sb;
    private final AABB bounds;
    private MutableAABB port = MutableAABB.create(2);
    private ProgramState shaderState;
    private VAO vao;
    private VBO vertices;
    private VBO colors;
    private PMVMatrix pmv = new PMVMatrix();
    private float x = 1.0f;
    private final FPSAnimator animator;
    private final Spaceships global;

    static {
        GLProfile.initSingleton();
    }

    public GLPort(Toolkit toolkit, int maxItems, Spaceships global, AABB bounds) {
        TOOLKIT = toolkit;
        this.maxItems = maxItems;
        this.global = global;
        this.sb = global.sb;
        this.bounds = bounds;

        final GLProfile glp = GLProfile.get(GLProfile.GL3);
        final GLCapabilitiesImmutable glcaps = (GLCapabilitiesImmutable) new GLCapabilities(glp);
        final GLAutoDrawable drawable;
        final GLCapabilities tGLCapabilities = new GLCapabilities(glp);
        tGLCapabilities.setSampleBuffers(true);
        tGLCapabilities.setNumSamples(1);

//            tGLCapabilities.setAccumAlphaBits(16);
//            tGLCapabilities.setAccumBlueBits(16);
//            tGLCapabilities.setAccumGreenBits(16);
//            tGLCapabilities.setAccumRedBits(16);

        if (TOOLKIT == Toolkit.NEWT || TOOLKIT == Toolkit.NEWT_CANVAS) {
            final GLWindow newt = GLWindow.create(glcaps);

            final NewtListener listener = new NewtListener();
            newt.addKeyListener(listener);
            newt.addMouseListener(listener);

            drawable = newt;
        } else {
            final GLCanvas glCanvas = new GLCanvas(glcaps);

            final AwtListener listener = new AwtListener();
            glCanvas.addKeyListener(listener);
            glCanvas.addMouseListener(listener);
            glCanvas.addMouseMotionListener(listener);
            glCanvas.addMouseWheelListener(listener);

            drawable = glCanvas;
        }

        drawable.addGLEventListener(this);
        animator = new FPSAnimator(drawable, 60);

        if (TOOLKIT == Toolkit.NEWT) {
            final GLWindow window = (GLWindow) drawable;

            window.addWindowListener(new com.jogamp.newt.event.WindowAdapter() {
                @Override
                public void windowDestroyNotify(com.jogamp.newt.event.WindowEvent arg0) {
                    animator.stop();
                    System.exit(0);
                }
            });
            window.setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
            window.setTitle(WINDOW_TITLE);
            window.setVisible(true);
            this.window = window;
        } else {
            final Component canvas;

            if (TOOLKIT == Toolkit.NEWT_CANVAS)
                canvas = new NewtCanvasAWT((GLWindow) drawable);
            else
                canvas = (GLCanvas) drawable;

            final Frame window = new Frame();

            window.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent windowevent) {
                    animator.stop();
                    window.remove(canvas);
                    window.dispose();
                    System.exit(0);
                }
            });

            window.add(canvas);
            window.pack();
            canvas.requestFocusInWindow();
            window.setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
            window.setTitle(WINDOW_TITLE);
            window.setVisible(true);
            this.window = window;
        }

        animator.start();
    }

    private void setTitle(String title) {
        if (TOOLKIT == Toolkit.NEWT)
            ((GLWindow) window).setTitle(title);
        else
            ((Frame) window).setTitle(title);
    }

    @Override
    public void init(GLAutoDrawable drawable) {
        drawable.setGL(new DebugGL3(drawable.getGL().getGL3()));

        final GL3 gl = drawable.getGL().getGL3();
        try {
            spaceshipTex = TextureIO.newTexture(TextureIO.newTextureData(GLProfile.get(GLProfile.GL3), new FileInputStream("spaceship.png"), false, "png"));
            explosionTex = TextureIO.newTexture(TextureIO.newTextureData(GLProfile.get(GLProfile.GL3), new FileInputStream("explosion.png"), false, "png"));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        drawableWidth = drawable.getWidth();
        drawableHeight = drawable.getHeight();
        port.min(X, -drawable.getWidth() / 2);
        port.max(X, drawable.getWidth() / 2);
        port.min(Y, -drawable.getHeight() / 2);
        port.max(Y, drawable.getHeight() / 2);
        //gl.glEnable(gl.GL_VERTEX_PROGRAM_POINT_SIZE);
        gl.glViewport(0, 0, (int) (port.max(X) - port.min(X)), (int) (port.max(Y) - port.min(Y)));
        gl.glClearColor(0, 0, 0, 1);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);

        ShaderCode vertexShader = ShaderCode.create(gl, GL3.GL_VERTEX_SHADER, this.getClass(), "shader", null, "vertex", false);
        ShaderCode geometryShader = ShaderCode.create(gl, GL3.GL_GEOMETRY_SHADER, this.getClass(), "shader", null, "geometry", false);
        ShaderCode fragmentShader = ShaderCode.create(gl, GL3.GL_FRAGMENT_SHADER, this.getClass(), "shader", null, "fragment", false);

        if (!vertexShader.compile(gl, System.err))
            throw new GLException("Couldn't compile shader: " + vertexShader);
        if (!geometryShader.compile(gl, System.err))
            throw new GLException("Couldn't compile shader: " + geometryShader);
        if (!fragmentShader.compile(gl, System.err))
            throw new GLException("Couldn't compile shader: " + fragmentShader);

        shaderProgram = new ShaderProgram();
        shaderProgram.add(gl, vertexShader, System.err);
        shaderProgram.add(gl, geometryShader, System.err);
        shaderProgram.add(gl, fragmentShader, System.err);
        if (!shaderProgram.link(gl, System.err))
            throw new GLException("Couldn't link program: " + shaderProgram);

        this.shaderState = new ProgramState(gl, shaderProgram);
        shaderState.bind(gl);

        pmv.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
        pmv.glLoadIdentity();

        this.vao = shaderState.createVAO(gl);
        vao.bind(gl);

        this.vertices = new VBO(gl, 2, GL3.GL_FLOAT, false, maxItems, GL3.GL_DYNAMIC_DRAW);
        this.colors = new VBO(gl, 3, GL3.GL_FLOAT, false, maxItems, GL3.GL_DYNAMIC_DRAW);

        vao.setVertex(gl, "in_Position", vertices);
        vao.setVertex(gl, "in_Vertex", colors);

        shaderState.unbind(gl);
    }

    private void portToMvMatrix(MutableAABB cp) {
        pmv.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
        pmv.glLoadIdentity();
        pmv.glScalef((float) (2.0 / (cp.max(X) - cp.min(X))), (float) (2.0 / (cp.max(Y) - cp.min(Y))), 1.0f);
        pmv.glTranslatef((float) (-(cp.max(X) + cp.min(X)) / 2.0), (float) (-(cp.max(Y) + cp.min(Y)) / 2.0), 0f);
    }

    @Override
    public void dispose(GLAutoDrawable drawable) {
        final GL3 gl = drawable.getGL().getGL3();

        shaderState.bind(gl);
        shaderState.destroy(gl);
        vertices.destroy(gl);
    }

    public Collection<Object> query(SpatialQuery<? super Spaceship> query) {
        try {
            final Collection<Object> resultSet = sb.createCollection();
            sb.query(query, new SpatialVisitor<Spaceship>() {
                @Override
                public void visit(Spaceship elem, SpatialToken token) {
                    resultSet.add(elem);
                }

                @Override
                public void done() {
                }
            }).join();
            return Collections.unmodifiableCollection(resultSet);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void display(GLAutoDrawable drawable) {
        final GL3 gl = drawable.getGL().getGL3();
        shaderState.bind(gl);
        vao.bind(gl);
        int spaceshipLoc = gl.glGetUniformLocation(shaderProgram.program(), "spaceshipTex");
        gl.glUniform1i(spaceshipLoc, 0);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, spaceshipTex.getTextureObject(gl));

        int explosionLoc = gl.glGetUniformLocation(shaderProgram.program(), "explosionTex");
        gl.glUniform1i(explosionLoc, 1);
        gl.glActiveTexture(GL.GL_TEXTURE0 + 1);
        gl.glBindTexture(GL.GL_TEXTURE_2D, explosionTex.getTextureObject(gl));

        vertices.clear();
        colors.clear();
        final FloatBuffer verticesb = (FloatBuffer) vertices.getBuffer();
        final FloatBuffer colorsb = (FloatBuffer) colors.getBuffer();
        long ct = System.currentTimeMillis();
        fixPort(ct, true);
        MutableAABB currentPort = getCurrentPort(ct);
        portToMvMatrix(currentPort);
        double margins = WIDTH_MARGINS;
        if (ct - lastSBQueryTime > SB_QUERY_RATE | lastSBCycleStart != global.getCycleStart()) {
            lastSBQueryTime = ct;
            lastSBQueryResult = query(SpatialQueries.contained(AABB.create(currentPort.min(X) - margins, currentPort.max(X) + margins, currentPort.min(Y) - margins, currentPort.max(Y) + margins)));
            lastSBCycleStart = global.getCycleStart();
        }
        double[] pos;
        int countInPort = 0;
        for (Object o : lastSBQueryResult) {
            Spaceship s = (Spaceship) o;
            if (s.getLastMoved() > 0) {
                long exrapolationTime;

                if (global.extrapolate)
                    // don't extrapolate spaceships that have npt been moved a long time
                    exrapolationTime = Math.min(ct, s.getLastMoved() + MAX_EXTRAPOLATION_DURATION);
                else
                    exrapolationTime = s.getLastMoved();
                pos = s.getCurrentPosition(exrapolationTime);
                verticesb.put((float) pos[0]);
                verticesb.put((float) pos[1]);

                if (s.getBlowTime() > 0) { // 0.01 - start blow animation, 1.0 - end od animation
                    colorsb.put(Math.min(1.0f, (ct - s.getBlowTime()) / EXPLOSION_DURATION));
                } else
                    colorsb.put(0); // regular ship without blow
                colorsb.put((float) s.getCurrentHeading(exrapolationTime));

                // put the shootLength (0 for ship wihout shoot)
                colorsb.put(ct - s.getShootTime() < SHOOT_DURATION ? (float) s.getShootLength() : 0f);
            }
            if (port.contains(s.getAABB()))
                countInPort++;
        }
        setTitle(""+countInPort+ " Spaceships " + (int)(port.max(X) - port.min(X)) + "x" + (int)(port.max(Y) - port.min(Y)) );            

        vertices.flip();
        colors.flip();

        int numElems = verticesb.limit() / 2;
        vertices.write(gl, 0, numElems);
        colors.write(gl, 0, numElems);

        shaderState.setUniform(gl, "in_Matrix", 4, 4, pmv.glGetMvMatrixf());

        gl.glClear(GL3.GL_COLOR_BUFFER_BIT);
        gl.glDrawArrays(GL3.GL_POINTS, 0, numElems);

        vao.unbind(gl);
        shaderState.unbind(gl);
    }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        final GL2ES2 gl = drawable.getGL().getGL2ES2();

        gl.glViewport(0, 0, width, height);
        port.max(X, port.min(X) + (double) width / drawableWidth * (port.max(X) - port.min(X)));
        port.max(Y, port.min(Y) + (double) height / drawableHeight * (port.max(Y) - port.min(Y)));
        drawableHeight = height;
        drawableWidth = width;
        portToMvMatrix(port);
    }

    private void movePort(boolean horizontal, double units) {
        //pmv.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
        final long ct = System.currentTimeMillis();
        fixPort(ct, false);

        final double width = port.max(X) - port.min(X);
        final double height = port.max(Y) - port.min(Y);

        double moveStep = units * KEY_PRESS_TRANSLATE;

        if (horizontal) {
            int dim = X;
            if (port.min(dim) + portMinXAnimation + moveStep < bounds.min(dim)) {
                moveStep = bounds.min(dim) - port.min(dim);
            } else if (port.max(dim) + portMaxXAnimation + moveStep > bounds.max(dim)) {
                moveStep = bounds.max(dim) - port.max(dim);
            }
            portMinXAnimation += moveStep;
            portMaxXAnimation += moveStep;
        } else {
            int dim = Y;
            if (port.min(dim) + portMinYAnimation + moveStep < bounds.min(dim)) {
                moveStep = bounds.min(dim) - port.min(dim);
            } else if (port.max(dim) + portMaxYAnimation + moveStep > bounds.max(dim)) {
                moveStep = bounds.max(dim) - port.max(dim);
            }
            portMinYAnimation += moveStep;
            portMaxYAnimation += moveStep;
        }
    }

    private void scalePort(double units) {
        final long ct = System.currentTimeMillis();
        fixPort(ct, false);
        final double width = port.max(X) - port.min(X);
        final double height = port.max(Y) - port.min(Y);
        final double ratio = height / width;
        final double widthToAdd = width * ZOOM_UNIT * units;
        final double heightToAdd = width * ZOOM_UNIT * units * ratio;

        if (units < 0) {
            if ((width + 2 * widthToAdd + portMaxXAnimation - portMinXAnimation > MAX_PORT_WIDTH)
                    & (height + 2 * heightToAdd + portMaxYAnimation - portMinYAnimation > MAX_PORT_WIDTH * ratio)) {
                portMaxXAnimation += widthToAdd;
                portMinXAnimation -= widthToAdd;
                portMaxYAnimation += heightToAdd;
                portMinYAnimation -= heightToAdd;
            }
        } else { // zoomout
            if ((bounds.min(X) < port.min(X) + portMinXAnimation - widthToAdd)
                    & (bounds.min(Y) < port.min(Y) + portMinYAnimation - heightToAdd)
                    & (bounds.max(X) > port.max(X) + portMaxXAnimation + widthToAdd)
                    & (bounds.max(Y) > port.max(Y) + portMaxYAnimation + heightToAdd)) {
                portMaxXAnimation += widthToAdd;
                portMinXAnimation -= widthToAdd;
                portMaxYAnimation += heightToAdd;
                portMinYAnimation -= heightToAdd;
            }
        }
    }

    public void myKeyPressed(int keyCode) {
        switch (keyCode) {
            case com.jogamp.newt.event.KeyEvent.VK_UP:
                movePort(false, 5);
                break;
            case com.jogamp.newt.event.KeyEvent.VK_DOWN:
                movePort(false, -5);
                break;
            case com.jogamp.newt.event.KeyEvent.VK_LEFT:
                movePort(true, -5);
                break;
            case com.jogamp.newt.event.KeyEvent.VK_RIGHT:
                movePort(true, 5);
                break;
            case com.jogamp.newt.event.KeyEvent.VK_EQUALS:
                scalePort(-1); // reduce port
                break;
            case com.jogamp.newt.event.KeyEvent.VK_MINUS:
                scalePort(+1); //extend port
                break;
        }
    }

    public void myMouseWheelMoved(boolean idControlDown, float wheelRotation, boolean isShiftDown) {
        if (idControlDown) {
            scalePort((int) Math.signum(wheelRotation));
        } else {
            movePort(isShiftDown, -1 * (isShiftDown ? 1 : -1) * wheelRotation);
        }
    }

    private class AwtListener implements java.awt.event.KeyListener, java.awt.event.MouseListener, java.awt.event.MouseMotionListener, java.awt.event.MouseWheelListener {
        @Override
        public void keyPressed(java.awt.event.KeyEvent e) {
            int keyCode = e.getKeyCode();
            myKeyPressed(keyCode);
        }

        @Override
        public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
            final boolean idControlDown = e.isControlDown();
            final boolean isShiftDown = e.isShiftDown();
            final float wheelRotation = e.getWheelRotation();
            myMouseWheelMoved(idControlDown, wheelRotation, isShiftDown);
        }

        @Override
        public void keyTyped(java.awt.event.KeyEvent e) {
        }

        @Override
        public void keyReleased(java.awt.event.KeyEvent e) {
        }

        @Override
        public void mouseClicked(java.awt.event.MouseEvent e) {
        }

        @Override
        public void mousePressed(java.awt.event.MouseEvent e) {
        }

        @Override
        public void mouseReleased(java.awt.event.MouseEvent e) {
        }

        @Override
        public void mouseEntered(java.awt.event.MouseEvent e) {
        }

        @Override
        public void mouseExited(java.awt.event.MouseEvent e) {
        }

        @Override
        public void mouseDragged(java.awt.event.MouseEvent e) {
        }

        @Override
        public void mouseMoved(java.awt.event.MouseEvent e) {
        }
    }

    private class NewtListener implements com.jogamp.newt.event.KeyListener, com.jogamp.newt.event.MouseListener {
        @Override
        public void keyPressed(com.jogamp.newt.event.KeyEvent e) {
            int keyCode = e.getKeyCode();
            myKeyPressed(keyCode);
        }

        @Override
        public void mouseWheelMoved(com.jogamp.newt.event.MouseEvent e) {
            final boolean idControlDown = e.isControlDown();
            final boolean isShiftDown = e.isShiftDown();
            final float wheelRotation = e.getWheelRotation();
            myMouseWheelMoved(idControlDown, wheelRotation, isShiftDown);
        }

        @Override
        public void keyReleased(com.jogamp.newt.event.KeyEvent e) {
        }

        @Override
        public void keyTyped(com.jogamp.newt.event.KeyEvent e) {
        }

        @Override
        public void mouseClicked(com.jogamp.newt.event.MouseEvent e) {
        }

        @Override
        public void mouseEntered(com.jogamp.newt.event.MouseEvent e) {
        }

        @Override
        public void mouseExited(com.jogamp.newt.event.MouseEvent e) {
        }

        @Override
        public void mousePressed(com.jogamp.newt.event.MouseEvent e) {
        }

        @Override
        public void mouseReleased(com.jogamp.newt.event.MouseEvent e) {
        }

        @Override
        public void mouseMoved(com.jogamp.newt.event.MouseEvent e) {
        }

        @Override
        public void mouseDragged(com.jogamp.newt.event.MouseEvent e) {
        }
    }

//    private void print(FloatBuffer buffer) {
//        int pos = buffer.position();
//        System.err.print(buffer.remaining() + ": ");
//        while (buffer.position() < buffer.limit())
//            System.err.print(buffer.get() + ", ");
//        System.err.println();
//        buffer.position(pos);
//    }
}
