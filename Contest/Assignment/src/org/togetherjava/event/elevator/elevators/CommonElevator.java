package org.togetherjava.event.elevator.elevators;

import org.togetherjava.event.elevator.util.CollectionUtils;

import java.util.*;

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
        }
    }

    /**
     * Add a floor to the task queue of this elevator. Either as a new element at the end of the queue,
     * or by modifying the last element if it's possible to do so without changing elevator semantics.<br>
     * After that, try to find an optimal path through all targets and repopulate the targets' collection, if necessary.<br>
     * It is expected that this method is called in a synchronized context.
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

        var optimalTargetsRecord = rearrangeTargets(currentFloor, targets);
        var optimalTargets = optimalTargetsRecord.targets();

        if (!CollectionUtils.equals(targets, optimalTargets)) {
            System.out.printf("Elevator on floor %d is rearranging targets after receiving new floor %d, would-be %s, new %s%n", currentFloor, targetFloor, targets, optimalTargets);
            targets.clear();
            targets.addAll(optimalTargets);
        }

//        System.out.printf("Elevator %d on floor %d has added floor %d to the queue, the queue is now %s, potential targets %s, queue length in turns is %d%n",
//                id, currentFloor, targetFloor, targets, potentialTargets, optimalTargetsRecord.cost());
    }

    /**
     * A recursive method that tries to find an optimal path from a specified starting point through all the targets
     * in the specified deque.<br>
     * It is assumed that the input deque is in synchronized context and does not contain duplicates.<br>
     * No guarantee is made that the deque reference contained in the returned record will be the same
     * or different to the input deque.
     */
    private static OptimalTargetsAndCost rearrangeTargets(int from, Deque<Integer> targets) {
        int size = targets.size();
        if (size == 0) {
            // No targets - no need to move
            return new OptimalTargetsAndCost(from, targets, 0);
        } else if (size == 1) {
            // One target - calculate distance to it
            return new OptimalTargetsAndCost(from, targets, Math.abs(targets.getFirst() - from));
        } else if (size == 2) {
            // Two targets - simple enough to do a manual calculation on them
            int e1 = targets.getFirst();
            int e2 = targets.getLast();
            // c1 represents cost as is, c2 represents cost if the two elements were flipped
            int c1 = Math.abs(e2 - e1) + Math.abs(e1 - from);
            int c2 = Math.abs(e1 - e2) + Math.abs(e2 - from);
            int cost;
            if (c2 < c1) {
                // Flip the two elements
                targets.addLast(targets.removeFirst());
                cost = c2;
            } else {
                cost = c1;
            }
            return new OptimalTargetsAndCost(from, targets, cost);
        } else {
            // Anything with N targets, where N > 2, gets decomposed into N - 1 recursive method calls,
            // one for each of the new starting points
            return targets.stream()
                    // First off, decompose and do recursive calls
                    .map(nextFrom -> {
                        var recursiveTargets = new ArrayDeque<Integer>(targets.size() - 1);
                        targets.iterator().forEachRemaining(e -> {
                            if (!e.equals(nextFrom)) {
                                recursiveTargets.addLast(e);
                            }
                        });
                        return rearrangeTargets(nextFrom, recursiveTargets);
                    })
                    // Then, filter out the result with the smallest potential cost
                    .min(Comparator.comparingInt(r -> r.cost() + Math.abs(r.from() - from)))
                    // Finally, perform the object creation since
                    .map(r -> {
                        var oldTargets = r.targets();
                        var newTargets = new ArrayDeque<Integer>(oldTargets.size() + 1);
                        newTargets.addAll(oldTargets);
                        newTargets.addFirst(r.from());
                        return new OptimalTargetsAndCost(from, newTargets, r.cost() + Math.abs(r.from() - from));
                    })
                    .orElseThrow(() -> new RuntimeException("Target sorting is functioning incorrectly"));
        }
    }

    /**
     * A record that holds intermediate search results.
     *
     * @param from starting point
     * @param targets a deque holding points to visit, sorted in the optimal order
     * @param cost the amount of turns it would take to visit all points
     */
    private record OptimalTargetsAndCost(int from, Deque<Integer> targets, int cost) {}

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

        int min = currentFloor;
        int max = currentFloor;
        for (int nextTarget : targets) {
            min = Math.min(min, nextTarget);
            max = Math.max(max, nextTarget);
            if (min <= floor && floor <= max) {
                return true;
            }
        }
        return false;
    }
}
