/**
 * Copyright (c) 2014, by the Authors: Antonio Sanchez (UBC)
 *
 * This software is freely available under a 2-clause BSD license. Please see
 * the LICENSE file in the ArtiSynth distribution directory for details.
 */
package artisynth.core.renderables;

import java.util.Iterator;
import java.util.LinkedList;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;

import maspack.matrix.AffineTransform3dBase;
import maspack.matrix.Point3d;
import maspack.properties.PropertyList;
import maspack.render.DisplayListManager;
import maspack.render.GLRenderer;
import maspack.render.Material;
import maspack.render.PointRenderProps;
import maspack.render.RenderList;
import maspack.render.RenderProps;
import maspack.render.RenderablePoint;
import maspack.render.RenderableUtils;
import artisynth.core.modelbase.RenderableComponentList;
import artisynth.core.util.ScalableUnits;
import artisynth.core.util.TransformableGeometry;

public class VertexList<P extends VertexComponent> extends RenderableComponentList<P>
implements TransformableGeometry, ScalableUnits {

   protected static final long serialVersionUID = 1;
   
   boolean useDisplayLists = false;
   int displayList = 0;
   boolean displayListValid = false;

   public static PropertyList myProps =
      new PropertyList (VertexList.class, RenderableComponentList.class);

   static {
      myProps.get ("renderProps").setDefaultValue (new PointRenderProps());
   }

   public PropertyList getAllPropertyInfo() {
      return myProps;
   }

   public VertexList (Class<P> type) {
      this (type, null, null);
   }
   
   public VertexList (Class<P> type, String name, String shortName) {
      super (type, name, shortName);
      setRenderProps (createRenderProps());
   }
   
   @Override
   protected void notifyStructureChanged(Object comp, boolean stateIsChanged) {
      super.notifyStructureChanged(comp, stateIsChanged);
      displayListValid = false;
   }

   /* ======== Renderable implementation ======= */

   public RenderProps createRenderProps() {
      return RenderProps.createPointProps (this);
   }

   public void prerender (RenderList list) {
      for (int i = 0; i < size(); i++) {
         VertexComponent p = get (i);
         if (p.getRenderProps() != null) {
            list.addIfVisible (p);
         }
         else {
            p.prerender (list);
         }
      }
      displayListValid = false;
   }

   public boolean rendersSubComponents() {
      return true;
   }

   public void render (GLRenderer renderer, int flags) {

      renderer.checkAndPrintGLError();
      
      GL2 gl = renderer.getGL2();
      gl.glPushMatrix();

      RenderProps props = getRenderProps();
      float[] color = props.getPointColorArray();
      float[] selColor = renderer.getSelectionColor().getColorComponents(new float[4]);
      Material pointMaterial = props.getPointMaterial();

      if (isSelected()) {
         color = selColor;
         pointMaterial = renderer.getSelectionMaterial();
      }

      if (useDisplayLists && displayList == 0) {
         displayList  = DisplayListManager.allocList(gl);
         displayListValid = false;
      }
      
      boolean lastSelected = false;

      switch (props.getPointStyle()) {
         case POINT: {

            renderer.setLightingEnabled (false);
            renderer.setPointSize (props.getPointSize());

            if (renderer.isSelecting()) {
               // don't worry about color in selection mode
               int i = 0;
               for (VertexComponent vc : this) {
                  if (vc.getRenderProps() == null) {
                     renderer.beginSelectionQuery (i);
                     gl.glBegin (GL2.GL_POINTS);
                     gl.glVertex3fv (vc.getRenderCoords(), 0);
                     gl.glEnd();
                     renderer.endSelectionQuery ();
                  }
                  i++;
               }
            } else {
               renderer.setColor (color, false);
               if (useDisplayLists && !displayListValid) {
                  gl.glNewList(displayList, GL2.GL_COMPILE_AND_EXECUTE);
               }
               
               if (!displayListValid || renderer.isSelecting()) {
               
                  for (VertexComponent vc : this) {
                     if (vc.getRenderProps() == null) {
   
                        if (vc.isSelected() && !lastSelected) {
                           renderer.setColor(selColor);
                           lastSelected = true;
                        } else if (!vc.isSelected() && lastSelected){
                           renderer.setColor(color);
                        }
                        gl.glBegin (GL2.GL_POINTS);
                        gl.glVertex3fv (vc.getRenderCoords(), 0);
                        gl.glEnd();
                     }
                  }
                  
                  if (useDisplayLists) {
                     gl.glEndList();
                     displayListValid = true;
                  }
               } else if (displayListValid) {
                 gl.glCallList(displayList);
               }
            }
            
            renderer.setPointSize(1);
            renderer.setLightingEnabled(true);
         }
         case SPHERE: {
            renderer.setMaterialAndShading (props, pointMaterial, false);

            if (useDisplayLists && !displayListValid) {
               renderer.validateInternalDisplayLists(props); // ensure valid sphere
               gl.glNewList(displayList, GL2.GL_COMPILE_AND_EXECUTE);
            }
            
            if (!displayListValid || renderer.isSelecting()) {
               int i=0;
               for (VertexComponent vc : this) {
                  if (vc.getRenderProps() == null) {
   
                     if (renderer.isSelecting()) {
                        renderer.beginSelectionQuery (i);
                        renderer.drawSphere (props, vc.getRenderCoords());
                        renderer.endSelectionQuery ();      
                     }  else {
                        if (vc.isSelected() && !lastSelected) {
                           renderer.updateMaterial(props, renderer.getSelectionMaterial(), false);
                           lastSelected = true;
                        } else if (!vc.isSelected() && lastSelected){
                           renderer.updateMaterial(props, pointMaterial, false);
                           lastSelected = false;
                        }
                        renderer.drawSphere (props, vc.getRenderCoords());
                     }
                  }
                  i++;
               }
              
               if (useDisplayLists) {
                  gl.glEndList();
                  displayListValid = true;
               }
            } else {
               gl.glCallList(displayList);
               
               int err = gl.glGetError();
               if (err != GL.GL_NO_ERROR) {
                  System.err.println("GL Error: " + err);
               }
            }
            renderer.restoreShading (props);
         }
      }
      
      

      //         gl.glEndList();
      //         displayListValid = true;
      //      } 
      //
      //      gl.glCallList(displayList);

      gl.glPopMatrix();
      
      renderer.checkAndPrintGLError();
   }

   public void drawPoints (GLRenderer renderer,
      RenderProps props, Iterator<? extends RenderablePoint> iterator) {

      GL2 gl = renderer.getGL2().getGL2();
      gl.glPushMatrix();

      switch (props.getPointStyle()) {
         case POINT: {
            renderer.setLightingEnabled (false);
            // draw regular points first
            renderer.setPointSize (props.getPointSize());
            if (renderer.isSelecting()) {
               // don't worry about color in selection mode
               int i = 0;
               while (iterator.hasNext()) {
                  RenderablePoint pnt = iterator.next();
                  if (pnt.getRenderProps() == null) {
                     if (renderer.isSelectable (pnt)) {
                        renderer.beginSelectionQuery (i);
                        gl.glBegin (GL2.GL_POINTS);
                        gl.glVertex3fv (pnt.getRenderCoords(), 0);
                        gl.glEnd();
                        renderer.endSelectionQuery ();
                     }
                  }
                  i++;
               }
            }
            else {
               gl.glBegin (GL2.GL_POINTS);
               renderer.setColor (props.getPointColorArray(), false);
               while (iterator.hasNext()) {
                  RenderablePoint pnt = iterator.next();
                  if (pnt.getRenderProps() == null) {
                     renderer.updateColor (props.getPointColorArray(), pnt.isSelected());
                     gl.glVertex3fv (pnt.getRenderCoords(), 0);
                  }
               }
               gl.glEnd();
            }
            renderer.setPointSize (1);
            renderer.setLightingEnabled (true);
            break;
         }
         case SPHERE: {
            renderer.setMaterialAndShading (props, props.getPointMaterial(), false);
            int i = 0;
            while (iterator.hasNext()) {
               RenderablePoint pnt = iterator.next();
               if (pnt.getRenderProps() == null) {
                  if (renderer.isSelecting()) {
                     if (renderer.isSelectable (pnt)) {
                        renderer.beginSelectionQuery (i);
                        renderer.drawSphere (props, pnt.getRenderCoords());
                        renderer.endSelectionQuery ();      
                     }
                  }
                  else {
                     renderer.updateMaterial (
                        props, props.getPointMaterial(), pnt.isSelected());
                     renderer.drawSphere (props, pnt.getRenderCoords());
                  }
               }
               i++;
            }
            renderer.restoreShading (props);
         }
      }

      gl.glPopMatrix();
   }

   /**
    * {@inheritDoc}
    */
   public boolean isSelectable() {
      return true;
   }

   public int numSelectionQueriesNeeded() {
      return size();
   }

   public void getSelection (LinkedList<Object> list, int qid) {
      if (qid >= 0 && qid < size()) {
         list.addLast (get (qid));
      }
   }

   public void transformGeometry (AffineTransform3dBase X) {
      transformGeometry (X, this, 0);
   }

   public void transformGeometry (
      AffineTransform3dBase X, TransformableGeometry topObject, int flags) {
      for (int i = 0; i < size(); i++) {
         get (i).transformGeometry (X, topObject, flags);
      }
   }

   public void scaleDistance (double s) {
      for (int i = 0; i < size(); i++) {
         get (i).scaleDistance (s);
      }
      if (myRenderProps != null) {
         RenderableUtils.cloneRenderProps (this);
         myRenderProps.scaleDistance (s);
      }
   }

   public void scaleMass (double s) {
      // nothing
   }

   @Override
   public void updateBounds(Point3d pmin, Point3d pmax) {
      for (VertexComponent c : this) {
         c.updateBounds(pmin, pmax);
      }
   }

}
