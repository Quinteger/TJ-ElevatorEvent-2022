package org.togetherjava.event.elevator.elevators;

/**
 * A single elevator that can serve a given amount of floors.
 * <p>
 * This elevator can take floor requests from either humans or the elevator system itself.
 * The elevator will eventually move towards the requested floor and transport humans to their destinations.
 */
public final class CommonElevator extends Elevator {
    /**
     * Creates a new elevator.
     *
     * @param minFloor     the minimum floor that the elevator can serve, must be greater than or equal to 1.
     * @param floorsServed the amount of floors served in total by this elevator, must be greater than or equal to 2.
     *                     Together with the minFloor this forms a consecutive range of floors with no gaps in between.
     * @param currentFloor the floor the elevator starts at, must be within the defined range of floors served by the elevator
     */
    public CommonElevator(int minFloor, int floorsServed, int currentFloor) {
        super(minFloor, floorsServed, currentFloor);
    }

    @Override
    public boolean canRequestDestinationFloor() {
        return true;
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

    @Override
    protected void modifyTargetsOnArrival() {
        targets.remove();
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
}
