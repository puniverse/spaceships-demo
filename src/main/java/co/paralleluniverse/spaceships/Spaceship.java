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

import co.paralleluniverse.actors.ActorRef;
import co.paralleluniverse.actors.BasicActor;
import co.paralleluniverse.actors.MailboxConfig;
import co.paralleluniverse.data.record.Record;
import co.paralleluniverse.data.record.Records;
import co.paralleluniverse.common.util.Debug;
import co.paralleluniverse.data.record.DynamicRecordType;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.spacebase.AABB;
import static co.paralleluniverse.spacebase.AABB.X;
import static co.paralleluniverse.spacebase.AABB.Y;
import co.paralleluniverse.spacebase.ElementAndBounds;
import co.paralleluniverse.spacebase.ElementUpdater;
import co.paralleluniverse.spacebase.MutableAABB;
import co.paralleluniverse.spacebase.SpatialQueries;
import co.paralleluniverse.spacebase.SpatialToken;
import co.paralleluniverse.spacebase.quasar.ElementUpdater1;
import co.paralleluniverse.spacebase.quasar.ResultSet;
import static co.paralleluniverse.spaceships.SpaceshipState.*;
import co.paralleluniverse.strands.channels.Channels;
import static java.lang.Math.*;
import java.nio.FloatBuffer;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;

/**
 * A spaceship
 */
public class Spaceship extends BasicActor<Spaceship.SpaceshipMessage, Void> {
    public static enum Status {
        ALIVE, BLOWING_UP, GONE
    };
    private static final int MIN_PERIOD_MILLIS = 30;
    private static final int MAX_SEARCH_RANGE = 400;
    private static final int MAX_SEARCH_RANGE_SQUARED = MAX_SEARCH_RANGE * MAX_SEARCH_RANGE;
    private static final int TIMES_HIT_TO_BLOW = 3;
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
    private final int id;
    private final State state; // the ships' public state - explanation below
    private final Record<SpaceshipState> stateRecord; // the ships' public state - explanation below
    private final Queue<DelayedRunnable> delayQueue = new PriorityQueue<DelayedRunnable>();
    private final Phaser phaser;
    // private state:
    private Status status = Status.ALIVE;
    private SpatialToken lockedOn;
    private double chaseAx;
    private double chaseAy;
    private double exVx = 0;
    private double exVy = 0;
    private int timesHit = 0;
    private long timeHit = 0;
    private long timeFired = 0;
    private double shotLength = 10f;
    // "external velocity" does not result from thruster (but from nearby explosions or by getting hit), and threfore does not affect heading
    private long exVelocityUpdated = 0;

    private static class State {
        ActorRef<SpaceshipMessage> spaceship;
        SpatialToken token;
        Status status = Status.ALIVE;
        long lastMoved = -1L;
        double x;
        double y;
        double vx;
        private double vy;
        private double ax;
        private double ay;
        private double exVx = 0;
        private double exVy = 0;
        private long timeFired = 0;
        private long blowTime = 0;
        private double shotLength = 10f;
        private int timesHit = 0;
    }
    // The public state is only updated by the owning Spaceship, and only in a SB transaction.
    // Therefore the owning spaceship can read it any time, but anyone else (other spacehips or the renderer) must only do so in
    // a transaction.
    public static void getCurrentLocation(Record<SpaceshipState> s, long currentTime, FloatBuffer buffer) {
        final double duration = (double) (currentTime - s.get($lastMoved)) / TimeUnit.SECONDS.toMillis(1);
        final double duration2 = duration * duration;

        final double currentX = s.get($x) + (s.get($vx) + s.get($exVx)) * duration + s.get($ax) * duration2;
        final double currentY = s.get($y) + (s.get($vy) + s.get($exVx)) * duration + s.get($ay) * duration2;

        buffer.put((float) currentX);
        buffer.put((float) currentY);
    }

    public static double getCurrentHeading(Record<SpaceshipState> s, long currentTime) {
        final double duration = (double) (currentTime - s.get($lastMoved)) / TimeUnit.SECONDS.toMillis(1);

        final double currentVx = s.get($vx) + s.get($ax) * duration;
        final double currentVy = s.get($vy) + s.get($ay) * duration;

        return atan2(currentVx, currentVy);
    }

