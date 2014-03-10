package bluedevel;

import sun.misc.Unsafe;
import org.openjdk.jol.util.VMSupport;
import java.lang.reflect.Field;
import java.util.WeakHashMap;
import java.util.Map;

/**
* This app attempts to highlight the race condition when using addressOf to reference on-heap objects.
*
* Basically, the process is: allocate an array with a sequence of predictable values, fetch the
* address of that array, and then loop continuously, checking whether the memory contents still match.
* In parallel, generate a lot of garbage to trigger a GC.
*
* The expected outcome is that the array contents will be shuffled and the data at the original array
* address will be overwritten. This should be detected by the continuous check, and if an incorrect
* value is detected, flag it and exit the app.
*
* YMMV, especially regarding UseCompressedOops; jol toNativeAddress not quite working yet.
* In particular, JOL is only good for zero-based compressed oops.
*/
public class App {

    /** termination flag */
    private static volatile boolean finished = false;

    /** array size -- keep it small; too large and it gets placed straight into old */
    private static final int ARRAY_SIZE = 1*1024;

    public static void main(String[] args) throws InterruptedException {

	if(VMSupport.USE_COMPRESSED_REFS) {
	    System.out.println("Compressed refs in use, this will probably fail");
	    System.out.println("Try running with -XX:-UseCompressedOops");
	}

        // set up an array (do it before we start generating garbage)
        // so we have a fixed location on the heap before it has a chance to move
        // somewhere eg (old) where it might stay for a long time
        System.out.println("initialising array");
        long arrayAddress = initialiseArray(ARRAY_SIZE);

        // set up another thread to generate garbage
        final Thread generator = new Thread(new GarbageGenerator());
        generator.start();

        // keep checking forever, eventually we expect to fail this
        System.out.println("checking array:");
        while(isArrayValid(arrayAddress, ARRAY_SIZE)) {
	    // let the garbage generator make plenty of progress
            Thread.sleep(100);
        }

        // all done, do some cleanup
        finished = true;
        generator.join();

    }

    /** Create an on-heap array and return the address */
    private static long initialiseArray(int length) {
        final int[] values = new int[length];
        for(int i = 0; i < length; i++) {
            values[i] = i;
        }
        return toAddress(values);
    }

    /** Collect the on heap address of an object */
    private static long toAddress(Object o) {
        // thanks shipilev!
        final long address = VMSupport.addressOf(o);
        System.out.println("address: " + address);
        final long nativeA = VMSupport.toNativeAddress(address);
        System.out.println("native address: " + nativeA);
        return nativeA;
    }

    /** Check if the array is valid */
    private static boolean isArrayValid(long baseAddress, long length) {
        for(int i = 0; i < length; i++) {
            int valueDereferenced = getIntFromArray(baseAddress, i);
            if (i != valueDereferenced) {
                System.out.println("oops! expected: " + i + "; but found: " + valueDereferenced);
                return false;
            }
        }
        System.out.println("seems ok");
        return true;
    }

    /** Fetch a single int from an array address */
    private static int getIntFromArray(long baseAddress, long index) {
        final long SIZE_OF_INT = UNSAFE.arrayIndexScale(int[].class);
        final long INT_ARRAY_OFFSET = UNSAFE.arrayBaseOffset(int[].class);

        return UNSAFE.getInt(baseAddress + INT_ARRAY_OFFSET + (SIZE_OF_INT * index));
    }

    /** here be dragons */
    private static final Unsafe UNSAFE;
    static
    {
        try
        {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            UNSAFE = (Unsafe)field.get(null);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /** Generate an endless stream of garbage to be cleaned up */
    public static class GarbageGenerator implements Runnable {

        // wait a bit before starting, to let a few is-valid loops
        // complete so that the effect is nicely visible
        private final long SLEEP_ON_START_MILLIS = 3000;

        public void run() {
            final Map<Object, Object> map = new WeakHashMap();
            try {
                Thread.sleep(SLEEP_ON_START_MILLIS);
                System.out.println("making garbage");
                int i = 0;
                while (!finished) {
                    map.put(new Object(), new Object());
                    i++;
                }
                // just some random printlns to avoid any hoisting / dce'ing
                System.out.println("inserted to map -> " + i + "; map size ended at -> " + map.size());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                System.out.println("garbage generator finished");
            }
        }

    }

}

