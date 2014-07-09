/**
 * Copyright (c) 2014, by the Authors: John E Lloyd (UBC)
 *
 * This software is freely available under a 2-clause BSD license. Please see
 * the LICENSE file in the ArtiSynth distribution directory for details.
 */
package maspack.apps;

import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;

import maspack.geometry.*;
import maspack.matrix.AffineTransform3d;
import maspack.matrix.AffineTransform3dBase;
import maspack.matrix.AxisAngle;
import maspack.matrix.Point3d;
import maspack.matrix.RigidTransform3d;
import maspack.util.NumberFormat;
import maspack.render.RenderableUtils;
import argparser.ArgParser;
import argparser.BooleanHolder;
import argparser.DoubleHolder;
import argparser.StringHolder;

/**
 * Class to transform a mesh from one type to another
 */
public class MeshTransform {
   static StringHolder formatStr = new StringHolder ("%15.10f");
   static StringHolder inFileName = new StringHolder (null);
   static StringHolder outFileName = new StringHolder (null);
   static double[] xyz = new double[3];
   static double[] scaleXyz = new double[] { 1, 1, 1 };
   static double[] laplacianSmoothing = new double[] { 0, 0.7, 0 };
   static BooleanHolder removeDisconnectedFaces = new BooleanHolder (false);
   static BooleanHolder removeDisconnectedVertices = new BooleanHolder (false);
   static double[] axisAngle = new double[4];
   static DoubleHolder scaling = new DoubleHolder (1.0);
   static DoubleHolder closeVertexTol = new DoubleHolder (-1.0);
   static BooleanHolder zeroIndexedIn = new BooleanHolder (false);
   static BooleanHolder zeroIndexedOut = new BooleanHolder (false);
   static BooleanHolder pointMesh = new BooleanHolder (false);
   static StringHolder className =
      new StringHolder ("maspack.geometry.PolygonalMesh");

