package org.togetherjava.event.elevator.elevators;


import java.util.concurrent.RecursiveAction;

class MoveElevatorTask extends RecursiveAction {
    private final Elevator elevator;

    MoveElevatorTask(Elevator elevator) {
        this.elevator = elevator;
    }

    @Override
    protected void compute() {
        elevator.moveOneFloor();
    }
}
