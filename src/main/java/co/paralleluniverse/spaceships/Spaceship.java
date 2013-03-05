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
import static co.paralleluniverse.spacebase.AABB.X;
import static co.paralleluniverse.spacebase.AABB.Y;
import co.paralleluniverse.spacebase.ElementUpdater;
import co.paralleluniverse.spacebase.MutableAABB;
import co.paralleluniverse.spacebase.SpatialModifyingVisitor;
import co.paralleluniverse.spacebase.SpatialQueries;
import co.paralleluniverse.spacebase.SpatialSetVisitor;
import co.paralleluniverse.spacebase.SpatialToken;
import co.paralleluniverse.spacebase.Sync;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * A spaceship
 */
public class Spaceship {
    private static final int MAX_SEARCH_RANGE = 400;
    private static final int TIMES_HITTED_TO_BLOW = 3;
    private static final double REJECTION_COEFF = 80000.0;
    private static final double SPEED_LIMIT = 100.0;
    private static final double SPEED_BOUNCE_DAMPING = 0.9;
    private static final double MIN_PROXIMITY = 4;
    private static final double HIT_RECOIL_VELOCITY = -100.0;
    private static final int BLAST_RANGE = 200;
    private static final int BLOW_TILL_DELETE_DURATION = 1000;
    private static final double SEARCH_PROBABLITY = 0.02;
    private static final int SHOOT_INABILITY_DURATION = 3000;
    //
    private SpatialToken token;
    private Sync sync;
    //
    private long lastMoved = -1L;
    private long shootTime = 0;
    private double shootLength = 10f;
    private int timesHit = 0;
    private long blowTime = 0;
    private SpatialToken lockedOn;
    private double chaseAx;
    private double chaseAy;
    private double x;
    private double y;
    private double vx;
    private double vy;
    private double ax;
    private double ay;
    // "external velocity" does not result from thruster (but from nearby explosions or by getting hit), and threfore does not affect heading
    private long exVelocityUpdated = 0;
    private double exVx = 0;
    private double exVy = 0;

    public Spaceship(Spaceships global) {
        if (global == null)
            return;

        final RandSpatial random = global.random;

        x = random.randRange(global.bounds.min(X), global.bounds.max(X));
        y = random.randRange(global.bounds.min(Y), global.bounds.max(Y));

        final double direction = random.nextDouble() * 2 * Math.PI;
        final double speed = SPEED_LIMIT / 4 + random.nextGaussian() * global.speedVariance;
        setVelocityDir(direction, speed);
    }

    public Sync run(final Spaceships global) {
        final RandSpatial random = global.random;

        if (blowTime > 0) { // if i'm being being blown up
            debug(global,"blow");
            if (global.currentTime() - blowTime > BLOW_TILL_DELETE_DURATION)
                global.deleteSpaceship(this); // explosion has finished
            return Sync.DONE;
        }

        if (lockedOn == null) {
            debug(global,"no lock");
            if (global.currentTime() - getTimeShot() > SHOOT_INABILITY_DURATION && random.nextFloat() < SEARCH_PROBABLITY)
                searchForTargets(global);
        } else {
            debug(global,"locked");
            // check lock range, chase, shoot
            global.sb.update(lockedOn, new SpatialModifyingVisitor<Spaceship>() {
                private static final int SHOOT_RANGE = 200;
                private static final int SHOOT_ACCURACY = 10;
                private static final double SHOOT_PROBABLITY = 0.2;
                boolean foundLockedOn = false;

                @Override
                public void visit(ElementUpdater<Spaceship> updater) {
                    foundLockedOn = true;
                    Spaceship lockedSpaceship = updater.elem();
                    double range = mag(lockedSpaceship.x - x, lockedSpaceship.y - y);
                    double angularDiversion = Math.abs(
                            Math.atan2(lockedSpaceship.vx, lockedSpaceship.vy)
                            - getCurrentHeading(shootTime));
                    if (lockedSpaceship.getBlowTime()!=0) {
                        lock(null);
                        return;
                    }
                    if (inShootRange(range, lockedSpaceship) & global.random.nextGaussian() < SHOOT_PROBABLITY) {
                        debug(global,"shootrange");
                        Spaceship.this.shoot(global,range);
                        updater.elem().shot(global, Spaceship.this);
                    }
                    if (inLockRange(lockedSpaceship)) {
                        debug(global,"lockrange");
//                        Spaceship.this.shoot(global, range);
                        chase(updater.elem());
                    } else {
                        debug(global,"release lock");                        
                        lock(null);  // not in range, release lock
                    }
                }

                @Override
                public void done() {
                    if (!foundLockedOn)
                        lock(null);
                }

                private boolean inLockRange(Spaceship target) {
                    return new RadarQuery(x, y, vx, vy, Math.toRadians(30), MAX_SEARCH_RANGE).queryElement(target.getAABB(), target);
                }

                private boolean inShootRange(double range, Spaceship target) {
                    double v = mag(vx, vy);
                    final double x2 = x + vx / v * SHOOT_RANGE;
                    final double y2 = y + vy / v * SHOOT_RANGE;
                    return (new LineDistanceQuery<Spaceship>(x + 1, x2, y + 1, y2,SHOOT_ACCURACY).queryElement(target.getAABB(), Spaceship.this));
                }
            });
        }

        // move based on nearby spacehips' positions
        return global.sb.queryForUpdate(SpatialQueries.range(getAABB(), global.range),
                SpatialQueries.equals(this, getAABB()), false, new SpatialSetVisitor<Spaceship>() {
            @Override
            public void visit(Set<Spaceship> resultReadOnly, Set<ElementUpdater<Spaceship>> resultForUpdate) {
                process(resultReadOnly, global.currentTime());

                assert resultForUpdate.size() <= 1;
                for (final ElementUpdater<Spaceship> updater : resultForUpdate) {
                    assert updater.elem() == Spaceship.this;

                    move(global, global.currentTime());
                    updater.update(getAABB());
                }
            }
        });
    }

