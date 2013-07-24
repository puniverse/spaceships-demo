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

import co.paralleluniverse.actors.BasicActor;
import co.paralleluniverse.actors.MailboxConfig;
import co.paralleluniverse.db.api.Sync;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.spacebase.AABB;
import static co.paralleluniverse.spacebase.AABB.X;
import static co.paralleluniverse.spacebase.AABB.Y;
import co.paralleluniverse.spacebase.ElementAndBounds;
import co.paralleluniverse.spacebase.ElementUpdater;
import co.paralleluniverse.spacebase.MutableAABB;
import co.paralleluniverse.spacebase.SpatialQueries;
import co.paralleluniverse.spacebase.SpatialToken;
import co.paralleluniverse.spacebase.quasar.ResultSet;
import co.paralleluniverse.strands.channels.Channels;
import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.pow;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static java.lang.Math.toRadians;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * A spaceship
 */
public class Spaceship extends BasicActor<Spaceship.SpaceshipMessage, Void> {
    private static final int MIN_PERIOD_MILLIS = 10;
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
    private static final int SHOOT_RANGE = 200;
    private static final int SHOOT_ACCURACY = 10;
    private static final double SHOOT_PROBABLITY = 0.2;
    //
    private final Spaceships global;
    private final RandSpatial random;
    private SpatialToken token;
    private Sync sync;
    //
    private long lastMoved = -1L;
    private long shotTime = 0;
    private double shotLength = 10f;
    private int timesHit = 0;
    private volatile long blowTime = 0;
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
        super(new MailboxConfig(10, Channels.OverflowPolicy.THROW));

        this.global = global;
        this.random = global.random;

        x = random.randRange(global.bounds.min(X), global.bounds.max(X));
        y = random.randRange(global.bounds.min(Y), global.bounds.max(Y));

