package org.cloudbus.cloudsim.examples;
import scpsolver.constraints.LinearBiggerThanEqualsConstraint;
import scpsolver.constraints.LinearSmallerThanEqualsConstraint;
import scpsolver.lpsolver.LinearProgramSolver;
import scpsolver.lpsolver.SolverFactory;
import scpsolver.problems.LinearProgram;

public class GeneralizedAssignmentProblem {

    public static void solveProblem(double[][] p, double[][] w, double[] t, int numAgents, int numTasks) {
        // 1. Set objective function (maximize total profit with minimum)
        // Variables are ordered as: x_11, x_12, ..., x_1n, x_21, x_22, ..., x_2n, ..., x_m1, x_m2, ..., x_mn
        int numVars = numAgents * numTasks; // Total number of variables (for x_ij)
        double[] objFun = new double[numVars];
        for (int i = 0; i < numAgents; i++) {
            for (int j = 0; j < numTasks; j++) {
                int varIndex = i * numTasks + j;  // Convert (i,j) to linear index
                objFun[varIndex] = p[i][j];  // Coefficient p_ij for variable x_ij
            }
        }
        LinearProgram lp = new LinearProgram(objFun);

        // 2. Constraint 1 (Agent Capacity Constraint):
        // sum_j w_ij * x_ij <= t_i for i = 1,...,m
        for (int i = 0; i < numAgents; i++) {
            double[] constraintCoeff = new double[numVars];

            // Set coefficients for this constraint
            for (int j = 0; j < numTasks; j++) {
                int varIndex = i * numTasks + j;
                constraintCoeff[varIndex] = w[i][j];  // w_ij coefficient
            }

            // Add constraint: sum <= t_i
            lp.addConstraint(new LinearSmallerThanEqualsConstraint(constraintCoeff, t[i], "capacity_" + i));
        }

        // 3. Constraint 2 Add task assignment constraints (each task assigned exactly once):
        // Add constraints: sum_i x_ij <= 1 for j = 1,...,n
        for (int j = 0; j < numTasks; j++) {
            double[] constraintCoeff = new double[numVars];

            // Set coefficients for this constraint
            for (int i = 0; i < numAgents; i++) {
                int varIndex = i * numTasks + j;
                constraintCoeff[varIndex] = 1.0;  // coefficient 1 for x_ij
            }

            // Add constraint: sum <= 1
            lp.addConstraint(new LinearSmallerThanEqualsConstraint(constraintCoeff, 1.0, "assignment_" + j));
        }

        // Set binary constraints (0 <= x_ij <= 1, integer)
        for (int i = 0; i < numVars; i++) {
            lp.setBinary(i);
        }

        // Solve the problem
        LinearProgramSolver solver = SolverFactory.newDefault();
        double[] solution = solver.solve(lp);

        // Print results
        if (solution != null) {
            System.out.println("Optimal solution found!");
            System.out.println("Objective value: " + lp.evaluate(solution));

            System.out.println("\nVariable values:");
            for (int i = 0; i < numAgents; i++) {
                for (int j = 0; j < numTasks; j++) {
                    int varIndex = i * numTasks + j;
                    if (solution[varIndex] != 0) {  // Since it's binary, check if >= 0.5
                        System.out.println("x[" + (i+1) + "][" + (j+1) + "] = " + solution[varIndex]);
                    }
                }
            }
        } else {
            System.out.println("No feasible solution found!");
        }
    }

    public static void main(String[] args) {
        // Example
        int numAgents = 3;  // number of agents.
        int numTasks = 4;   // number of tasks.

        // Profit matrix p_ij
        double[][] p = {
                {10, 15, 12, 8},
                {14, 11, 13, 9},
                {16, 10, 14, 12}
        };

        // Weight matrix w_ij
        double[][] w = {
                {5, 7, 6, 4},
                {8, 6, 7, 5},
                {9, 5, 8, 6}
        };

        // Capacity limits t_i
        double[] t = {15, 18, 20};

        // Solve the problem
        solveProblem(p, w, t, numAgents, numTasks);
    }
}
