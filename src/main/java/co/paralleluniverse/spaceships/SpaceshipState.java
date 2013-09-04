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
import co.paralleluniverse.common.record.Field.DoubleField;
import co.paralleluniverse.common.record.Field.LongField;
import co.paralleluniverse.common.record.Field.ObjectField;
import co.paralleluniverse.common.record.Records;
import co.paralleluniverse.common.record.SimpleRecordType;
import co.paralleluniverse.spacebase.SpatialToken;

/**
 *
 * @author pron
 */
public final class SpaceshipState {
    public static final SimpleRecordType<SpaceshipState> stateType = Records.newSimpleRecordType();
    public static final LongField<SpaceshipState> $lastMoved = stateType.longField("lastMoved");
    public static final LongField<SpaceshipState> $timeFired = stateType.longField("timeFired");
    public static final LongField<SpaceshipState> $blowTime = stateType.longField("blowTime");
    public static final DoubleField<SpaceshipState> $shotLength = stateType.doubleField("shotLength");
    public static final DoubleField<SpaceshipState> $x = stateType.doubleField("x");
    public static final DoubleField<SpaceshipState> $y = stateType.doubleField("y");
    public static final DoubleField<SpaceshipState> $vx = stateType.doubleField("vx");
    public static final DoubleField<SpaceshipState> $vy = stateType.doubleField("vy");
    public static final DoubleField<SpaceshipState> $ax = stateType.doubleField("ax");
    public static final DoubleField<SpaceshipState> $ay = stateType.doubleField("ay");
    public static final DoubleField<SpaceshipState> $exVx = stateType.doubleField("exVx");
    public static final DoubleField<SpaceshipState> $exVy = stateType.doubleField("exVy");
    public static final ObjectField<SpaceshipState, Spaceship.Status> $status = stateType.objectField("status", Spaceship.Status.class);
    public static final ObjectField<SpaceshipState, SpatialToken> $token = stateType.objectField("token", SpatialToken.class);
    public static final ObjectField<SpaceshipState, ActorRef<Spaceship.SpaceshipMessage>> $spaceship = (ObjectField)stateType.objectField("spaceship", ActorRef.class);

    private SpaceshipState() {
    }
}
