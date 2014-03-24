package co.paralleluniverse.spaceships;

import java.io.Serializable;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public abstract class SpaceshipEvent implements Serializable {
    public final long time;
    public final short snapId;
    public final int id;

    public SpaceshipEvent(long time, short snapId, int id) {
        this.time = time;
        this.snapId = snapId;
        this.id = id;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "," + ToStringBuilder.reflectionToString(this, ToStringStyle.SIMPLE_STYLE);
    }

    public String headers() {
        return "#" + getClass().getSimpleName() + "," + ToStringBuilder.reflectionToString(this, ToStringStyle.DEFAULT_STYLE);
    }

    public static class MoveEvent extends SpaceshipEvent {
        public final double x;
        public final double y;

        public MoveEvent(long time, short snapId, int id, double x, double y) {
            super(time, snapId, id);
            this.x = x;
            this.y = y;
        }
    }

    public static class ShootEvent extends SpaceshipEvent {
        public final int targetId;

        public ShootEvent(long time, short snapId, int targetId, int id) {
            super(time, snapId, id);
            this.targetId = targetId;
        }
    }

    public static class BlowEvent extends SpaceshipEvent {
        public BlowEvent(long time, short snapId, int id) {
            super(time, snapId, id);
        }
    }
}
