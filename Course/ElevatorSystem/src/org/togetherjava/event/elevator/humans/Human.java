package org.togetherjava.event.elevator.humans;

import org.togetherjava.event.elevator.elevators.ElevatorPanel;
import org.togetherjava.event.elevator.elevators.FloorPanelSystem;

import java.util.OptionalInt;

/**
 * A single human that starts at a given floor and wants to
 * reach a destination floor via using an elevator.
 * <p>
 * The class mainly acts upon given elevator events it listens to,
 * for example requesting an elevator, eventually entering and exiting them.
 */
public final class Human implements ElevatorListener {
    private State currentState;
    private final int startingFloor;
    private final int destinationFloor;
    /**
     * If the human is currently inside an elevator, this is its unique ID.
     * Otherwise, this is {@code null} to indicate that the human is currently on the corridor.
     */
    private Integer currentEnteredElevatorId;

    /**
     * Creates a new human.
     * <p>
     * It is supported that starting and destination floor are equals.
     * The human will then not travel with an elevator at all.
     *
     * @param startingFloor    the floor the human currently stands at, must be greater equals 1
     * @param destinationFloor the floor the human eventually wants to reach, must be greater equals 1
     */
    public Human(int startingFloor, int destinationFloor) {
        if (startingFloor <= 0 || destinationFloor <= 0) {
            throw new IllegalArgumentException("Floors must be at least 1");
        }

        this.startingFloor = startingFloor;
        this.destinationFloor = destinationFloor;

        currentState = State.IDLE;
    }

    public State getCurrentState() {
        return currentState;
    }

    public int getStartingFloor() {
        return startingFloor;
    }

    public int getDestinationFloor() {
        return destinationFloor;
    }

    @Override
    public void onElevatorSystemReady(FloorPanelSystem floorPanelSystem) {
        System.out.println("Ready-event received");
    }

    @Override
    public void onElevatorArrivedAtFloor(ElevatorPanel elevatorPanel) {
        System.out.println("Arrived-event received");
    }

    public OptionalInt getCurrentEnteredElevatorId() {
        return currentEnteredElevatorId == null
                ? OptionalInt.empty()
                : OptionalInt.of(currentEnteredElevatorId);
    }

    public enum State {
        IDLE,
        WAITING_FOR_ELEVATOR,
        TRAVELING_WITH_ELEVATOR,
        ARRIVED
    }

    // NOTE Put any extra code here, then it carries over to the next task
}