    /**
     * Apply rejection from neighbors.
     */
    protected void process(Set<Spaceship> neighbors, long currentTime) {
        final int n = neighbors.size();

        ax = chaseAx;
        ay = chaseAy;

        if (n > 1) {
            for (Spaceship s : neighbors) {
                if (s == this)
                    continue;

                assert !Double.isNaN(x + y);

                final double dx = s.x - x;
                final double dy = s.y - y;
                double d = mag(dx, dy);
                if (d < MIN_PROXIMITY)
                    d = MIN_PROXIMITY;

                final double udx = dx / d;
                final double udy = dy / d;

                double rejection = Math.min(REJECTION_COEFF / (d * d), 250);

                ax -= rejection * udx;
                ay -= rejection * udy;

                if (Double.isNaN(ax + ay))
                    assert false;
            }
        }
    }

    /**
     * Update ship position
     */
    public void move(Spaceships global, long currentTime) {
        final long duration = currentTime - lastMoved;
        if (lastMoved > 0 & duration > 0) {
            final AABB bounds = global.bounds;
            double pos[] = getCurrentPosition(currentTime);
            x = pos[0];
            y = pos[1];
            double vel[] = getCurrentVelocity(currentTime);
            vx = vel[0];
            vy = vel[1];

            limitSpeed();

            assert !Double.isNaN(vx + vy);

            if (x > bounds.max(X) || x < bounds.min(X)) {
                x = Math.min(x, bounds.max(X));
                x = Math.max(x, bounds.min(X));
                vx = -vx * SPEED_BOUNCE_DAMPING;
            }
            if (y > bounds.max(Y) || y < bounds.min(Y)) {
                y = Math.min(y, bounds.max(Y));
                y = Math.max(y, bounds.min(Y));
                vy = -vy * SPEED_BOUNCE_DAMPING;
            }

            assert !Double.isNaN(x + y);
        }
        this.lastMoved = currentTime;
        reduceExternalVelocity(currentTime);
    }

    /**
     * extrapolate position
     */
    public double[] getCurrentPosition(long currentTime) {
        if (blowTime > 0)
            currentTime = blowTime;
        double duration = (double) (currentTime - lastMoved) / TimeUnit.SECONDS.toMillis(1);
        double duration2 = duration * duration;// * Math.signum(duration);
        double pos[] = {x + (vx + exVx) * duration + ax * duration2, y + (vy + exVy) * duration + ay * duration2};
        return pos;
    }

    /**
     * extrapolate velocity
     */
    public double[] getCurrentVelocity(long currentTime) {
        double duration = (double) (currentTime - lastMoved) / TimeUnit.SECONDS.toMillis(1);
        double velocity[] = {vx + (ax) * duration, vy + (ay) * duration};
        return velocity;
    }

    /**
     * extrapolate heading
     */
    public double getCurrentHeading(long currentTime) {
        double velocity[] = getCurrentVelocity(currentTime);
        return Math.atan2(velocity[0], velocity[1]);
    }

    private void reduceExternalVelocity(long currentTime) {
        double duration = (double) (currentTime - exVelocityUpdated) / TimeUnit.SECONDS.toMillis(1);
        if (exVelocityUpdated > 0 & duration > 0) {
            exVx /= (1 + 8 * duration);
            exVy /= (1 + 8 * duration);
        }
        exVelocityUpdated = currentTime;
    }

