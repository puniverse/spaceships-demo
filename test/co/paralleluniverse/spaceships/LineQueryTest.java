/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package co.paralleluniverse.spaceships;

import co.paralleluniverse.spacebase.AABB;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author eitan
 */
public class LineQueryTest {
        LineDistanceQuery<Object> lq;
        public LineQueryTest() {
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }

    public void ae(boolean val,double x,double y) {
        AABB aabb = AABB.create(x,x,y,y);
        assertEquals(""+aabb,val, lq.queryElement(aabb,null));
    }
    @Test
    public void testSomeMethod() {
        int d = 5;
        lq = new LineDistanceQuery<>(0,100,0,100,10);
        for (int i=45+d; i<45-d+360; i++) {
            ae(false,50*Math.cos(Math.PI*i/180.0),50*Math.sin(Math.PI*i/180.0));
        }
        d=4;
        for (int i=45-d; i<45+d; i++) {
            ae(true,50*Math.cos(Math.PI*i/180.0),50*Math.sin(Math.PI*i/180.0));
        }
        
//        assertEquals(true, lq.queryElement(AABB.create(0.5,0.5,0.5,0.5), null));
//        assertEquals(false, lq.queryElement(AABB.create(50,50,90,90), null));
//        assertEquals(false, lq.queryElement(AABB.create(-50,-50,90,90), null));
//        assertEquals(false, lq.queryElement(AABB.create(0,0,90,90), null));
//        assertEquals(false, lq.queryElement(AABB.create(-50,-50,90,90), null));
//        assertEquals(false, lq.queryElement(AABB.create(-50,-50,90,90), null));
//        assertEquals(false, lq.queryElement(AABB.create(-50,-50,90,90), null));
//        assertEquals(false, lq.queryElement(AABB.create(-50,-50,90,90), null));
//        assertEquals(false, lq.queryElement(AABB.create(-50,-50,90,90), null));
    }
}
