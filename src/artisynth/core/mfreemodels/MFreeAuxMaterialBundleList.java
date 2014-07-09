/**
 * Copyright (c) 2014, by the Authors: Antonio Sanchez (UBC)
 *
 * This software is freely available under a 2-clause BSD license. Please see
 * the LICENSE file in the ArtiSynth distribution directory for details.
 */
package artisynth.core.mfreemodels;

import maspack.render.RenderProps;
import artisynth.core.modelbase.RenderableComponentList;
import artisynth.core.util.ScalableUnits;

public class MFreeAuxMaterialBundleList
   extends RenderableComponentList<MFreeAuxMaterialBundle> implements ScalableUnits {

   protected static final long serialVersionUID = 1;

   public MFreeAuxMaterialBundleList () {
      this (null, null);   
   }
   
   public MFreeAuxMaterialBundleList (String name, String shortName) {
      super (MFreeAuxMaterialBundle.class, name, shortName);
      setRenderProps (createRenderProps());
   }

   public boolean hasParameterizedType() {
      return false;
   }
   
   public RenderProps createRenderProps() {
      return RenderProps.createLineProps (this);
   }

   public void scaleDistance (double s) {
      for (int i = 0; i < size(); i++) {
         get (i).scaleDistance (s);
      }
      if (myRenderProps != null) {
         myRenderProps.scaleDistance (s);
      }
   }

   public void scaleMass (double s) {
      for (int i = 0; i < size(); i++) {
         get (i).scaleMass (s);
      }
   }

}
