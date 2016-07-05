package maspack.render.GL;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;

import javax.media.nativewindow.WindowClosingProtocol.WindowClosingMode;
import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GL2GL3;
import javax.media.opengl.GL3;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLDrawableFactory;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.GLProfile;
import javax.media.opengl.awt.GLCanvas;
import javax.media.opengl.awt.GLJPanel;

import maspack.render.TextureContent;
import maspack.render.TextureContent.ContentFormat;
import maspack.render.GL.GLSupport.GLVersionInfo;
import maspack.render.GL.GLSupport.GLVersionListener;
import maspack.util.BufferUtilities;
import maspack.util.Rectangle;

/**
 * Container class for resources tied to a particular context.  There should
 * one set of resources per context.
 * @author antonio
 *
 */
public abstract class GLSharedResources implements GLEventListener, GLGarbageSource {
   
   private static long DEFAULT_GARBAGE_INTERVAL = 20000; // 20 seconds
   private static boolean DEFAULT_GARBAGE_TIMER_ENABLED = true;
   
   GLCapabilities glCapabilities;
   GLAutoDrawable masterDrawable;
   HashSet<GLViewer> viewers;
   
   GLResourceList<GLResource> resources;
   HashMap<TextureContent, GLTexture> textureMap;
   
   GLTextureLoader textureLoader;
   long garbageCollectionInterval;
   boolean garbageTimerEnabled;
   MasterRedrawThread masterRedrawThread;
   GLGarbageCollector garbageman;
   GLGarbageBin<GLResource> garbagebin;
   
   
   private static class MasterRedrawThread extends Thread {
      
      GLAutoDrawable master;
      private long redrawInterval;
      private volatile boolean terminate;
      
      public MasterRedrawThread(GLAutoDrawable master, long redrawInterval) {
         this.master = master;
         this.redrawInterval = redrawInterval;
         terminate = false;
      }
      
      public void setRedrawInterval(long ms) {
         redrawInterval = ms;
      }
      
      @Override
      public void run () {
         while (!terminate) {
            try {
               master.display ();
            } catch(Exception e) {
            	e.printStackTrace();
            }
            try {
               Thread.sleep (redrawInterval);
            } catch (InterruptedException e) {
            }
            
         }
      }
      
      public void terminate() {
         terminate = true;
      }
      
   }
   
   public GLSharedResources(GLCapabilities cap) {
      this.glCapabilities = cap;
      viewers = new HashSet<>();
      masterDrawable = null;
      masterRedrawThread = null;
      garbageCollectionInterval = DEFAULT_GARBAGE_INTERVAL;
      garbageTimerEnabled = DEFAULT_GARBAGE_TIMER_ENABLED;
      textureLoader = new GLTextureLoader();
      
      textureMap = new HashMap<> ();
      
      garbageman = new GLGarbageCollector ();
      garbagebin = new GLGarbageBin<> ();
      resources = new GLResourceList<> ();
      garbageman.addSource (garbagebin);
      garbageman.addSource (resources);
      garbageman.addSource (textureLoader);
      garbageman.addSource (this);  // for textures
   }
   
   protected void addGarbageSource(GLGarbageSource source) {
      garbageman.addSource (source);
   }
   
   /**
    * Returns the capabilities of the underlying GL context
    * @return the GL capabilities
    */
   public GLCapabilities getGLCapabilities() {
      return glCapabilities;
   }
   
   protected GLAutoDrawable getMasterDrawable() {
      return masterDrawable;
   }
   
   @Override
   public void garbage (GL gl) {
      
      // check for any expired textures
      synchronized(textureMap) {
         Iterator<Entry<TextureContent,GLTexture>> it = textureMap.entrySet ().iterator ();
         while (it.hasNext ()) {
            Entry<TextureContent,GLTexture> entry = it.next ();
            TextureContent key = entry.getKey ();
            if (key.getReferenceCount () == 0) {
               GLTexture tex = entry.getValue ();
               tex.releaseDispose (gl);
               it.remove ();
            }
         }
      }
   }
   
   private void maybeCreateMaster() {
      if (masterDrawable == null) {
         // create the master drawable
         final GLProfile glp = glCapabilities.getGLProfile();
         masterDrawable = GLDrawableFactory.getFactory(glp).createDummyAutoDrawable(null, true, glCapabilities, null);
         
         GLVersionListener glv = new GLVersionListener ();
         masterDrawable.addGLEventListener (this);
         masterDrawable.addGLEventListener (garbageman);
         masterDrawable.addGLEventListener (glv);
         
         masterDrawable.display(); // triggers GLContext object creation and native realization.
         
         while(!glv.isValid ()) {
         }
         GLVersionInfo version = glv.getVersionInfo ();
         System.out.println (version.getVersionString ());
         
         masterDrawable.removeGLEventListener (glv);
         
         if (garbageTimerEnabled) {
            masterRedrawThread = new MasterRedrawThread (masterDrawable, garbageCollectionInterval);
            masterRedrawThread.setName ("GL Garbage Collector - " + masterDrawable.getHandle ());
            masterRedrawThread.start ();
         }
      }
   }
   
