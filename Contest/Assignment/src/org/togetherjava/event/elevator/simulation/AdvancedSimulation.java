package org.togetherjava.event.elevator.simulation;

import org.togetherjava.event.elevator.elevators.Elevator;
import org.togetherjava.event.elevator.humans.Human;

import java.util.List;
import java.util.Random;

/**
 * A simulation that adds humans while it's running.
 */
public final class AdvancedSimulation extends Simulation {

    public AdvancedSimulation(List<? extends Elevator> elevators, List<Human> humans) {
        super(elevators, humans);
    }

    public static AdvancedSimulation createRandomSimulation(long seed, int amountOfElevators, int amountOfHumans, int floorsServed) {
        return Simulation.createRandomSimulation(seed, amountOfElevators, amountOfHumans, floorsServed, AdvancedSimulation::new);
    }

    @Override
    public void step() {
        if (stepCount > 0 && stepCount % elevatorSystem.getFloorAmount() == 0) {
//            addMoreHumans(elevators.size() / 2);
            addMoreHumans(1);
        }
        super.step();
    }

    private void addMoreHumans(int amount) {
        Random random = new Random();

        int maxFloor = elevatorSystem.getMaxFloor();
        int minFloor = elevatorSystem.getMinFloor();
        for (int i = 0; i < amount; i++) {
            Human human = new Human(
                    random.nextInt(maxFloor - minFloor + 1) + minFloor,
                    random.nextInt(maxFloor - minFloor + 1) + minFloor);
            elevatorSystem.registerElevatorListener(human);
            humans.add(human);
            humanStatistics.add(new HumanStatistics(human));
        }
    }
}
