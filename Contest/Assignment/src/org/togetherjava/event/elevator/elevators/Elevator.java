package org.togetherjava.event.elevator.elevators;

import lombok.Getter;
import org.togetherjava.event.elevator.humans.ElevatorListener;
import org.togetherjava.event.elevator.humans.Passenger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A single elevator that can serve a given amount of floors.
 * <p>
 * An elevator can take floor requests from either humans or the elevator system itself.
 * The elevator will eventually move towards the requested floor and transport humans to their destinations.
 */
public final class Elevator implements ElevatorPanel {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(0);

    @Getter private final int id;
    @Getter private final int minFloor;
    @Getter private final int maxFloor;
    @Getter private final Collection<ElevatorListener> passengers = ConcurrentHashMap.newKeySet();
    private final Deque<Integer> targets = new ArrayDeque<>();
    @Getter private int currentFloor;
    private ElevatorSystem elevatorSystem;

    /**
     * Creates a new elevator.
     *
     * @param minFloor     the minimum floor that the elevator can serve, must be greater than or equal to 1.
     * @param floorsServed the amount of floors served in total by this elevator, must be greater than or equal to 2.
     *                     Together with the minFloor this forms a consecutive range of floors with no gaps in between.
     * @param currentFloor the floor the elevator starts at, must be within the defined range of floors served by the elevator
     */
    public Elevator(int minFloor, int floorsServed, int currentFloor) {
        if (minFloor < 1) {
            throw new IllegalArgumentException("Minimum floor must at least 1, got " + minFloor);
        }
        if (floorsServed < 2) {
            throw new IllegalArgumentException("Amount of served floors must be at least 2, got " + floorsServed);
        }
        int maxFloor = minFloor + floorsServed - 1;
        if (currentFloor < minFloor || maxFloor < currentFloor) {
            throw new IllegalArgumentException("The current floor for this elevator must be between %d and %d, got %d".formatted(minFloor, maxFloor, currentFloor));
        }

        this.id = NEXT_ID.getAndIncrement();
        this.minFloor = minFloor;
        this.maxFloor = maxFloor;
        this.currentFloor = currentFloor;
    }

    public int getFloorsServed() {
        return maxFloor - minFloor + 1;
    }

    void setElevatorSystem(ElevatorSystem elevatorSystem) {
        if (elevatorSystem == null) {
            throw new IllegalArgumentException("Elevator system must not be null");
        }
        this.elevatorSystem = elevatorSystem;
    }

    @Override
    public void boardPassenger(Passenger passenger) {
        passengers.add(passenger);
        elevatorSystem.passengerEnteredElevator(passenger);
    }

    @Override
    public void removePassenger(Passenger passenger, boolean arrived) {
        if (!passengers.contains(passenger)) {
            throw new IllegalArgumentException("Attempt to remove a passenger which is not in the elevator");
        }
        passengers.remove(passenger);
        elevatorSystem.passengerLeftElevator(passenger, arrived);
    }

    @Override
    public synchronized void requestDestinationFloor(int destinationFloor) {
        // This represents a human or the elevator system
        // itself requesting this elevator to eventually move to the given floor.
        // The elevator is supposed to memorize the destination in a way that
        // it can ensure to eventually reach it.
        rangeCheck(destinationFloor);

        // Let's check if the work queue already contains the desired floor
        if (!willVisitFloor(destinationFloor)) {
            addTargetFloor(destinationFloor);
            System.out.printf("Elevator %d on floor %d has added floor %d to the queue, the queue is now %s%n", id, currentFloor, destinationFloor, targets);
        }
    }

    /**
     * Add a floor to the task queue of this elevator. Either as a new element at the end of the queue,
     * or by modifying the last element if it's possible to do so without changing elevator semantics.
     */
    private void addTargetFloor(int targetFloor) {
        if (!targets.isEmpty()) {
            int to = targets.removeLast();
            int from = targets.isEmpty() ? currentFloor : targets.getLast();
            if (from < to) {
                if (targetFloor < to) {
                    targets.add(to);
                }
            } else if (from > to) {
                if (targetFloor > to) {
                    targets.add(to);
                }
            } else {
                throw new IllegalArgumentException("Elevator has two of the same floors as consecutive targets, this is a bug");
            }
        }
        targets.add(targetFloor);
    }

