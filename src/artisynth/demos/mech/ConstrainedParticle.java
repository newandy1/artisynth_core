package artisynth.demos.mech;

import maspack.geometry.*;
import maspack.spatialmotion.*;
import maspack.matrix.*;
import maspack.render.*;
import maspack.util.*;
import maspack.fileutil.*;
import artisynth.core.mechmodels.*;
import artisynth.core.mechmodels.MechSystemSolver.Integrator;
import artisynth.core.modelbase.*;
import artisynth.core.probes.*;
import artisynth.core.util.*;
import artisynth.core.workspace.RootModel;
import artisynth.core.gui.*;
import artisynth.core.driver.*;
import maspack.render.*;

import java.awt.Color;
import java.io.*;

import javax.swing.JFrame;

public class ConstrainedParticle extends RootModel {

   public ConstrainedParticle() {
      super (null);
   }

   public ConstrainedParticle (String name) {
      this();
      setName (name);

      MechModel mech = new MechModel ("mech");
      mech.setGravity (0, 0, -9.8);

      double wx = 1;
      double wy = 1;
      double rbase = 0.3;
      double rcylinder = 0.2;
      double wz = 0.5;
      int nsegs = 32;
      double density = 1000;

      PolygonalMesh mesh = null;

      String meshFile = PathFinder.expand (
         "$ARTISYNTH_HOME/src/maspack/geometry/sampleData/bowl.obj");

      try {
         mesh = new PolygonalMesh (new File(meshFile));
         mesh.triangulate();
         mesh.scale (0.5);
         mesh.transform (new RigidTransform3d (0, 0, -0.5, 0, 0, Math.PI/2));
      }
      catch (Exception e) {
         e.printStackTrace(); 
      }
      // PolygonalMesh mesh = MeshFactory.createHollowedBox (
      //    wx, wy, wz, rbase, nsegs);

      //PolygonalMesh mesh = MeshFactory.createTriangularBox (wx, wy, wz);
      //PolygonalMesh mesh = MeshFactory.createTriangularSphere (wx/2, 6);
      //mesh.transform (new RigidTransform3d (0, 0, 0, 0, Math.PI/2, 0));
      MeshComponent body = new FixedMesh (mesh);
      mech.addMeshBody (body);

      RenderProps.setPointStyle (mech, RenderProps.PointStyle.SPHERE);
      RenderProps.setPointRadius (mech, 0.02);
      RenderProps.setPointColor (mech, Color.RED);

      // setting for cylinder
      //Particle p = new Particle (5, -0.28839441, 0, -0.058345216);
      double ang = Math.toRadians (15);
      double rad = 0.425;
      //Particle p = new Particle (5, -rad*Math.cos(ang), 0, -rad*Math.sin(ang));
      Particle p = new Particle (5, -0.4, -0.3, 0.7);
      p.setVelocity (0, 1.0, -0.5);
      ParticleMeshConstraint c = new ParticleMeshConstraint (p, mesh);
      c.setUnilateral (true);

      mech.addParticle (p);
      mech.addConstrainer (c);

      mech.setProfiling (true);
      //mech.setGravity (new Vector3d (0, 0, -.01)
      mech.setPointDamping (5);


      addModel (mech);
   }
}
