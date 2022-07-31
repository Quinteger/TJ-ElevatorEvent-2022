package org.togetherjava.event.elevator.elevators;

import org.togetherjava.event.elevator.humans.ElevatorListener;
import org.togetherjava.event.elevator.humans.Passenger;

import java.util.*;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;
import java.util.function.Consumer;

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
        floors.values().forEach(Floor::fireElevatorArrivalEvents);
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
    public synchronized void requestElevator(int atFloor, TravelDirection desiredTravelDirection) {
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

        Elevator elevator = elevators.stream()
                .filter(e -> e.canServe(atFloor, atFloor + (desiredTravelDirection == TravelDirection.UP ? 1 : -1)))
                .min((e1, e2) -> {
                    boolean s1 = e1.canServe(target);
                    boolean s2 = e1.canServe(target);
                    if (s1 && s2) {
                        return Integer.compare(e1.turnsToVisit(target), e2.turnsToVisit(target));
                    } else if (s1) {
                        return -1;
                    } else if (s2) {
                        return 1;
                    } else {
                        return Integer.compare(
                                Math.min(Math.abs(e1.getMaxFloor() - target), Math.abs(e1.getMinFloor() - target)),
                                Math.min(Math.abs(e2.getMaxFloor() - target), Math.abs(e2.getMinFloor() - target))
                        );
                    }
                })
                .orElseThrow(() -> new IllegalStateException("No elevators can go %s from floor %d".formatted(desiredTravelDirection.name(), atFloor)));

        elevator.requestDestinationFloor(atFloor);
    }

    public boolean hasActivePassengers() {
        return floors.values().stream()
                .map(Floor::getActivePassengers)
                .reduce(0, Integer::sum) > 0;
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
        performTasksInParallel(elevators, e -> {
            floors.get(e.getCurrentFloor()).removeElevator(e);
            e.moveOneFloor();
            floors.get(e.getCurrentFloor()).addElevator(e);
        });
    }

    private void fireFloorListeners() {
        performTasksInParallel(floors.values(), f -> {
            f.fireElevatorPassengerEvents();
            f.fireElevatorArrivalEvents();
            f.fireElevatorRequestEvents(this);
        });
    }

    private static <V> void performTasksInParallel(Collection<V> targets, Consumer<V> action) {
        List<? extends ForkJoinTask<?>> tasks = targets.stream().map(target -> new RecursiveAction() {
            @Override
            protected void compute() {
                action.accept(target);
            }
        }).toList();
        ForkJoinTask.invokeAll(tasks);
    }
}
