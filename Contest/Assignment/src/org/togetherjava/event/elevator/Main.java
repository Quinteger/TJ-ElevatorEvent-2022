package org.togetherjava.event.elevator;

import org.togetherjava.event.elevator.simulation.MoreSimulations;
import org.togetherjava.event.elevator.simulation.Simulation;

public final class Main {
    /**
     * Starts the application.
     * <p>
     * Will create an elevator-system simulation, execute it until it is done
     * and pretty print the state to console.
     *
     * @param args Not supported
     */
    public static void main(final String[] args) {
        // Select a desired simulation for trying out your code.
        // Start with the simple simulations first, try out the bigger systems once you got it working.
        // Eventually try out the randomly generated systems. If you want to debug a problem you encountered
        // with one of them, note down the seed that it prints at the beginning and then use the variant that takes this seed.
        // That way, it will generate the same system again, and you can repeat the test.
//        Simulation simulation = Simulation.createSingleElevatorSingleHumanSimulation();
//         Simulation simulation = Simulation.createSimpleSimulation();
//         Simulation simulation = Simulation.createRandomSimulation(5, 50, 10);
        // Simulation simulation = Simulation.createRandomSimulation(putDesiredSeedHere, 5, 50, 10);
        Simulation simulation = MoreSimulations.createMegaSimulation();
//        Simulation simulation = MoreSimulations.createMegaAdvancedSimulation();
//        Simulation simulation = MoreSimulations.createSimpleFailingSimulation();
//        Simulation simulation = MoreSimulations.createSimpleSucceedingSimulation();
//        Simulation simulation = MoreSimulations.createSimpleThreeStepSimulation();
//        Simulation simulation = Simulation.createRandomSimulation(1, 100, 1000, 50);
//        Simulation simulation = MoreSimulations.createSimplePaternosterSimulation();
//        Simulation simulation = MoreSimulations.createSimpleAdvancedSimulation();

        simulation.printSummary();

        long simulationStart = System.nanoTime();
        System.out.println("Starting simulation...");
        simulation.start();
        simulation.prettyPrint();

        while (!simulation.isDone()) {
            System.out.println("\tSimulation step " + simulation.getStepCount());
            simulation.step();
            if (simulation.shouldPrettyPrint()) {
                simulation.prettyPrint();
            }
//            simulation.printCurrentStatistics();

            if (simulation.getStepCount() >= 100_000) {
                throw new IllegalStateException("Simulation aborted. All humans should have arrived"
                        + " by now, but they did not. There is likely a bug in your code.");
            }
        }
        long simulationEnd = System.nanoTime();
        System.out.printf("Simulation completed in %.3f seconds.%n", (simulationEnd - simulationStart) / 1e9);

        simulation.printResult();
    }
}