    public Spaceship(Spaceships global, int id, Phaser phaser) {
        super(new MailboxConfig(10, Channels.OverflowPolicy.THROW));
        this.id = id;
        this.state = new State();
        this.stateRecord = Records.delegate(this, SpaceshipState.stateType.newInstance(state, DynamicRecordType.Mode.UNSAFE));
        this.phaser = phaser;

        this.global = global;
        this.random = global.random;

        state.x = random.randRange(global.bounds.min(X), global.bounds.max(X));
        state.y = random.randRange(global.bounds.min(Y), global.bounds.max(Y));

        final double direction = random.nextDouble() * 2 * Math.PI;
        final double speed = SPEED_LIMIT / 4 + random.nextGaussian() * global.speedVariance;
        setVelocityDir(direction, speed);
    }

    @Override
    public String toString() {
        return "Spaceship@" + id;
    }

    private AABB getAABB() {
        final MutableAABB aabb = AABB.create(2);
        getAABB(aabb);
        return aabb;
    }

    private void getAABB(MutableAABB aabb) {
        // capture x and y atomically (each)
        final double _x = state.x;
        final double _y = state.y;
        aabb.min(X, _x);
        aabb.max(X, _x);
        aabb.min(Y, _y);
        aabb.max(Y, _y);
    }

    @Override
    protected Void doRun() throws InterruptedException, SuspendExecution {
//        phaser.register();
        state.spaceship = ref();
        state.token = global.sb.insert(stateRecord, getAABB());
        try {
            record(1, "Spaceship", "doRun", "%s: aaaaa", this);
            for (int i = 0;; i++) {
                final long nextMove = state.lastMoved + MIN_PERIOD_MILLIS;

                SpaceshipMessage message = receive(nextMove - now(), TimeUnit.MILLISECONDS);
                final long now = now();
                if (message != null) {
                    // handle message
                    if (message instanceof Shot)
                        shot(((Shot) message).x, ((Shot) message).y);
                    else if (message instanceof Blast)
                        blast(((Blast) message).x, ((Blast) message).y);
                } else {
                    // no message
                    runDelayed(now); // apply delayed actions

                    if (status == Status.GONE) {
                        record(1, "Spaceship", "doRun", "%s: deleting", this);
                        return null; // explosion has finished
                    } else if (status == Status.ALIVE) {
                        if (lockedOn == null) {
                            record(1, "Spaceship", "doRun", "%s: no lock", this);
                            if (now - timeHit > SHOOT_INABILITY_DURATION && random.nextFloat() < SEARCH_PROBABLITY)
                                searchForTargets();
                        } else
                            chaseAndShoot();

                        AABB myAABB = getAABB();
                        try (ResultSet<Record<SpaceshipState>> rs = global.sb.queryForUpdate(
                                        SpatialQueries.range(myAABB, global.range),
                                        SpatialQueries.equals((Record<SpaceshipState>) stateRecord, myAABB), false)) {

                            applyNeighborRejection(rs.getResultReadOnly(), global.now());

                            assert rs.getResultForUpdate().size() <= 1;
                            for (final ElementUpdater<Record<SpaceshipState>> updater : rs.getResultForUpdate()) {
                                assert updater.elem().equals(stateRecord);

                                move(now);
                                state.status = status;
                                state.timeFired = timeFired;
                                state.shotLength = shotLength;
                                updater.update(getAABB());
                            }
                        }
                        reduceExternalVelocity(now);
                    } else if (status == Status.BLOWING_UP && state.status != status) {
                        try (ElementUpdater1<Record<SpaceshipState>> up = global.sb.update(state.token)) {
                            state.status = status;
                            state.vx = 0.0;
                            state.vy = 0.0;
                            state.exVx = 0.0;
                            state.exVy = 0.0;
                            state.ax = 0.0;
                            state.ay = 0.0;
                        }
                    }
                }
                record(1, "Spaceship", "doRun", "%s: iter %s", this, i);
//                phaser.arrive();
            }
        } catch (Throwable e) {
            e.printStackTrace();
            Debug.exit(1);
            return null;
        } finally {
            record(1, "Spaceship", "doRun", "%s: XXXXXX", this);
//            phaser.arriveAndDeregister();
            global.sb.delete(state.token);
        }
    }

