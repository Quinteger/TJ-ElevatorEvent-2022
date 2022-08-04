package org.togetherjava.event.elevator.util;

import java.util.Collection;
import java.util.Objects;

public final class CollectionUtils {
    private CollectionUtils() {}

    /**
     * Equality check for collections that do not override {@link #equals(Object)} for some reason.<br>
     * Not thread-safe, requires external synchronization.
     */
    public static boolean equals(Collection<?> c1, Collection<?> c2) {
        if (c1 == c2) {
            return true;
        }

        if (c1.size() != c2.size()) {
            return false;
        }

        var i1 = c1.iterator();
        var i2 = c2.iterator();
        while (i1.hasNext()) {
            if (!Objects.equals(i1.next(), i2.next())) {
                return false;
            }
        }

        return !i2.hasNext();
    }
}