   /**
    * Creates a canvas with the same capabilities and shared context
    * as other viewers using this set of resources.  This ensures
    * that the sharing of resources is properly initialized.
    * @return the created canvas
    */
   public synchronized GLCanvas createCanvas() {
      
      maybeCreateMaster();
      
      GLCanvas canvas = new GLCanvas (glCapabilities);
      canvas.setSharedAutoDrawable (masterDrawable);
      canvas.setDefaultCloseOperation (WindowClosingMode.DISPOSE_ON_CLOSE);
      return canvas;
   }
   
   /**
    * Creates a canvas with the same capabilities and shared context
    * as other viewers using this set of resources.  This ensures
    * that the sharing of resources is properly initialized.
    * MUST BE CALLED IN THE GLViewer's CONSTRUCTOR!!
    * @return the created canvas
    */
   public synchronized GLJPanel createPanel() {
      maybeCreateMaster();
      GLJPanel panel = new GLJPanel (glCapabilities);
      panel.setSharedAutoDrawable (masterDrawable);
      panel.setDefaultCloseOperation (WindowClosingMode.DISPOSE_ON_CLOSE);
      return panel;
   }
   
   /**
    * Register a particular viewer with this set of resources.
    * MUST BE CALLED IN THE GLViewer's CONSTRUCTOR!!
    * @param viewer the viewer with which to share resources.
    */
   public synchronized void registerViewer(GLViewer viewer) {
      viewers.add (viewer);
   }
   
   /**
    * Unregisters a particular viewer with this set of resources.
    * MUST BE CALLED IN THE GLViewer's dispose() METHOD.
    * @param viewer
    */
   public synchronized void deregisterViewer(GLViewer viewer) {
      viewers.remove (viewer);
      if (viewers.size () == 0) {
         if (masterDrawable != null) {
            masterRedrawThread.terminate ();
            masterRedrawThread = null;
            masterDrawable.destroy ();
            masterDrawable = null;
         }
      }
   }
   
   /**
    * Clears all resources with the associated with the master
    * @param gl
    */
   public void dispose(GL gl) {
      for (GLTexture tex : textureMap.values ()) {
         tex.releaseDispose (gl);
      }
      textureMap.clear (); // other sources should be cleared by the garbage man separately
   }
   
   /**
    * Potentially release invalid texture associated with this key
    * @param key for grabbing texture
    * @param tex stored texture object
    * @return true if texture is invalid and released
    */
   private boolean maybeReleaseTexture(TextureContent key, GLTexture tex) {
      if (!tex.isValid ()) {
         // release
         tex.release ();
         synchronized(textureMap) {
            textureMap.remove (key);
         }
         return true;
      }
      
      return false;
   }
   
   private boolean maybeUpdateTexture(GL gl, GLTexture texture, TextureContent content) {
      boolean update = false;
      Rectangle dirty = content.getDirty ();
      if (dirty != null) {
         update = true;
         
         int psize = content.getPixelSize ();
         int width = dirty.width ();
         int height = dirty.height ();
         
         ByteBuffer buff = BufferUtilities.newNativeByteBuffer (width*height*psize);
         content.getData (dirty, buff);
         buff.flip ();
         
         ContentFormat format = content.getFormat ();
         int glFormat = 0;
         int glType = 0;
         switch(format) {
            case GRAYSCALE_ALPHA_BYTE_2:
               if (gl.isGL3()) {
                  glFormat = GL3.GL_RG;
               } else if (gl.isGL2()) {
                  glFormat = GL2.GL_LUMINANCE_ALPHA;
               }
               glType = GL.GL_UNSIGNED_BYTE;
               break;
            case GRAYSCALE_ALPHA_SHORT_2: 
               if (gl.isGL3()) {
                  glFormat = GL3.GL_RG;
               } else if (gl.isGL2()) {
                  glFormat = GL2.GL_LUMINANCE_ALPHA;
               }
               glType = GL.GL_UNSIGNED_SHORT;
               break;
            case GRAYSCALE_BYTE:
               if (gl.isGL3()) {
                  glFormat = GL3.GL_RED;
               } else if (gl.isGL2()) {
                  glFormat = GL2.GL_LUMINANCE;
               }
               glType = GL.GL_UNSIGNED_BYTE;
               break;
            case GRAYSCALE_SHORT:
               if (gl.isGL3()) {
                  glFormat = GL3.GL_RED;
               } else if (gl.isGL2()) {
                  glFormat = GL2.GL_LUMINANCE;
               }
               glType = GL.GL_UNSIGNED_SHORT;
               break;
            case RGBA_BYTE_4:
               glFormat = GL.GL_RGBA;
               glType = GL.GL_UNSIGNED_BYTE;
               break;
            case RGBA_INTEGER:
               glFormat = GL2GL3.GL_RGBA_INTEGER;
               glType = GL2GL3.GL_UNSIGNED_INT_8_8_8_8;
               break;
            case RGB_BYTE_3:
               glFormat = GL.GL_RGB;
               glType = GL.GL_UNSIGNED_BYTE;
               break;
            default:
               break;
         }
         
         texture.fill (gl, dirty.x (), dirty.y (), width, height, psize, glFormat, glType, buff);
         content.markClean ();
         buff = BufferUtilities.freeDirectBuffer (buff);
      }
      return update;
   }
   
