package org.togetherjava.event.elevator.simulation;

import org.togetherjava.event.elevator.elevators.Elevator;
import org.togetherjava.event.elevator.humans.Human;

import java.util.List;

/**
 * Separate file to avoid bloating {@link Simulation} with static methods
 */
public class MoreSimulations {

    public static Simulation createSimpleFailingSimulation() {
        return new Simulation(
                List.of(
                        new Elevator(6, 5, 10),
                        new Elevator(1, 5, 5)),
                List.of(
                        new Human(1, 7)));
    }

    public static Simulation createSimpleSucceedingSimulation() {
        return new Simulation(
                List.of(
                        new Elevator(6, 5, 10),
                        new Elevator(1, 6, 5)),
                List.of(
                        new Human(1, 8)));
    }
}
