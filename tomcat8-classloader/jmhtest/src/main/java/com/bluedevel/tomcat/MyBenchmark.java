package com.bluedevel.tomcat;

import org.openjdk.jmh.annotations.GenerateMicroBenchmark;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.logic.BlackHole;

// scope benchmark = shared by all threads
// scope thread = one per thread

// TODO - add tests that exercise "this" classloader.
// at present all tests fall through to system class loader (java.util.x)

@State(Scope.Benchmark)
public class MyBenchmark {

	@State(Scope.Benchmark)
	public static class Loaders {
		private org.apache.catalina.loader.WebappClassLoader tomcat;
		private com.bluedevel.tomcat.ChmWebappClassLoader chmwacl;
		private com.bluedevel.tomcat.GuavaCachingWebappClassLoader guavacaching;
		private com.bluedevel.tomcat.ChmCachingWebappClassLoader chmcaching;
		public Loaders() {
			tomcat = new org.apache.catalina.loader.WebappClassLoader();
			chmwacl = new com.bluedevel.tomcat.ChmWebappClassLoader();
			guavacaching = new com.bluedevel.tomcat.GuavaCachingWebappClassLoader();
			chmcaching = new com.bluedevel.tomcat.ChmCachingWebappClassLoader();
			try {
				tomcat.start();
				chmwacl.start();
				guavacaching.start();
				chmcaching.start();
			} catch (org.apache.catalina.LifecycleException e) {
				System.out.println("fail.");
				throw new RuntimeException(e);
			}
		}
	}

    @GenerateMicroBenchmark
    public void testWaCLLoader(BlackHole bh1, BlackHole bh2, Loaders l) throws ClassNotFoundException {
    	ClassLoader cl = l.tomcat;
        bh1.consume(cl.loadClass("java.util.ArrayList"));
        bh2.consume(cl.loadClass("java.util.ArrayDeque"));
    }

    @GenerateMicroBenchmark
    public void testChmWaCL(BlackHole bh1, BlackHole bh2, Loaders l) throws ClassNotFoundException {
    	ClassLoader cl = l.chmwacl;
        bh1.consume(cl.loadClass("java.util.ArrayList"));
        bh2.consume(cl.loadClass("java.util.ArrayDeque"));
    }

    @GenerateMicroBenchmark
    public void testGuavaCachingLoader(BlackHole bh1, BlackHole bh2, Loaders l) throws ClassNotFoundException {
    	ClassLoader cl = l.guavacaching;
        bh1.consume(cl.loadClass("java.util.ArrayList"));
        bh2.consume(cl.loadClass("java.util.ArrayDeque"));
    }

    @GenerateMicroBenchmark
    public void testChmCachingLoader(BlackHole bh1, BlackHole bh2, Loaders l) throws ClassNotFoundException {
    	ClassLoader cl = l.chmcaching;
        bh1.consume(cl.loadClass("java.util.ArrayList"));
        bh2.consume(cl.loadClass("java.util.ArrayDeque"));
    }
}
