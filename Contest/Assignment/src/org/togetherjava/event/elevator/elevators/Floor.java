package org.togetherjava.event.elevator.elevators;

import org.togetherjava.event.elevator.humans.ElevatorListener;
import org.togetherjava.event.elevator.humans.Human;
import org.togetherjava.event.elevator.humans.Passenger;

import java.util.Collection;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

class Floor {
    private final int number;
    private final Collection<Passenger> passengers = ConcurrentHashMap.newKeySet();
    private final Collection<Elevator> elevators = ConcurrentHashMap.newKeySet();

    Floor(int number) {
        this.number = number;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Floor.class.getSimpleName() + "[", "]")
                .add("number=" + number)
                .toString();
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

    synchronized int getActivePassengers() {
        return passengers.size() + elevators.stream().map(e -> e.getPassengers().size()).reduce(0, Integer::sum);
    }

    synchronized void fireElevatorPassengerEvents() {
        for (Elevator elevator : elevators) {
            for (ElevatorListener passenger : elevator.getPassengers()) {
                passenger.onElevatorArrivedAtFloor(elevator);
            }
        }
    }

    synchronized void fireElevatorArrivalEvents() {
        for (Passenger passenger : passengers) {
            for (Elevator elevator : elevators) {
                passenger.onElevatorArrivedAtFloor(elevator);
            }
        }
    }

    synchronized void fireElevatorRequestEvents(FloorPanelSystem floorPanelSystem) {
        for (Passenger passenger : passengers) {
            passenger.onElevatorSystemReady(floorPanelSystem);
        }
    }
}
