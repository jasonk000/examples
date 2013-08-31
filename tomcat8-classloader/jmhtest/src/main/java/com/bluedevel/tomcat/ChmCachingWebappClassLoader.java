/**
 * Free for use at your own risk.
 * @author jasonk@bluedevel.com
 */
package com.bluedevel.tomcat;

import org.apache.catalina.LifecycleException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * Caching extension to Tomcat's WebappClassLoader
 * <p>
 * Can be used to cache lookups and avoid the locking present in the default Tomcat loader
 * <p>
 * To use, set up custom classloader {@code META-INF/context.xml} with the following:
 * <pre>
 * {@code
 * <?xml version="1.0" encoding="UTF-8" ?>
 * <Context>
 *   <Loader loaderClass="com.bluedevel.tomcat.ChmCachingWebappClassLoader"/>
 * </Context>
 * }
 * </pre>
 *
 * @author jasonk@bluedevel.com
 */
public class ChmCachingWebappClassLoader extends org.apache.catalina.loader.WebappClassLoader {

    private final ConcurrentHashMap<String, Class> cache = new ConcurrentHashMap<String, Class>();

    /**
     * Default constructor
     */
	public ChmCachingWebappClassLoader() {	
	    super();
	}

    /**
     * Constructor with parent reference
     * 
     * @param parent the parent class loader
     */
	public ChmCachingWebappClassLoader(ClassLoader parent) { 
		super(parent);
	}

    /**
     * Stops the classloader, clearing the cache
     * 
     * @exception LifecycleException if one is thrown from the WebappClassLoader
     */
    public void stop() throws LifecycleException {
    	cache.clear();
    	super.stop();

        // clear it again, just in case something happened between the 
        // invalidate and the stop
        cache.clear();
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
        // check in teh superclass if not in cache
        Class clazz = cache.get(name);
        if (clazz == null) {
            // always assume resolve is false at this point - we'll resolve later
            clazz = ChmCachingWebappClassLoader.super.loadClass(name, false);
            Class clazz2 = cache.putIfAbsent(name, clazz);
            // check if someone else beat us to updating the cache entry,
            if (clazz2 != null) {
                // if so then we should use the cached entry to be consistent
                clazz = clazz2;
            }
        }
        if (resolve) resolveClass(clazz);
        return clazz;

    }

}