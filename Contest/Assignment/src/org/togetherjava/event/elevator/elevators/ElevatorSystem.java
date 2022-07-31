package org.togetherjava.event.elevator.elevators;

import org.togetherjava.event.elevator.humans.ElevatorListener;
import org.togetherjava.event.elevator.humans.Passenger;

import java.util.*;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * System controlling all elevators of a building.
 * <p>
 * Once all elevators and humans have been registered via {@link #registerElevator(Elevator)}
 * and {@link #registerElevatorListener(ElevatorListener)} respectively,
 * the system can be made ready using {@link #ready()}.
 */
public final class ElevatorSystem implements FloorPanelSystem {
    private final Collection<Elevator> elevators = new HashSet<>();
    private final Collection<ElevatorListener> elevatorListeners = new HashSet<>();
    private final NavigableMap<Integer, Floor> floors = new TreeMap<>();

    public void registerElevator(Elevator elevator) {
        elevators.add(elevator);
        elevator.setElevatorSystem(this);

        for (int i = elevator.getMinFloor(); i <= elevator.getMaxFloor(); i++) {
            floors.computeIfAbsent(i, Floor::new);
        }

        floors.get(elevator.getCurrentFloor()).addElevator(elevator);
    }

    public void registerElevatorListener(ElevatorListener listener) {
        elevatorListeners.add(listener);
        if (listener instanceof Passenger passenger && passenger.getStartingFloor() != passenger.getDestinationFloor()) {
            floors.get(passenger.getCurrentFloor()).addPassenger(passenger);
        }
    }

    /**
     * Upon calling this, the system is ready to receive elevator requests. Elevators may now start moving.<br>
     * <br>
     * Additionally, elevator arrival events are fired so that humans can immediately enter them.
     */
    public void ready() {
        elevatorListeners.forEach(listener -> listener.onElevatorSystemReady(this));
        floors.values().forEach(Floor::fireWaitingPassengerListeners);
    }

    void passengerEnteredElevator(Passenger passenger) {
        floors.get(passenger.getCurrentFloor()).removePassenger(passenger);
    }

    void passengerLeftElevator(Passenger passenger, boolean arrived) {
        if (!arrived) {
            floors.get(passenger.getCurrentFloor()).addPassenger(passenger);
        }
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
            throw new IllegalStateException("An elevator was requested, but there are none registered in the system");
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
        fireFloorListeners();
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
            int maxFloor = floors.lastEntry().getKey();
            if (floorFrom >= maxFloor) {
                return OptionalInt.empty();
            } else {
                int delta = maxFloor - floorFrom;
                delta = delta % 2 == 0 ? delta / 2 : delta / 2 + 1;
                return OptionalInt.of(floorFrom + delta);
            }
        } else {
            int minFloor = floors.firstEntry().getKey();
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
        performTasksInParallel(elevators, MoveElevatorTask::new);
    }

    private void fireFloorListeners() {
        performTasksInParallel(floors.values(), FireListenersTask::new);
    }

    private static <V, T extends ForkJoinTask<?>> void performTasksInParallel(Collection<V> targets, Function<V, T> taskCreator) {
        List<T> tasks = targets.stream().map(taskCreator).toList();
        ForkJoinTask.invokeAll(tasks);
    }

    private class MoveElevatorTask extends RecursiveAction {
        private final Elevator elevator;

        MoveElevatorTask(Elevator elevator) {
            this.elevator = elevator;
        }

        @Override
        protected void compute() {
            floors.get(elevator.getCurrentFloor()).removeElevator(elevator);
            elevator.moveOneFloor();
            floors.get(elevator.getCurrentFloor()).addElevator(elevator);
        }
    }

    private static class FireListenersTask extends RecursiveAction {
        private final Floor floor;

        FireListenersTask(Floor floor) {
            this.floor = floor;
        }

        @Override
        protected void compute() {
            floor.fireAllListeners();
        }
    }
}
