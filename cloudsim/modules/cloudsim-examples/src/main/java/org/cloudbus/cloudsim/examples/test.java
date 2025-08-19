package org.cloudbus.cloudsim.examples;

import scpsolver.constraints.*;
import scpsolver.lpsolver.*;
import scpsolver.problems.LinearProgram;




public class test {

    public static void main(String[] var0) {
        LinearProgram lp = new LinearProgram(new double[]{4.0,5.0});
        lp.addConstraint(new LinearSmallerThanEqualsConstraint(new double[]{1.0,1.0}, 20.0, "c1"));
        lp.addConstraint(new LinearSmallerThanEqualsConstraint(new double[]{3.0,4.0}, 72.0, "c2"));
        lp.setMinProblem(true);
        LinearProgramSolver solver  = SolverFactory.newDefault();
        double[] sol = solver.solve(lp);
    }
}