   public static void main (String[] args) {
      ArgParser parser = new ArgParser ("[options] <infileName>");
      //parser.addOption ("-inFile %s #input file name", inFileName);
      parser.addOption ("-out %s #output file name", outFileName);
      parser.addOption ("-xyz %fX3 #x,y,z translation values", xyz);
      parser.addOption ("-scaleXyz %fX3 #x,y,z scale values", scaleXyz);
      parser.addOption (
         "-laplacianSmooth %fX3 #iter count, lambda, mu", laplacianSmoothing);
      parser.addOption (
         "-removeDisconnectedFaces %v #remove disconnected faces",
         removeDisconnectedFaces);
      parser.addOption (
         "-removeDisconnectedVertices %v #remove disconnected vertices",
         removeDisconnectedVertices);
      parser.addOption (
         "-mergeCloseVertices %f #(close vertex distance)/(mesh radius)",
         closeVertexTol);
      parser.addOption (
         "-axisAngle %fX4 #x,y,z,deg axis-angle values", axisAngle);
      parser.addOption ("-scale %f #scaling value", scaling);
      parser.addOption (
         "-format %s #printf-syle format string for vertex output", formatStr);
      parser.addOption (
         "-zeroIndexedIn %v #input is zero indexed", zeroIndexedIn);
      parser.addOption (
         "-zeroIndexedOut %v #output should be zero indexed", zeroIndexedOut);
      parser.addOption ("-class %s #use PolygonalMesh sub class", className);
      parser.addOption ("-pointMesh %v #mesh is a point cloud", pointMesh);

      int idx = 0;
      while (idx < args.length) {
         try {
            idx = parser.matchArg (args, idx);
            if (parser.getUnmatchedArgument() != null) {
               String fileName = parser.getUnmatchedArgument();
               if (inFileName.value == null) {
                  inFileName.value = fileName;
               }
               else {
                  System.out.println ("Ignoring extra input "+fileName);
               }
            }
         }
         catch (Exception e) {
            // malformed or erroneous argument
            parser.printErrorAndExit (e.getMessage());
         }
      }      

      PolygonalMesh mesh = null;

      if (inFileName.value == null) {
         parser.printErrorAndExit ("input file name missing");
      }
      if (outFileName.value == null) {
         parser.printErrorAndExit ("output file name missing");
      }

      File outFile = new File (outFileName.value);
      if (outFile.exists()) {
         System.out.println ("File "+outFileName.value+" exists. Overwrite?");
         String input = System.console().readLine();
         if (!input.equalsIgnoreCase ("y")) {
            System.out.println ("aborting");
            System.exit(1); 
         }
      }

      try {
         File meshFile = new File (inFileName.value);

         if (!meshFile.exists()) {
            System.out.println (
               "Error: mesh file " + meshFile.getName() + " not found");
            meshFile = null;
            System.exit (1);
         }

         Class meshClass = Class.forName (className.value);
         Class meshBaseClass = new PolygonalMesh().getClass();

         if (meshClass == null) {
            System.out.println ("can't find class " + className.value);
            System.exit (1);
         }
         if (!meshBaseClass.isAssignableFrom (meshClass)) {
            System.out.println (
               className.value+" is not an instance of "+
               meshBaseClass.getName());
            System.exit (1);
         }
         Constructor constructor =
            meshClass.getDeclaredConstructor (new Class[] {}); // meshFile.getClass()});
         if (constructor == null) {
            System.out.println (
               "can't find constructor " + className.value + "()");
            System.exit (1);
         }
         mesh = (PolygonalMesh)constructor.newInstance (new Object[] {}); // meshFile
         mesh.read (
            new BufferedReader (new FileReader (meshFile)),
            zeroIndexedIn.value);
      }
      catch (Exception e) {
         System.out.println ("Error creating mesh object");
         e.printStackTrace();
         System.exit (1);
      }

      if (removeDisconnectedVertices.value) {
         System.out.println ("removing disconnected vertices ...");
         int nv = mesh.removeDisconnectedVertices();
         if (nv > 0) {
            System.out.println (" removed "+nv);
         }
         System.out.println ("done");
      }

      if (closeVertexTol.value >= 0) {
         double rad = mesh.computeRadius();
         double dist = rad*closeVertexTol.value;
         System.out.println ("removing vertices within "+dist+ " ...");
         int nv = mesh.mergeCloseVertices (dist);
         if (nv > 0) {
            System.out.println (" removed "+nv);
         }
         mesh.checkForDegenerateFaces();
         System.out.println ("done");
      }
 
      if (removeDisconnectedFaces.value) {
         System.out.println ("removing disconnected faces ...");
         int nf = mesh.removeDisconnectedFaces();
         if (nf > 0) {
            System.out.println (" removed "+nf);
         }
         System.out.println ("done");
      }

      if (laplacianSmoothing[0] != 0) {
         System.out.println ("smoothing ...");
         int iter = (int)laplacianSmoothing[0];
         double lam = laplacianSmoothing[1];
         double mu = laplacianSmoothing[2];
         LaplacianSmoother.smooth (mesh, iter, lam, mu);
         System.out.println ("done");
      }

      if (!mesh.isClosed()) {
         System.out.println ("WARNING: mesh is not closed");
      }

      boolean translate = false;
      boolean rotate = false;
      if (xyz[0] != 0 || xyz[1] != 0 || xyz[2] != 0) {
         translate = true;
      }
      if (axisAngle[3] != 0) {
         rotate = true;
      }
      axisAngle[3] = Math.toRadians (axisAngle[3]);

      AffineTransform3dBase X = null;
      if (scaling.value != 1 || scaleXyz[0] != 1 || scaleXyz[1] != 1 ||
          scaleXyz[2] != 1) {
         AffineTransform3d A = new AffineTransform3d();
         X = A;
         X.setTranslation (new Point3d (xyz));
         X.setRotation (new AxisAngle (axisAngle));
         A.applyScaling (scaleXyz[0] * scaling.value, scaleXyz[1]
         * scaling.value, scaleXyz[2] * scaling.value);
      }
      else if (translate || rotate) {
         X = new RigidTransform3d();
         X.setTranslation (new Point3d (xyz));
         X.setRotation (new AxisAngle (axisAngle));
      }

      boolean facesClockwise = false;
      if (X != null) {
         mesh.transform (X);
         if (X.getMatrix().determinant() < 0) {
            // then mesh has been flipped and we need to flip face ordering
            facesClockwise = true;
         }
      }

      try {
         mesh.write (
            new PrintWriter (
               new BufferedWriter (
                  new FileWriter (outFile))),
            new NumberFormat(formatStr.value),
            zeroIndexedOut.value, facesClockwise);
      }
      catch (Exception e) {
         e.printStackTrace();
         System.exit (1);
      }
   }
}
