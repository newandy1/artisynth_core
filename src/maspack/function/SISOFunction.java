/**
 * Copyright (c) 2014, by the Authors: Antonio Sanchez (UBC)
 *
 * This software is freely available under a 2-clause BSD license. Please see
 * the LICENSE file in the ArtiSynth distribution directory for details.
 */
package maspack.function;

/**
 * Single-input single-output
 */
public interface SISOFunction {

   /**
    * Evaluates the function at a prescribed input value.
    *
    * @param x input value
    * @return function output value
    */
   double eval (double x);
}
