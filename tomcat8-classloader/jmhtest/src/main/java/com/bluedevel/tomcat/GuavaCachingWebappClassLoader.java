/**
 * Free for use at your own risk.
 * @author jasonk@bluedevel.com
 */
package com.bluedevel.tomcat;

import org.apache.catalina.LifecycleException;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * Caching extension to Tomcat's WebappClassLoader
 * <p>
 * Can be used to cache lookups and avoid the locking present in the default Tomcat loader
 * <p>
 * TODO - use KittyCache as it is much lighter, Guava is a 3mb JAR
 * To use, set up custom classloader {@code META-INF/context.xml} with the following:
 * <pre>
 * {@code
 * <?xml version="1.0" encoding="UTF-8" ?>
 * <Context>
 *   <Loader loaderClass="com.bluedevel.tomcat.CachingWebappClassLoader"/>
 * </Context>
 * }
 * </pre>
 *
 * @author jasonk@bluedevel.com
 */
public class GuavaCachingWebappClassLoader extends org.apache.catalina.loader.WebappClassLoader {

    /** default cache size */
    private static final int DEFAULT_CACHE_SIZE = 1024;

    /** cache instance; defer to parent to load classes */
    private final LoadingCache<String, Class> cache = CacheBuilder.newBuilder().maximumSize(DEFAULT_CACHE_SIZE).build(
         new CacheLoader<String, Class>() {
                @Override
                public Class load(final String name) throws ClassNotFoundException {
                    System.out.println("loading from super");
                    // always do NOT resolve in here
                    // expect loadClass call to do it
                    return GuavaCachingWebappClassLoader.super.loadClass(name, false);
                }
            });

    /**
     * Default constructor
     */
	public GuavaCachingWebappClassLoader() {	
	    super();
	}

    /**
     * Constructor with parent reference
     * 
     * @param parent the parent class loader
     */
	public GuavaCachingWebappClassLoader(ClassLoader parent) { 
		super(parent);
	}

    /**
     * Stops the classloader, clearing the cache
     * 
     * @exception LifecycleException if one is thrown from the WebappClassLoader
     */
    public void stop() throws LifecycleException {
    	cache.invalidateAll();
    	super.stop();

        // clear it again, just in case something happened between the 
        // invalidate and the stop
        cache.invalidateAll();
        cache.cleanUp();
    }

    /**
     * Load the class with the specified name, searching the cache for entries first.
     * <p>
     * If the value is not present in the cache, check in the superclass loader (WebappClassLoader)
     *
     * @param name Name of the class to be loaded
     * @param resolve resolve the class
     *
     * @exception ClassNotFoundException if the class was not found or there was an issue with the cache
     */
    @Override
    public Class loadClass(final String name, final boolean resolve) throws ClassNotFoundException
    {
        try {
            Class clazz = cache.get(name);
            if (resolve) resolveClass(clazz);
            return clazz;
        } catch (ExecutionException e) {
            // painful forced catch
            System.err.println("exception!");
            throw new ClassNotFoundException("could not load from cache", e);
        }

    }

}