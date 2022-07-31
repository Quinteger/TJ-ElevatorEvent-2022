package org.togetherjava.event.elevator.elevators;

import org.togetherjava.event.elevator.humans.ElevatorListener;

import java.util.List;
import java.util.concurrent.RecursiveAction;

class FireListenersTask extends RecursiveAction {
    private final Elevator elevator;
    private final List<ElevatorListener> listeners;

    FireListenersTask(Elevator elevator, List<ElevatorListener> listeners) {
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
