/**
 * Copyright (c) 2014, by the Authors: John E Lloyd (UBC)
 *
 * This software is freely available under a 2-clause BSD license. Please see
 * the LICENSE file in the ArtiSynth distribution directory for details.
 */
package maspack.render;

import maspack.matrix.Point3d;

public abstract class GLRenderableBase implements GLRenderable {

   /**
    * {@inheritDoc}
    */
   public void prerender (RenderList list) {
   }

   /**
    * {@inheritDoc}
    */
   public abstract void  render (GLRenderer renderer, int flags);

   /**
    * {@inheritDoc}
    */
   public void updateBounds (Point3d pmin, Point3d pmax) {
   }

   /**
    * {@inheritDoc}
    */
   public int getRenderHints() {
      return 0;
   }
}
