package org.togetherjava.event.elevator.elevators;

import org.togetherjava.event.elevator.humans.ElevatorListener;
import org.togetherjava.event.elevator.humans.Passenger;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

class Floor {
    private final int number;
    private final Collection<Passenger> passengers = ConcurrentHashMap.newKeySet();
    private final Collection<Elevator> elevators = ConcurrentHashMap.newKeySet();

    Floor(int number) {
        this.number = number;
    }

    @Override
    public int hashCode() {
        return number;
    }

    void addPassenger(Passenger passenger) {
        passengers.add(passenger);
    }

    void removePassenger(Passenger passenger) {
        passengers.remove(passenger);
    }

    void addElevator(Elevator elevator) {
        elevators.add(elevator);
    }

    void removeElevator(Elevator elevator) {
        elevators.remove(elevator);
    }

    void fireElevatorPassengerEvents() {
        for (Elevator elevator : elevators) {
            for (ElevatorListener passenger : elevator.getPassengers()) {
                passenger.onElevatorArrivedAtFloor(elevator);
            }
        }
    }

    void fireElevatorArrivalEvents() {
        for (Passenger passenger : passengers) {
            for (Elevator elevator : elevators) {
                passenger.onElevatorArrivedAtFloor(elevator);
            }
        }
    }

    void fireElevatorRequestEvents(FloorPanelSystem floorPanelSystem) {
        for (Passenger passenger : passengers) {
            passenger.onElevatorSystemReady(floorPanelSystem);
        }
    }
}
