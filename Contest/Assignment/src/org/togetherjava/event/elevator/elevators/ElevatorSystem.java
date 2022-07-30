package org.togetherjava.event.elevator.elevators;

import org.togetherjava.event.elevator.humans.ElevatorListener;

import java.util.*;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;

/**
 * System controlling all elevators of a building.
 * <p>
 * Once all elevators and humans have been registered via {@link #registerElevator(Elevator)}
 * and {@link #registerElevatorListener(ElevatorListener)} respectively,
 * the system can be made ready using {@link #ready()}.
 */
public final class ElevatorSystem implements FloorPanelSystem {
    private final List<Elevator> elevators = new ArrayList<>();
    private final List<ElevatorListener> elevatorListeners = new ArrayList<>();

    private int minFloor = Integer.MAX_VALUE;
    private int maxFloor = Integer.MIN_VALUE;

    public void registerElevator(Elevator elevator) {
        elevators.add(elevator);
        minFloor = Math.min(minFloor, elevator.getMinFloor());
        maxFloor = Math.max(maxFloor, elevator.getMaxFloor());
    }

    public void registerElevatorListener(ElevatorListener listener) {
        elevatorListeners.add(listener);
    }

    /**
     * Upon calling this, the system is ready to receive elevator requests. Elevators may now start moving.<br>
     * <br>
     * Additionally, elevator arrival events are fired so that humans can immediately enter them.
     */
    public void ready() {
        elevatorListeners.forEach(listener -> {
            listener.onElevatorSystemReady(this);
            elevators.forEach(listener::onElevatorArrivedAtFloor);
        });

    }

    @Override
    public void requestElevator(int atFloor, TravelDirection desiredTravelDirection) {
        // This represents a human standing in the corridor,
        // requesting that an elevator comes to pick them up for travel into the given direction.
        // The system is supposed to make sure that an elevator will eventually reach this floor to pick up the human.
        // The human can then enter the elevator and request their actual destination within the elevator.
        // Ideally this has to select the best elevator among all which can reduce the time
        // for the human spending waiting (either in corridor or in the elevator itself).
        if (elevators.isEmpty()) {
            throw new IllegalStateException("There are no elevators registered in the system");
        }

        int target = calculateAverageTarget(atFloor, desiredTravelDirection)
                .orElseThrow(() -> new IllegalArgumentException("Impossible to travel %s from floor %d".formatted(desiredTravelDirection.name(), atFloor)));

//        NavigableMap<Integer, List<Elevator>> map = elevators.stream()
//                .filter(e -> e.canServe(atFloor, target))
//                .collect(Collectors.groupingBy(e -> e.turnsToVisit(atFloor, target), TreeMap::new, Collectors.toList()));
//
//        System.out.printf("A user wants to travel from floor %d %s and has following options:%n", atFloor, desiredTravelDirection.name());
//
//        map.forEach((cost, elevators) -> {
//            elevators.forEach(elevator -> {
//                System.out.printf("Elevator %d at floor %d with queue %s has cost %d%n", elevator.getId(), elevator.getCurrentFloor(), elevator.targets, cost);
//            });
//        });
//
//        Elevator elevator = map.firstEntry().getValue().get(0);


        Elevator elevator = elevators.stream()
                .filter(e -> e.canServe(atFloor, target))
                .min(Comparator.comparingInt(e -> e.turnsToVisit(atFloor, target)))
                .orElseThrow(() -> new IllegalStateException("No suitable elevator found"));

        elevator.requestDestinationFloor(atFloor);
    }

    public void moveOneFloor() {
        long stepStart = System.nanoTime();
//        elevators.forEach(Elevator::moveOneFloor);
        moveElevators();
        long stepEnd = System.nanoTime();
        System.out.printf("Move one floor took %,d ns%n", stepEnd - stepStart);

        stepStart = System.nanoTime();
//        elevators.forEach(elevator -> elevatorListeners.forEach(listener -> listener.onElevatorArrivedAtFloor(elevator)));
        fireElevatorListeners();
        stepEnd = System.nanoTime();
        System.out.printf("Listener firing took %,d ns%n", stepEnd - stepStart);
    }

    /**
     * Estimate the average target floor that a user might select given a starting floor and a direction.
     * @return {@link OptionalInt} describing the calculated floor number, or empty if the request doesn't make sense
     * (going up from the topmost floor or down from the bottom floor)
     */
    private OptionalInt calculateAverageTarget(int floorFrom, TravelDirection desiredTravelDirection) {
        if (desiredTravelDirection == TravelDirection.UP) {
            if (floorFrom >= maxFloor) {
                return OptionalInt.empty();
            } else {
                int delta = maxFloor - floorFrom;
                delta = delta % 2 == 0 ? delta / 2 : delta / 2 + 1;
                return OptionalInt.of(floorFrom + delta);
            }
        } else {
            if (floorFrom <= minFloor) {
                return OptionalInt.empty();
            } else {
                int delta = floorFrom - minFloor;
                delta = delta % 2 == 0 ? delta / 2 : delta / 2 + 1;
                return OptionalInt.of(floorFrom - delta);
            }
        }
    }

    private void moveElevators() {
        List<MoveElevatorTask> tasks = elevators.stream().map(MoveElevatorTask::new).toList();
        ForkJoinTask.invokeAll(tasks);
    }

    private static class MoveElevatorTask extends RecursiveAction {
        private final Elevator elevator;

        private MoveElevatorTask(Elevator elevator) {
            this.elevator = elevator;
        }

        @Override
        protected void compute() {
            elevator.moveOneFloor();
        }
    }

    private void fireElevatorListeners() {
        List<FireListenersTask> tasks = elevators.stream().map(elevator -> new FireListenersTask(elevator, elevatorListeners)).toList();
        ForkJoinTask.invokeAll(tasks);
    }

    private static class FireListenersTask extends RecursiveAction {
        private final Elevator elevator;
        private final List<ElevatorListener> listeners;

        private FireListenersTask(Elevator elevator, List<ElevatorListener> listeners) {
            this.elevator = elevator;
            this.listeners = listeners;
        }

        @Override
        protected void compute() {
//            List<FireSingleListenerTask> tasks = listeners.stream().map(listener -> new FireSingleListenerTask(elevator, listener)).toList();
//            tasks.forEach(ForkJoinTask::fork);
//            tasks.forEach(ForkJoinTask::join);
            long startTime = System.nanoTime();
            listeners.forEach(listener -> listener.onElevatorArrivedAtFloor(elevator));
            long endTime = System.nanoTime();
            System.out.printf("Single listener took %,d ns%n", endTime - startTime);
        }
    }

    private static class FireSingleListenerTask extends RecursiveAction {
        private final Elevator elevator;
        private final ElevatorListener listener;

        private FireSingleListenerTask(Elevator elevator, ElevatorListener listener) {
            this.elevator = elevator;
            this.listener = listener;
        }

        @Override
        protected void compute() {
            listener.onElevatorArrivedAtFloor(elevator);
        }
    }
}
