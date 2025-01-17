package artisynth.core.inverse;

import java.io.*;
import java.util.List;
import java.util.Collection;

import maspack.matrix.MatrixNd;
import maspack.matrix.VectorNd;
import maspack.util.*;
import maspack.solvers.DantzigQPSolver;
import maspack.solvers.DantzigQPSolver.Status;

/**
 * Solves a quadratic program given a set of cost and constraint terms.
 */
public class QPSolver {

   /*
    * Default solver
    */
   DantzigQPSolver mySolver = new DantzigQPSolver();
   String myQPTestCaseFile = null; // "frameQP.txt";
   int myQPTestCaseCnt = 200;
   int myQPCnt = 0;
   FunctionTimer timer = new FunctionTimer();

   /**
    * Solves the quadratic program of the form:
    * <pre>
    *   min { 1/2 x^T Q x + x^T p } 
    *   A x &gt;= b,
    *   Aeq x = beq
    * </pre>
    * and returns the solution vector. Q and P are assembled
    * form the cost termss, while A, b, Aeq and beq are assembled
    * from the constraint terms, with inequality terms contributing
    * to A and b and equality terms contributing to Aeq and beq.
    *
    * @param costTerms terms used to assemble Q and p
    * @param constraintTerms terms used to assemble A, b, Aeq and beq.
    * @param size size of the program
    * @param t0 time step start time
    * @param t0 time step end time
    * 
    * @return solution to the program
    */
   public VectorNd solve (
      List<QPCostTerm> costTerms,
      List<QPConstraintTerm> constraintTerms, 
      int size, double t0, double t1) {

      MatrixNd Q = new MatrixNd (size, size);
      VectorNd P = new VectorNd (size);

      int numEq = 0;
      int numIneq = 0;
      if (constraintTerms != null) {
         for (QPConstraintTerm term : constraintTerms) {
            if (term.isEnabled()) {
               if (term.getType() == QPConstraintTerm.Type.EQUALITY) {
                  numEq += term.numConstraints (size);
               }
               else if (term.getType() == QPConstraintTerm.Type.INEQUALITY) {
                  numIneq += term.numConstraints (size);
               }
            }
         }
      }

      MatrixNd A = new MatrixNd (numIneq, size);
      VectorNd b = new VectorNd (numIneq);

      MatrixNd Aeq = new MatrixNd (numEq, size);
      VectorNd beq = new VectorNd (numEq);

      VectorNd x = new VectorNd (size);

      // collect all cost terms
      for (QPCostTerm term : costTerms) {
         if (term.isEnabled ()) {
            term.getQP (Q,P,t0,t1);
         }
      }
      int rowEq = 0;
      int rowIneq = 0;

      // collect all constraint terms
      if (constraintTerms != null) {
         for (QPConstraintTerm term : constraintTerms) {
            if (term.isEnabled()) {
               if (term.getType() == QPConstraintTerm.Type.EQUALITY) {
                  rowEq += term.getTerm (Aeq, beq, rowEq, t0, t1);
               }
               else if (term.getType() == QPConstraintTerm.Type.INEQUALITY) {
                  rowIneq += term.getTerm (A, b, rowIneq, t0, t1);
               }
            }
         }
      }

      // solve the problem
      try {
         if (Aeq.rowSize() == 0) {
            // System.out.println ("Q=\n" + Q.toString ("%12.5f"));
            // System.out.println ("P=\n" + P.toString ("%12.5f"));
            // System.out.println ("A=\n" + A.toString ("%12.5f"));
            // System.out.println ("b=\n" + b.toString ("%12.5f"));
            FunctionTimer timer = null;
            if (myQPTestCaseFile != null) {
               if (myQPCnt == myQPTestCaseCnt) {
                  timer = new FunctionTimer();
                  timer.start();
               }
            }
            mySolver.solve (x,Q,P,A,b);
            if (myQPTestCaseFile != null) {
               if (myQPCnt == myQPTestCaseCnt) {
                  timer.stop();
                  System.out.println ("inverse solve time: "+timer.result(1));
                  writeQP (myQPTestCaseFile, Q, P, A, b, x);
               }
               myQPCnt++;
            }
         }
         else {
            Status qpStatus = mySolver.solve (x, Q, P, A, b, Aeq, beq);
            if (qpStatus != Status.SOLVED) {
               System.err.println (
                  "InverseSolve failed: solver status = "+qpStatus.toString ());
            }
         }
      }
      catch (Exception e) {
         e.printStackTrace();
      }
      return x;
   }

   void writeQP (
      String fileName, MatrixNd Q, VectorNd f,
      MatrixNd A, VectorNd b, VectorNd x) {

      try {
         PrintWriter pw = 
            new PrintWriter (new FileWriter ("frameQP.txt"));
         pw.printf ("size: %d nc: %d\n", Q.colSize(), A.rowSize());
         pw.println ("Q:");
         NumberFormat fmt = new NumberFormat ("%g");
         Q.write (pw, fmt);
         pw.println ("f:");
         f.write (pw, fmt);
         pw.println ("\nA:");
         A.write (pw, fmt);
         pw.println ("b:");                  
         b.write (pw, fmt);
         pw.println ("\nx:");                  
         x.write (pw, fmt);
         pw.println ("");
         pw.close();
      }
      catch (Exception e) {
         e.printStackTrace();
      }
   }
}
