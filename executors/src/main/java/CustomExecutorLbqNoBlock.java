import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.*;
import java.util.concurrent.*;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import java.util.Queue;

public class CustomExecutorLbqNoBlock {
 
    static double calculatePiFor(int slice, int nrOfIterations) {
        double acc = 0.0;
        for (int i = slice * nrOfIterations; i <= ((slice + 1) * nrOfIterations - 1); i++) {
            acc += 4.0 * (1 - (i % 2) * 2) / (2 * i + 1);
        }
        return acc;
    }
 
    private static long piTest(final int numThreads) throws InterruptedException {
 
        final int numMessages = 1000000;
        final int step = 100;

        final ExecutorService test = new LtqExecutor(numThreads);
        final AtomicInteger latch = new AtomicInteger(numMessages);
        final AtomicReference<Double> result = new AtomicReference<>(0.0);
        final AtomicLong timSum = new AtomicLong(0);
 
        final long tim = System.currentTimeMillis();
        for ( int i= 0; i< numMessages; i++) {
            final int finalI = i;
            /*while ( ((ThreadPoolExecutor)test).getQueue().size() > 40000 ) {
                LockSupport.parkNanos(100);
		} */
            test.execute(new Runnable() {
                public void run() {
                    double res = calculatePiFor(finalI, step);
                    Double expect;
                    boolean success;
                    do {
                        expect = result.get();
                        success = result.compareAndSet(expect,expect+res);
                    } while( !success );
                    int lc = latch.decrementAndGet();
                    if (lc == 0 ) {
                        long l = System.currentTimeMillis() - tim;
                        timSum.set(timSum.get()+l);
                        System.out.println("pi: " + result.get() + " t:" + l + " finI " + finalI);
                        test.shutdown();
                    }
                }
            });
        }
        while (latch.get() > 0 ) {
            LockSupport.parkNanos(1000*500); // don't care as 0,5 ms are not significant per run
        }
        return timSum.get();
    }
 
    public static void main( String arg[] ) throws Exception {
        final int MAX_ACT = 8;
        String results[] = new String[MAX_ACT];
 
        for ( int numActors = 1; numActors <= MAX_ACT; numActors++ ) {
            long sum = 0;
            for ( int ii=0; ii < 30; ii++) {
                long res = piTest(numActors);
                if ( ii >= 20 ) {
                    sum+=res;
                }
            }
            results[numActors-1] = "average "+numActors+" threads : "+sum/10;
        }
 
        for (int i = 0; i < results.length; i++) {
            String result = results[i];
            System.out.println(result);
        }
          
    }

    private static class LtqExecutor<T> implements ExecutorService {

        private volatile boolean running;
        private volatile boolean stopped;
        private final int threadCount;
        private final Queue<Runnable> queue;
    	private final Thread[] threads;

        public LtqExecutor(int threadCount) {
            this.threadCount = threadCount;
	    // this.queue = new LinkedTransferQueue();
            // this.queue = new ArrayBlockingQueue(2^18);
            // this.queue = new ConcurrentLinkedQueue();
            this.queue = new LinkedBlockingQueue();
            running = true;
            stopped = false;
            threads = new Thread[threadCount];
            for(int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(new Worker());
		threads[i].start();
            }
        }

        public void execute(Runnable runnable) {
            while (!queue.offer(runnable)) {
                LockSupport.parkNanos(1);
                /* try {
                    Thread.sleep(1);
                } catch (InterruptedException ie) {
                    throw new RuntimeException(ie);
                } */
            }
            /* try {
                queue.put(runnable);
	    } catch (InterruptedException ie) {
		throw new RuntimeException(ie);
	    } */
	}

        private class Worker implements Runnable {
            public void run() {
                while(running) {
                    Runnable runnable = null;
                    while (true) {
                        if (Thread.interrupted()) return;
                        runnable = queue.poll();
                        if (runnable != null) break;
                        LockSupport.parkNanos(1);
                    }
                    /* try {
                        runnable = queue.take();
                    } catch (InterruptedException ie) {
                        // was interrupted - just go round the loop again,
                        // if running = false will cause it to exit
                    } */
                    try {
                        if (runnable != null) { 
			    runnable.run();
			}
                    } catch (Exception e) {
                        System.out.println("failed because of: " + e.toString());
                        e.printStackTrace();
                    }
                }
            }
        }
        public void shutdown() {
            running = false;
            for(int i = 0; i < threadCount; i++) {
                threads[i].interrupt();
                threads[i] = null;
            }
            stopped = true;
        }
        public boolean isShutdown() {
            return running;
        }
        public boolean isTerminated() {
            return stopped;
        }

        // ***************************************************

        public boolean awaitTermination(long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException("oops!");
        }
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
            throw new UnsupportedOperationException("oops!");
        }
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException("oops!");
        }
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks) {
            throw new UnsupportedOperationException("oops!");
        }
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException("oops!");
        }
        public List<Runnable> shutdownNow() {
            throw new UnsupportedOperationException("oops!");
        }
        public <T> Future<T> submit(Callable<T> task) {
            throw new UnsupportedOperationException("oops!");
        }
        public Future<?> submit(Runnable task) {
            throw new UnsupportedOperationException("oops!");
        }
        public <T> Future<T> submit(Runnable task, T result) {
            throw new UnsupportedOperationException("oops!");
        }
        public String toString() {
            throw new UnsupportedOperationException("oops!");
        }
    }
}
