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
package co.paralleluniverse.spaceships;

import co.paralleluniverse.spacebase.AABB;
import co.paralleluniverse.spacebase.SpatialQuery;

/**
 *
 * @author eitan
 */
public class RadarQuery implements SpatialQuery<Spaceship> 
{
    private final double x,y;
    private final double dev;
    private final double range;
    private AABB aabb;
    private final double heading;

    public RadarQuery(double x, double y, double vx, double vy, double dev, double range) {
        this.x = x;
        this.y = y;
        this.dev = dev;
        this.range = range;
        this.heading = Math.atan2(vy, vx);
        double minAng = heading - dev;
        double maxAng = heading + dev;
        double x1,x2,y1,y2;
        x1 = x + range * Math.cos(minAng);        
        x2 = x + range * Math.cos(maxAng);
        y1 = y + range * Math.sin(minAng);
        y2 = y + range * Math.sin(maxAng);
        aabb = AABB.create(
                Math.min(x, Math.min(x1, x2)),
                Math.max(x, Math.max(x1, x2)),
                Math.min(y, Math.min(y1, y2)),
                Math.max(y, Math.max(y1, y2))
                );
        
    }        
    
    
    @Override
    public Result queryContainer(AABB aabb) {
            if (this.aabb.contains(aabb) || this.aabb.intersects(aabb))
                return SpatialQuery.Result.SOME;
            return SpatialQuery.Result.NONE;
    }

    @Override
    public boolean queryElement(AABB aabb, Spaceship elem) {
        double ang = Math.atan2(aabb.max(1)-y, aabb.max(0)-x);
        if (Math.abs(ang-heading)<dev) return true;
        return false;
    }
    
}