        final double direction = random.nextDouble() * 2 * Math.PI;
        final double speed = SPEED_LIMIT / 4 + random.nextGaussian() * global.speedVariance;
        setVelocityDir(direction, speed);
    }

    @Override
    protected Void doRun() throws InterruptedException, SuspendExecution {
        this.token = global.sb.insert(this, getAABB());
        try {
            for (;;) {
                final long nextMove = lastMoved + MIN_PERIOD_MILLIS;

                SpaceshipMessage message = receive(nextMove - now(), TimeUnit.MILLISECONDS);
                final long now = now();
                if (message != null) {
                    if (message instanceof Shot)
                        shot(((Shot) message).x, ((Shot) message).y);
                    else if (message instanceof Blast) {
                        blast(((Shot) message).x, ((Shot) message).y);
                    }
                } else if (nextMove - now <= 0) {
                    if (blowTime > 0) { // if i'm being being blown up
                        debug("blow");
                        if (global.now() - blowTime > BLOW_TILL_DELETE_DURATION)
                            return null; // explosion has finished
                    }

                    if (lockedOn == null) {
                        debug("no lock");
                        if (now - getTimeShot() > SHOOT_INABILITY_DURATION && random.nextFloat() < SEARCH_PROBABLITY)
                            searchForTargets();
                    } else
                        chaseAndShoot();

                    try (ResultSet<Spaceship> rs = global.sb.queryForUpdate(
                                    SpatialQueries.range(getAABB(), global.range),
                                    SpatialQueries.equals(this, getAABB()), false)) {

                        applyNeighborRejection(rs.getResultReadOnly(), global.now());

                        assert rs.getResultForUpdate().size() <= 1;
                        for (final ElementUpdater<Spaceship> updater : rs.getResultForUpdate()) {
                            assert updater.elem() == this;

                            move(now);
                            updater.update(getAABB());
                        }
                    }
                }
            }
        } finally {
            global.sb.delete(token);
        }
    }

    private void searchForTargets() throws SuspendExecution, InterruptedException {
        double v = sqrt(pow(vx, 2) + pow(vy, 2));
        final double x2 = x + vx / v * MAX_SEARCH_RANGE;
        final double y2 = y + vy / v * MAX_SEARCH_RANGE;
        debug("searching...");

        // search for targets in the line of shot
//        global.sb.query(new LineDistanceQuery<Spaceship>(x + 1, x2, y + 1, y2), new SpatialSetVisitor<Spaceship>() {
        try (ResultSet<Spaceship> rs = global.sb.query(new RadarQuery(x, y, vx, vy, toRadians(30), MAX_SEARCH_RANGE))) {
            Spaceship nearestShip = null;
            double minRange2 = pow(MAX_SEARCH_RANGE, 2);
            for (Spaceship s : rs.getResultReadOnly()) {
                double rng2 = pow(s.x - x, 2) + pow(s.y - y, 2);
                if (rng2 > 100 & rng2 <= minRange2) { //not me and not so close
                    minRange2 = rng2;
                    nearestShip = s;
                }
            }
            if (nearestShip != null)
                lockOnTarget(nearestShip);
            debug("size of radar query " + rs.getResultReadOnly().size());
        }
    }

    private void chaseAndShoot() throws SuspendExecution, InterruptedException {
        debug("locked");
        // check lock range, chase, shoot
        boolean foundLockedOn = false;
        ElementAndBounds<Spaceship> target = global.sb.getElement(lockedOn);
        if (target != null) {
            foundLockedOn = true;
            final Spaceship lockedSpaceship = target.getElement();

            // double angularDiversion = abs(atan2(lockedSpaceship.vx, lockedSpaceship.vy) - getCurrentHeading(shootTime));
            if (lockedSpaceship.getBlowTime() != 0) {
                lockOnTarget(null);
                return;
            }
            if (inShotRange(target.getBounds()) & global.random.nextGaussian() < SHOOT_PROBABLITY) {
                debug("shootrange");
                double range = mag(lockedSpaceship.x - x, lockedSpaceship.y - y);
                this.shoot(range);
                lockedSpaceship.send(new Shot(x, y));
            }
            if (inLockRange(target.getBounds())) {
                debug("lockrange");
//              shoot(global, range);
                chase(target.getBounds());
            } else {
                debug("release lock");
                lockOnTarget(null);  // not in range, release lock
            }
        }
        if (!foundLockedOn)
            lockOnTarget(null);
    }

    private boolean inLockRange(AABB aabb) {
        return new RadarQuery(x, y, vx, vy, toRadians(30), MAX_SEARCH_RANGE).queryElement(aabb, null);
    }

    private boolean inShotRange(AABB aabb) {
        double v = mag(vx, vy);
        final double x2 = x + vx / v * SHOOT_RANGE;
        final double y2 = y + vy / v * SHOOT_RANGE;
        return (new LineDistanceQuery<Spaceship>(x + 1, x2, y + 1, y2, SHOOT_ACCURACY).queryElement(aabb, this));
    }

    protected void applyNeighborRejection(Set<Spaceship> neighbors, long currentTime) {
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

                double rejection = min(REJECTION_COEFF / (d * d), 250);

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
    public void move(long currentTime) {
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
                x = min(x, bounds.max(X));
                x = max(x, bounds.min(X));
                vx = -vx * SPEED_BOUNCE_DAMPING;
            }
            if (y > bounds.max(Y) || y < bounds.min(Y)) {
                y = min(y, bounds.max(Y));
                y = max(y, bounds.min(Y));
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
            currentTime = blowTime; // don't move while blowing up
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
        return atan2(velocity[0], velocity[1]);
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
     * Accelerate toward given ship
     */
    private void chase(AABB target) {
        final double dx = target.min(X) - x;
        final double dy = target.min(Y) - y;
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
    private void shot(double shooterX, double shooterY) throws SuspendExecution, InterruptedException {
        this.timesHit++;
        this.timeShot = global.now();
        if (timesHit < TIMES_HITTED_TO_BLOW) {
            final double dx = shooterX - x;
            final double dy = shooterY - y;
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
            try (ResultSet<Spaceship> rs = global.sb.query(SpatialQueries.range(getAABB(), BLAST_RANGE))) {
                final Blast blastMessage = new Blast(now(), x, y);
                for (Spaceship s : rs.getResultReadOnly())
                    s.send(blastMessage);
            }
            blowTime = global.now();
        }
    }

    /**
     * A nearby ship has exploded, accelerate away in the blast.
     */
    private void blast(double explosionX, double explosionY) {
        final double dx = explosionX - x;
        final double dy = explosionY - y;
        final double d = mag(dx, dy);
        if (d < MIN_PROXIMITY)
            return;
        final double udx = dx / d;
        final double udy = dy / d;

        double hitRecoil = 0.25 * d - 200;

        reduceExternalVelocity(global.now());
        exVx += hitRecoil * udx;
        exVy += hitRecoil * udy;
        this.exVelocityUpdated = global.now();
    }

    public long getLastMoved() {
        return lastMoved;
    }

    private long now() {
        return global.now();
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

    private void lockOnTarget(Spaceship target) {
        if (target != null) {
            lockedOn = target.token;
            chaseAx = 0;
            chaseAy = 0;
        } else
            lockedOn = null;
    }

    public void setSync(Sync sync) {
        this.sync = sync;
    }

    private void shoot(double range) {
        shotTime = global.now();
        shotLength = range;
    }

    private double mag(double x, double y) {
        return sqrt(x * x + y * y);
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

    public double getShotLength() {
        return shotLength;
    }
    private long timeShot = 0;

    public long getTimeShot() {
        return timeShot;
    }

    public long getBlowTime() {
        return blowTime;
    }

    public long getShotTime() {
        return shotTime;
    }

    private void debug(String s) {
//        if(global.getShips().get(0)==this) {
//            System.out.println(s);
//        }
    }

    static class SpaceshipMessage {
    }

    static class Shot extends SpaceshipMessage {
        final double x;
        final double y;

        public Shot(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    static class Blast extends SpaceshipMessage {
        final long time;
        final double x;
        final double y;

        public Blast(long time, double x, double y) {
            this.time = time;
            this.x = x;
            this.y = y;
        }
    }
}
