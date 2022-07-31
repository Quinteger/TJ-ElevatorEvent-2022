package org.togetherjava.event.elevator.elevators;

import org.togetherjava.event.elevator.humans.ElevatorListener;
import org.togetherjava.event.elevator.humans.Passenger;

/**
 * The system inside an elevator which provides information about the elevator and can be
 * used to request a destination floor.
 */
public interface ElevatorPanel {
    /**
     * The unique ID of the elevator.
     *
     * @return the unique ID of the elevator
     */
    int getId();

    /**
     * The floor the elevator is currently at.
     *
     * @return the current floor
     */
    int getCurrentFloor();

    /**
     * Requesting the elevator to eventually move to the given destination floor, for humans to exit.
     *
     * @param destinationFloor the desired destination, must be within the range served by this elevator
     */
    void requestDestinationFloor(int destinationFloor);

    /**
     * The lowest floor the elevator can travel to.
     */
    int getMinFloor();

    /**
     * The highest floor the elevator can travel to.
     */
    int getMaxFloor();

    /**
     * Ask the elevator to accept this passenger and to tell the system to remove it from the floor.
     * @param passenger the passenger
     */
    void boardPassenger(Passenger passenger);

    /**
     * Ask the elevator to remove this passenger and to tell the system to add it to the floor, if necessary.
     * @param passenger the passenger
     * @param arrived whether the passenger has reached its final destination
     * @throws IllegalArgumentException if the elevator does not have the specified passenger
     */
    void removePassenger(Passenger passenger, boolean arrived);
}