   public GLTexture getTexture(GL gl, TextureContent content) {
      
      GLTexture tex = null;
      // check if texture exists in map
      synchronized(textureMap) {
         tex = textureMap.get (content);
         if (tex == null || maybeReleaseTexture(content, tex)) {
            return null; 
         }
      }
      
      maybeUpdateTexture (gl, tex, content);
      return tex;
   }
   
   public GLTexture getOrLoadTexture(GL gl, TextureContent content) {
      
      GLTexture tex = null;
      synchronized(textureMap) {
         tex = getTexture(gl, content);
         if (tex != null && !tex.disposeInvalid (gl)) {
            maybeUpdateTexture (gl, tex, content);
            return tex;
         }
         
         // load texture
         synchronized (textureLoader) {
            tex = textureLoader.getAcquiredTexture (gl, content);
            textureMap.put (content, tex);
         }
         
      }
      return tex;
   }
   
   @Override
   public void init (GLAutoDrawable drawable) {
      System.out.println("Master drawable initialized");
   }

   @Override
   public void dispose (GLAutoDrawable drawable) {
      System.out.println("Master drawable disposed");
      textureLoader.clearAllTextures (); // clean up
   }
   
   public void track(GLResource res) {
      synchronized (resources) {
         resources.track (res);
      }
   }
   
   public void disposeResource(GLResource res) {
      garbagebin.trash (res);
   }
   
   
   public void runGarbageCollection(GL gl) {
      garbageman.collect(gl);
   }
   
   /**
    * Runs garbage collection if sufficient time has
    * passed since the last collection, as specified
    * by the garbage collection interval
    * @param gl
    * @return true if garbage collection run
    */
   public boolean maybeRunGarbageCollection(GL gl) {
      return garbageman.maybeCollect (gl, garbageCollectionInterval);
   }
   
   /**
    * Enables or disables an automatic garbage timer.  This
    * timer runs on a separate thread.
    * @param set
    */
   public void setGarbageTimerEnabled(boolean set) {
      if (set != garbageTimerEnabled) {
         
         if (set) {
            masterRedrawThread = new MasterRedrawThread (masterDrawable, garbageCollectionInterval);
            masterRedrawThread.setName ("GL Garbage Collector - " + masterDrawable.getHandle ());
            masterRedrawThread.start ();
         } else {
            if (masterRedrawThread != null) {
               masterRedrawThread.terminate ();
               masterRedrawThread = null;
            }
         }
         
         garbageTimerEnabled = set;
      }
   }
   
   public boolean isGarbageTimerEnabled() {
      return garbageTimerEnabled;
   }
   
   /**
    * Time interval for running garbage collection, either with a
    * separate timed thread, or by manual calls to {@link #maybeRunGarbageCollection(GL)}.
    * @param ms time interval for collection in ms
    */
   public void setGarbageCollectionInterval(long ms) {
      garbageCollectionInterval = ms;
      if (masterRedrawThread != null) {
         masterRedrawThread.setRedrawInterval (ms);
      }
   }
   
   public long getGarbageCollectionInterval() {
      return garbageCollectionInterval;
   }

   @Override
   public void display (GLAutoDrawable drawable) {
      //System.out.println("Master drawable displayed");
   }

   @Override
   public void reshape (
      GLAutoDrawable drawable, int x, int y, int width, int height) {
      //System.out.println("Master drawable reshaped");
   }
   
}
