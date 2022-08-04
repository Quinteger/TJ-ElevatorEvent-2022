package org.togetherjava.event.elevator.elevators;

import lombok.Getter;
import org.togetherjava.event.elevator.humans.Passenger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Common superclass for all elevators.
 * <p>
 * An elevator may be able to take floor requests from either humans or the elevator system itself. In that case,
 * the elevator will eventually move towards the requested floor and transport humans to their destinations.
 */
public abstract class Elevator implements ElevatorPanel {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(0);

    @Getter protected final int id;
    @Getter protected final int minFloor;
    @Getter protected final int maxFloor;
    @Getter protected final Collection<Passenger> passengers = ConcurrentHashMap.newKeySet();
    protected final Deque<Integer> targets = new ArrayDeque<>();
    protected final Collection<Integer> potentialTargets = ConcurrentHashMap.newKeySet();
    @Getter protected int currentFloor;
    /**
     * An elevator should be aware of the system it belongs to.
     */
    protected ElevatorSystem elevatorSystem;

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

    public int getTaskCount() {
        return targets.size();
    }

    void setElevatorSystem(ElevatorSystem elevatorSystem) {
        if (elevatorSystem == null) {
            throw new IllegalArgumentException("Elevator system must not be null");
        }
        this.elevatorSystem = elevatorSystem;
    }

    @Override
    public void boardPassenger(Passenger passenger) {
        if (elevatorSystem == null) {
            throw new IllegalStateException("Elevator is not connected to an elevator system");
        }
        if (passengers.contains(passenger)) {
            throw new IllegalArgumentException("Attempt to add a passenger which is already in the elevator");
        }
        passengers.add(passenger);
        elevatorSystem.passengerEnteredElevator(passenger);
    }

    @Override
    public void removePassenger(Passenger passenger, boolean arrived) {
        if (elevatorSystem == null) {
            throw new IllegalStateException("Elevator is not connected to an elevator system");
        }
        if (!passengers.contains(passenger)) {
            throw new IllegalArgumentException("Attempt to remove a passenger which is not in the elevator");
        }
        passengers.remove(passenger);
        elevatorSystem.passengerLeftElevator(passenger, arrived);
    }

    /**
     * Whether this elevator accepts requests to move to a floor.
     * For example, a paternoster elevator does not, because his movement pattern is predetermined forever.
     * @see #requestDestinationFloor(int)
     */
    public abstract boolean canRequestDestinationFloor();

    /**
     * This represents a human or the elevator system
     * itself requesting this elevator to eventually move to the given floor.
     * The elevator is supposed to memorize the destination in a way that
     * it can ensure to eventually reach it.
     *
     * @throws UnsupportedOperationException if the operation is not supported by this elevator
     * @see #canRequestDestinationFloor()
     */
    @Override
    public abstract void requestDestinationFloor(int destinationFloor);

    void addPotentialTarget(int potentialTarget) {
        if (!willVisitFloor(potentialTarget) && !potentialTargets.contains(potentialTarget)) {
            potentialTargets.add(Math.max(Math.min(potentialTarget, maxFloor), minFloor));
            System.out.printf("Elevator %d on floor %d has added potential target %d, the queue is now %s, potential targets %s%n", id, currentFloor, potentialTarget, targets, potentialTargets);
        }
    }

    /**
     * Essentially there are three possibilities:
     * <ul>
     *  <li>move up one floor</li>
     *  <li>move down one floor</li>
     *  <li>stand still</li>
     *  </ul>
     *  The elevator is supposed to move in a way that it will eventually reach
     *  the floors requested by Humans via {@link #requestDestinationFloor(int)}, ideally "fast" but also "fair",
     *  meaning that the average time waiting (either in corridor or inside the elevator)
     *  is minimized across all humans.
     *  It is essential that this method updates the currentFloor field accordingly.
     */
    public void moveOneFloor() {
        if (!targets.isEmpty()) {
            int target = targets.element();
            if (currentFloor < target) {
                currentFloor++;
            } else if (currentFloor > target) {
                currentFloor--;
            } else {
                throw new IllegalArgumentException("Elevator has current floor as next target, this is a bug");
            }
            potentialTargets.remove(currentFloor);
            if (currentFloor == target) {
                // We arrived at the next target
                modifyTargetsOnArrival();
            }
        } else {
            potentialTargets.clear();
        }
    }

    protected abstract void modifyTargetsOnArrival();

    @Override
    public synchronized String toString() {
        return new StringJoiner(", ", getClass().getSimpleName() + "[", "]").add("id=" + id)
                .add("minFloor=" + minFloor)
                .add("maxFloor=" + maxFloor)
                .add("currentFloor=" + currentFloor)
                .add("passengers=" + passengers.size())
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
     * @return whether this elevator is currently on the specified floor
     * or will at some point visit that floor before all its tasks are done.
     */
    public abstract boolean willVisitFloor(int floor);

    /**
     * @return the minimum amount of turns it would take for this elevator to visit a specified sequence of floors
     * (either indirectly by passing by or by creating new tasks),
     * taking into account all potential targets of this elevator,
     * or -1 if it's impossible or of the input array is empty
     * @implNote a choice was made to use {@code int} over {@link OptionalInt OptionalInt}
     * since the amount of turns cannot be negative
     */
    public int turnsToVisit(int... floors) {
        if (floors.length == 0) {
            return -1;
        }

        int count = 0;
        int previousTarget = currentFloor;

        Collection<Integer> allTargets = new ArrayList<>(targets.size() + potentialTargets.size());
        allTargets.addAll(targets);
        allTargets.addAll(potentialTargets);
        Iterator<Integer> targetItr = allTargets.iterator();
        Iterator<Integer> floorItr = Arrays.stream(floors).iterator();

        int nextFloor = floorItr.next();
        if (!canServe(nextFloor)) {
            return -1;
        }

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
                    if (!canServe(nextFloor)) {
                        return -1;
                    }
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
            if (!canServe(nextFloor)) {
                return -1;
            }

            count += Math.abs(nextFloor - previousTarget);
            previousTarget = nextFloor;
        }

        return count;
    }
}
