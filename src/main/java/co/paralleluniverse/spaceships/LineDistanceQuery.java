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

import co.paralleluniverse.db.tree.QueryResult;
import co.paralleluniverse.spacebase.AABB;
import co.paralleluniverse.spacebase.SpatialQuery;

class LineDistanceQuery<T> implements SpatialQuery<T> {
    public static final int LINE_ACCURACY = 500;
    private AABB lineAABB;
    private double a, b, norm;
    private final double maxDist;

    public LineDistanceQuery(double x0, double x1, double y0, double y1, double dist) {
        this.maxDist = dist;
        double minX, maxX, minY, maxY;
        if (x0 < x1) {
            minX = x0;
            maxX = x1;
        } else {
            minX = x1;
            maxX = x0;
        }
        if (y0 < y1) {
            minY = y0;
            maxY = y1;
        } else {
            minY = y1;
            maxY = y0;
        }
        this.lineAABB = AABB.create(minX, maxX, minY, maxY);
        a = (y1 - y0) / (x1 - x0);
        b = y1 - a * x1;
        norm = Math.sqrt(a * a + 1);
    }

    @Override
    public QueryResult queryContainer(AABB aabb) {
        if (lineAABB.contains(aabb) || lineAABB.intersects(aabb))
            return QueryResult.SOME;
        return QueryResult.NONE;
    }

    @Override
    public boolean queryElement(AABB aabb, T elem) {
        if (!lineAABB.intersects(aabb))
            return false;
        double dist = Math.abs(a * aabb.min(0) - aabb.min(1) + b) / norm;
        if (dist < this.maxDist)
            return true;
        return false;
    }

    public double getDistance(double elemX, double elemY, T elem) {
        return Math.abs(a * elemX - elemY + b) / norm;
    }
}