    /**
     *
     * @param global
     */
    private void searchForTargets(final Spaceships global) {
        double v = Math.sqrt(Math.pow(vx, 2) + Math.pow(vy, 2));
        final double x2 = x + vx / v * MAX_SEARCH_RANGE;
        final double y2 = y + vy / v * MAX_SEARCH_RANGE;
        debug(global, "searching...");

        // search for targets in the line of shot
//        global.sb.query(new LineDistanceQuery<Spaceship>(x + 1, x2, y + 1, y2), new SpatialSetVisitor<Spaceship>() {
        global.sb.query(new RadarQuery(x, y, vx, vy, Math.toRadians(30), MAX_SEARCH_RANGE), new SpatialSetVisitor<Spaceship>() {
            @Override
            public void visit(Set<Spaceship> resultReadOnly, Set<ElementUpdater<Spaceship>> resultForUpdate) {
                // find closest ship along line of shot and check whether locked-on ship is still in range
                Spaceship closeShip = null;
                double minRange2 = Math.pow(MAX_SEARCH_RANGE, 2);
                for (Spaceship s : resultReadOnly) {
                    double rng2 = Math.pow(s.x - x, 2) + Math.pow(s.y - y, 2);
                    if (rng2 > 100 & rng2 <= minRange2) { //not me and not so close
                        minRange2 = rng2;
                        closeShip = s;
                    }
                }
                if (closeShip != null)
                    lock(closeShip.getToken());
                debug(global, "size of radar query "+resultReadOnly.size());
            }
        });
    }

    /**
     * Accelerate toward given ship
     */
    private void chase(Spaceship s) {
        final double dx = s.x - x;
        final double dy = s.y - y;
        double d = mag(dx, dy);
        if (d < MIN_PROXIMITY)
            d = MIN_PROXIMITY;
        final double udx = dx / d;
        final double udy = dy / d;
        final double acc = 200;
        chaseAx = acc * udx;
        chaseAy = acc * udy;
    }

    /**
     * I've been shot!
     *
     * @param global
     * @param shooter
     */
    public void shot(final Spaceships global, Spaceship shooter) {
        this.timesHit++;
        this.timeShot = global.currentTime();
        if (timesHit < TIMES_HITTED_TO_BLOW) {
            final double dx = shooter.x - x;
            final double dy = shooter.y - y;
            double d = mag(dx, dy);
            if (d < MIN_PROXIMITY)
                d = MIN_PROXIMITY;
            final double udx = dx / d;
            final double udy = dy / d;

            reduceExternalVelocity(timeShot);
            exVx += HIT_RECOIL_VELOCITY * udx;
            exVy += HIT_RECOIL_VELOCITY * udy;
            this.exVelocityUpdated = timeShot;
        } else if (blowTime == 0) {
            // I'm dead: blow up. The explosion pushes away all nearby ships.
            global.sb.queryForUpdate(SpatialQueries.range(getAABB(), BLAST_RANGE),
                    new SpatialModifyingVisitor<Spaceship>() {
                        @Override
                        public void visit(ElementUpdater<Spaceship> updater) {
                            updater.elem().blast(global, Spaceship.this);
                        }

                        @Override
                        public void done() {
                        }
                    });
            blowTime = global.currentTime();
        }
    }

    /**
     * A nearby ship has exploded, accelerate away in the blast.
     */
    private void blast(Spaceships global, Spaceship exploded) {
        final double dx = exploded.x - x;
        final double dy = exploded.y - y;
        final double d = mag(dx, dy);
        if (d < MIN_PROXIMITY)
            return;
        final double udx = dx / d;
        final double udy = dy / d;

        double hitRecoil = 0.25 * d - 200;

        reduceExternalVelocity(global.currentTime());
        exVx += hitRecoil * udx;
        exVy += hitRecoil * udy;
        this.exVelocityUpdated = global.currentTime();
    }

    public long getLastMoved() {
        return lastMoved;
    }

    private void setVelocityDir(double direction, double speed) {
        vx = speed * cos(direction);
        vy = speed * sin(direction);
        limitSpeed();
    }

    private void limitSpeed() {
        final double speed = mag(vx, vy);
        if (speed > SPEED_LIMIT) {
            vx = vx / speed * SPEED_LIMIT;
            vy = vy / speed * SPEED_LIMIT;
        }
    }

    private double mag(double x, double y) {
        return Math.sqrt(x * x + y * y);
    }

    public AABB getAABB() {
        final MutableAABB aabb = AABB.create(2);
        getAABB(aabb);
        return aabb;
    }

    public void getAABB(MutableAABB aabb) {
        // capture x and y atomically (each)
        final double _x = x;
        final double _y = y;
        aabb.min(X, _x);
        aabb.max(X, _x);
        aabb.min(Y, _y);
        aabb.max(Y, _y);
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public SpatialToken getToken() {
        return token;
    }

    public double getShootLength() {
        return shootLength;
    }
    private long timeShot = 0;

    public long getTimeShot() {
        return timeShot;
    }

    public long getBlowTime() {
        return blowTime;
    }

    public void setToken(SpatialToken token) {
        this.token = token;
    }

    public long getShootTime() {
        return shootTime;
    }

    private void lock(SpatialToken token) {
        lockedOn = token;
        if (lockedOn == null) {
            chaseAx = 0;
            chaseAy = 0;
        }
    }

    public void setSync(Sync sync) {
        this.sync = sync;
    }

    public void join() throws InterruptedException {
        if (sync != null)
            sync.join();
    }

    private void shoot(Spaceships global, double range) {
        shootTime = global.currentTime();
        shootLength = range;
    }
    private void debug(Spaceships global, String s) {
//        if(global.getShips().get(0)==this) {
//            System.out.println(s);
//        }
    }
}
