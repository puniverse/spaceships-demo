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
import co.paralleluniverse.common.record.Field;
import co.paralleluniverse.common.record.Field.DoubleField;
import co.paralleluniverse.common.record.Field.LongField;
import co.paralleluniverse.common.record.Field.ObjectField;
import co.paralleluniverse.spacebase.SpatialToken;
import com.google.common.collect.ImmutableSet;
import java.util.Set;

/**
 *
 * @author pron
 */
public final class SpaceshipState {
    public static final LongField<SpaceshipState> $lastMoved = Field.longField("lastMoved", 0); // 0
    public static final LongField<SpaceshipState> $timeFired = Field.longField("timeFired", 1); // 1
    public static final LongField<SpaceshipState> $blowTime = Field.longField("blowTime", 2); // 2
    public static final DoubleField<SpaceshipState> $shotLength = Field.doubleField("shotLength", 3); // 3
    public static final DoubleField<SpaceshipState> $x = Field.doubleField("x", 4); // 4
    public static final DoubleField<SpaceshipState> $y = Field.doubleField("y", 5); // 5
    public static final DoubleField<SpaceshipState> $vx = Field.doubleField("vx", 6); // 6
    public static final DoubleField<SpaceshipState> $vy = Field.doubleField("vy", 7); // 7
    public static final DoubleField<SpaceshipState> $ax = Field.doubleField("ax", 8); // 8
    public static final DoubleField<SpaceshipState> $ay = Field.doubleField("ay", 8); // 9
    public static final DoubleField<SpaceshipState> $exVx = Field.doubleField("exVx", 10); // 10
    public static final DoubleField<SpaceshipState> $exVy = Field.doubleField("exVy", 11); // 11
    public static final ObjectField<SpaceshipState, SpatialToken> $token = Field.objectField("token", SpatialToken.class, 12); // 12
    public static final ObjectField<SpaceshipState, ActorRef<Spaceship.SpaceshipMessage>> $spaceship = (ObjectField)Field.objectField("spaceship", ActorRef.class, 13); // 13
    //
    public static final Set FIELDS = ImmutableSet.of(
            $lastMoved,
            $timeFired,
            $blowTime,
            $shotLength,
            $x, $y,
            $vx, $vy,
            $ax, $ay,
            $exVx, $exVy,
            $token,
            $spaceship);
    
    private SpaceshipState() {
    }
}