    public void moveOneFloor() {
        // Implement. Essentially there are three possibilities:
        // - move up one floor
        // - move down one floor
        // - stand still
        // The elevator is supposed to move in a way that it will eventually reach
        // the floors requested by Humans via requestDestinationFloor(), ideally "fast" but also "fair",
        // meaning that the average time waiting (either in corridor or inside the elevator)
        // is minimized across all humans.
        // It is essential that this method updates the currentFloor field accordingly.
        if (!targets.isEmpty()) {
            int target = targets.element();
            if (currentFloor < target) {
                currentFloor++;
            } else if (currentFloor > target) {
                currentFloor--;
            } else {
                throw new IllegalArgumentException("Elevator has current floor as next target, this is a bug");
            }
            if (currentFloor == target) {
                // We arrived at the next target
                targets.remove();
            }
        }
    }

    @Override
    public synchronized String toString() {
        return new StringJoiner(", ", Elevator.class.getSimpleName() + "[", "]").add("id=" + id)
                .add("minFloor=" + minFloor)
                .add("maxFloor=" + maxFloor)
                .add("currentFloor=" + currentFloor)
                .toString();
    }

    @Override
    public int hashCode() {
        return id;
    }

    /**
     * @return whether this elevator can serve all the specified floors.
     */
    public boolean canServe(int... floors) {
        for (int floor : floors) {
            if (floor < minFloor || floor > maxFloor) {
                return false;
            }
        }
        return true;
    }

    /**
     * @throws IllegalArgumentException if the specified floor cannot be served by this elevator.
     */
    private void rangeCheck(int floor) {
        if (!canServe(floor)) {
            throw new IllegalArgumentException("Elevator cannot serve floor %d, only %d to %d are available".formatted(floor, minFloor, maxFloor));
        }
    }

    /**
     * @return whether this elevator is currently on the specified floor
     * or will at some point visit that floor before all its tasks are done.
     */
    public boolean willVisitFloor(int floor) {
        if (!canServe(floor)) {
            return false;
        }

        if (floor == currentFloor) {
            return true;
        }

        int previousTarget = currentFloor;
        for (int nextTarget : targets) {
            // If the target floor is already on the path between floors, return true
            int min = Math.min(previousTarget, nextTarget);
            int max = Math.max(previousTarget, nextTarget);
            if (min <= floor && floor <= max) {
                return true;
            }
            previousTarget = nextTarget;
        }
        return false;
    }

    /**
     * @return the minimum amount of turns it would take for this elevator to visit a specified sequence of floors
     * (either indirectly by passing by or by creating new tasks), or -1 if it's impossible.
     * @implNote a choice was made to use {@code int} over {@link java.util.OptionalInt OptionalInt}
     * since the amount of turns cannot be negative.
     */
    public int turnsToVisit(int... floors) {
        if (floors.length == 0) {
            return -1;
        }

        int count = 0;
        int previousTarget = currentFloor;
        Iterator<Integer> targetItr = targets.iterator();
        Iterator<Integer> floorItr = Arrays.stream(floors).iterator();
        int nextFloor = floorItr.next();
        while (targetItr.hasNext()) {
            int nextTarget = targetItr.next();

            // While the next floor we're interested in lies on the path,
            // we "chop off" part of the path, adding the length of that part to count
            // we also advance the floor iterator, or return if the floor was last
            while (previousTarget <= nextFloor && nextFloor <= nextTarget || previousTarget >= nextFloor && nextFloor >= nextTarget) {
                count += Math.abs(nextFloor - previousTarget);
                previousTarget = nextFloor;
                if (floorItr.hasNext()) {
                    nextFloor = floorItr.next();
                } else {
                    return count;
                }
            }
            // If there are more floors remaining to check, add what's left of currently inspected path
            count += Math.abs(nextTarget - previousTarget);

            previousTarget = nextTarget;
        }

        // The floor currently at nextFloor is guaranteed to be unprocessed
        count += Math.abs(nextFloor - previousTarget);
        previousTarget = nextFloor;
        // If after traversing the queue we haven't covered all floors that we wanted,
        // simulate adding them to the queue
        while (floorItr.hasNext()) {
            nextFloor = floorItr.next();
            count += Math.abs(nextFloor - previousTarget);
            previousTarget = nextFloor;
        }

        return count;
    }
}