    private void searchForTargets() throws SuspendExecution, InterruptedException {
//        double v = sqrt(pow(vx, 2) + pow(vy, 2));
//        final double x2 = x + vx / v * MAX_SEARCH_RANGE;
//        final double y2 = y + vy / v * MAX_SEARCH_RANGE;
        record(1, "Spaceship", "searchForTargets", "%s: searching...", this);

        try (ResultSet<Record<SpaceshipState>> rs = global.sb.query(new RadarQuery(state.x, state.y, state.vx, state.vy, toRadians(30), MAX_SEARCH_RANGE))) {
            Record<SpaceshipState> nearestShip = null;
            double minRange2 = MAX_SEARCH_RANGE_SQUARED;
            for (Record<SpaceshipState> s : rs.getResultReadOnly()) {
                final double dx = s.get($x) - state.x;
                final double dy = s.get($y) - state.y;
                double rng2 = dx * dx + dy * dy;
                if (rng2 > 100 & rng2 <= minRange2) { //not me and not so close
                    minRange2 = rng2;
                    nearestShip = s;
                }
            }
            if (nearestShip != null)
                lockOnTarget(nearestShip);
            record(1, "Spaceship", "searchForTargets", "%s: size of radar query: %d", this, rs.getResultReadOnly().size());
        }
    }

    private void chaseAndShoot() throws SuspendExecution, InterruptedException {
        record(1, "Spaceship", "chaseAndShoot", "%s: locked", this);
        // check lock range, chase, shoot
        boolean foundLockedOn = false;
        ElementAndBounds<Record<SpaceshipState>> target = global.sb.getElement(lockedOn);
        if (target != null) {
            foundLockedOn = true;
            final Record<SpaceshipState> lockedSpaceship = target.getElement();

            // double angularDiversion = abs(atan2(lockedSpaceship.vx, lockedSpaceship.vy) - getCurrentHeading(shootTime));
            if (inShotRange(target.getBounds()) & global.random.nextGaussian() < SHOOT_PROBABLITY) {
                record(1, "Spaceship", "chaseAndShoot", "%s: shootrange", this);
                double range = mag(lockedSpaceship.get($x) - state.x, lockedSpaceship.get($y) - state.y);
                shoot(range);
                lockedSpaceship.get($spaceship).send(new Shot(state.x, state.y));
            }
            if (inLockRange(target.getBounds())) {
                record(1, "Spaceship", "chaseAndShoot", "%s: lockrange", this);
//              shoot(global, range);
                chase(target.getElement());
            } else {
                record(1, "Spaceship", "chaseAndShoot", "%s: release lock", this);
                lockOnTarget(null);  // not in range, release lock
            }
        }
        if (!foundLockedOn)
            lockOnTarget(null);
    }

    private boolean inLockRange(AABB aabb) {
        return new RadarQuery(state.x, state.y, state.vx, state.vy, toRadians(30), MAX_SEARCH_RANGE).queryElement(aabb, null);
    }

    private boolean inShotRange(AABB aabb) {
        final double v = mag(state.vx, state.vy);
        final double x2 = state.x + state.vx / v * SHOOT_RANGE;
        final double y2 = state.y + state.vy / v * SHOOT_RANGE;
        return (new LineDistanceQuery<Spaceship>(state.x + 1, x2, state.y + 1, y2, SHOOT_ACCURACY).queryElement(aabb, this));
    }

    // called in a transaction
    protected void applyNeighborRejection(Set<Record<SpaceshipState>> neighbors, long currentTime) {
        final int n = neighbors.size();

        state.ax = chaseAx;
        state.ay = chaseAy;

        if (n > 1) {
            for (Record<SpaceshipState> s : neighbors) {
                if (s == this.state)
                    continue;

                assert !Double.isNaN(state.x + state.y);

                final double dx = s.get($x) - state.x;
                final double dy = s.get($y) - state.y;
                double d = mag(dx, dy);
                if (d < MIN_PROXIMITY)
                    d = MIN_PROXIMITY;

                final double udx = dx / d;
                final double udy = dy / d;

                double rejection = min(REJECTION_COEFF / (d * d), 250);

                state.ax = state.ax - rejection * udx;
                state.ay = state.ay - rejection * udy;

                if (Double.isNaN(state.ax + state.ay))
                    assert false;
            }
        }
    }

