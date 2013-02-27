/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package co.paralleluniverse.spaceships;

import co.paralleluniverse.spacebase.AABB;
import co.paralleluniverse.spacebase.SpatialQueries;
import co.paralleluniverse.spacebase.SpatialQuery;
import co.paralleluniverse.spacebase.SpatialQuery.Result;
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
public class RadarQueryTest {
    private RadarQuery rq;
    public RadarQueryTest() {
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

    /**
     * Test of queryContainer method, of class RadarQuery.
     */
    @Test
    public void testQueryContainer() {
        System.out.println("queryContainer");
        AABB aabb = null;
        RadarQuery instance = null;
        Result expResult = null;
        Result result = instance.queryContainer(aabb);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    public void ae(boolean val,double x,double y) {
        AABB aabb = AABB.create(x,x,y,y);
        assertEquals(""+aabb,val, rq.queryElement(aabb,null));
        assertEquals(""+aabb,val ? SpatialQuery.Result.SOME : SpatialQuery.Result.NONE, 
                rq.queryContainer(aabb));
    }

    /**
     * Test of queryElement method, of class RadarQuery.
     */
    @Test
    public void testQueryElement() {
        rq = new RadarQuery(0, 0, 30, 30, Math.toRadians(45), 800);
        ae(true,30,30);
        ae(true,30,5);
        ae(false,30,-5);
        ae(false,-5,30);
        rq = new RadarQuery(0, 0, -30, 30, Math.toRadians(45), 800);
        ae(true,-30,30);
        ae(true,-30,5);
        ae(false,-30,-5);
        ae(false,5,30);
        rq = new RadarQuery(0, 0, -30, -30, Math.toRadians(45), 800);
        ae(true,-30,-30);
        ae(true,-30,-5);
        ae(false,-30,5);
        ae(false,5,-30);
        
    }
}