    /**
     * Update ship position
     */
    private void move(long now) {
        assert status == Status.ALIVE;

        state.exVx = exVx;
        state.exVy = exVy;

        final long lastMoved = state.lastMoved;

        if (lastMoved > 0 & now > lastMoved) {
            double x = state.x;
            double y = state.y;
            double vx = state.vx;
            double vy = state.vy;
            double ax = state.ax;
            double ay = state.ay;

            final AABB bounds = global.bounds;
            final double duration = (double) (now - lastMoved) / TimeUnit.SECONDS.toMillis(1);
            final double duration2 = duration * duration;// * Math.signum(duration);

            x = x + (vx + exVx) * duration + ax * duration2;
            y = y + (vy + exVy) * duration + ay * duration2;

            vx = vx + ax * duration;
            vy = vy + ay * duration;

            // before limitSpeed
            state.vx = vx;
            state.vy = vy;
            limitSpeed();
            vx = state.vx;
            vy = state.vy;

            assert !Double.isNaN(vx + vy);

            if (x > bounds.max(X) || x < bounds.min(X)) {
                x = min(x, bounds.max(X));
                x = max(x, bounds.min(X));
                vx = -vx * SPEED_BOUNCE_DAMPING;
                ax = 0;
            }
            if (y > bounds.max(Y) || y < bounds.min(Y)) {
                y = min(y, bounds.max(Y));
                y = max(y, bounds.min(Y));
                vy = -vy * SPEED_BOUNCE_DAMPING;
                ay = 0;
            }

            assert !Double.isNaN(x + y);

            state.x = x;
            state.y = y;
            state.vx = vx;
            state.vy = vy;
            state.ax = ax;
            state.ay = ay;
        }
        state.lastMoved = now;
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
    private void chase(Record<SpaceshipState> target) {
        final double dx = target.get($x) - state.x;
        final double dy = target.get($y) - state.y;
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
        record(1, "Spaceship", "shot", "%s: shot", this);
        this.timesHit++;
        timeHit = global.now();
        if (timesHit < TIMES_HIT_TO_BLOW) {
            final double dx = shooterX - state.x;
            final double dy = shooterY - state.y;
            double d = mag(dx, dy);
            if (d < MIN_PROXIMITY)
                d = MIN_PROXIMITY;
            final double udx = dx / d;
            final double udy = dy / d;

            reduceExternalVelocity(timeHit);
            exVx += HIT_RECOIL_VELOCITY * udx;
            exVy += HIT_RECOIL_VELOCITY * udy;
            this.exVelocityUpdated = timeHit;
        } else if (status == Status.ALIVE) {
            System.out.println("BOOM: " + this);
            record(1, "Spaceship", "shot", "%s: BOOM", this);
            // I'm dead: blow up. The explosion pushes away all nearby ships.
            try (ResultSet<Record<SpaceshipState>> rs = global.sb.query(SpatialQueries.range(getAABB(), BLAST_RANGE))) {
                final Blast blastMessage = new Blast(now(), state.x, state.y);
                for (Record<SpaceshipState> s : rs.getResultReadOnly())
                    s.get($spaceship).send(blastMessage);
            }
            this.status = Status.BLOWING_UP;
            state.blowTime = timeHit;
            delay(timeHit, BLOW_TILL_DELETE_DURATION, TimeUnit.MILLISECONDS, new Runnable() {
                public void run() {
                    status = Status.GONE;
                }
            });
        }
    }

    /**
     * A nearby ship has exploded, accelerate away in the blast.
     */
    private void blast(double explosionX, double explosionY) {
        final double dx = explosionX - state.x;
        final double dy = explosionY - state.y;
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

    private long now() {
        return global.now();
    }

    private void setVelocityDir(double direction, double speed) {
        state.vx = speed * cos(direction);
        state.vy = speed * sin(direction);
        limitSpeed();
    }

    private void limitSpeed() {
        final double vx = state.vx;
        final double vy = state.vy;

        final double speed = mag(vx, vy);
        if (speed > SPEED_LIMIT) {
            state.vx = vx / speed * SPEED_LIMIT;
            state.vy = vy / speed * SPEED_LIMIT;
        }
    }

    private void lockOnTarget(Record<SpaceshipState> target) {
        if (target != null)
            lockedOn = target.get($token);
        else
            lockedOn = null;
        chaseAx = 0;
        chaseAy = 0;
    }

    private void shoot(double range) {
        timeFired = global.now();
        shotLength = range;
    }

    private double mag(double x, double y) {
        return sqrt(x * x + y * y);
    }

    private void runDelayed(long now) {
        for (;;) {
            DelayedRunnable command = delayQueue.peek();
            if (command != null && command.time <= now) {
                delayQueue.poll();
                command.run();
            } else
                break;
        }
    }

    private void delay(long now, long delay, TimeUnit unit, final Runnable command) {
        delayQueue.add(new DelayedRunnable(now + unit.toMillis(delay)) {
            @Override
            public void run() {
                command.run();
            }
        });
    }

    public static class SpaceshipMessage {
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

    static abstract class DelayedRunnable implements Runnable, Comparable<DelayedRunnable> {
        final long time;

        public DelayedRunnable(long time) {
            this.time = time;
        }

        @Override
        public int compareTo(DelayedRunnable o) {
            return Long.signum(this.time - o.time);
        }
    }
}
