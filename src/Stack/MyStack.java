import java.lang.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public class MyStack<T> {
    private final Object lock;
    private final List<LinkedList<T>> stacks;
    private static final int STRIPES = 32;

    public MyStack(){
        lock = new Object();
        stacks = new ArrayList<LinkedList<T>>();
        for(int i = 0; i < STRIPES; i++)
            stacks.add(new LinkedList<T>());
    }

    public void push(T obj) {
        int stripe = Thread.currentThread().hashCode()%STRIPES;
        LinkedList<T> stack = stacks.get(stripe);
        synchronized(stack){
            stack.push(obj);
        }
    }

    public T pop() {
        int stripe = Thread.currentThread().hashCode()%STRIPES;
        for (int i = stripe; i < stripe+STRIPES; i++){
            LinkedList<T> stack = stacks.get(i%STRIPES);
            synchronized(stack){
                if(stack.size() == 0) continue;
                return i == stripe ? stack.pop() : stack.removeLast();
            }
        }
        return null;
    }

    public static void concurrentTest(final int size, final int threads,  
            MyStack<Integer> stack) throws Exception {
        final CyclicBarrier startBarrier = new CyclicBarrier(threads+1), 
              stopBarrier = startBarrier;

        final int range = size/threads;
        for (int i = 0; i < threads; ++i) {
            final int nr = i;
            Thread ti = new Thread(new Runnable() { public void run() {
                try { startBarrier.await(); } catch (Exception exn) { }
                    for(int j = range*nr; j<range*nr+range; j++)
                        stack.push(j);
                try { stopBarrier.await(); } catch (Exception exn) { }
            }});
            ti.start();
        }
        startBarrier.await();
        stopBarrier.await();
        startBarrier.reset();
        stopBarrier.reset();

        final Set<Integer> pops = ConcurrentHashMap.newKeySet();
        for (int i = 0; i < threads; ++i) {
            final int nr = i;
            Thread ti = new Thread(new Runnable() { public void run() {
                try { startBarrier.await(); } catch (Exception exn) { }
                    for(int j = range*nr; j<range*nr+range; j++)
                        pops.add(stack.pop());
                try { stopBarrier.await(); } catch (Exception exn) { }
            }});
            ti.start();
        }

        startBarrier.await();
        stopBarrier.await();

        if (pops.size() == size) System.out.println("Concurrency works :)");
        else  System.out.println("Concurrency doesn't work :(");
    }

    public static void testOrder(int n, MyStack<Integer> stack){
        final AtomicBoolean working = new AtomicBoolean(true);
        Thread A = new Thread(new Runnable() { public void run() {
            for(int i=0; i<n; i++) stack.push(i);
        }});
        Thread B = new Thread(new Runnable() { public void run() {
            for(int i=0; i<n; i++) 
                working.compareAndSet(true, n-1-i == stack.pop());
        }});
        try {
            A.start(); A.join();
            B.join(); B.join();
        } catch (Exception e) { 
            System.out.println("Order dies >:("); 
        }
        if(working.get()) System.out.println("Order works :)");
        else System.out.println("Order doesn't work :(");
    }

    public static void main(String[] args){
        int size = 10_000_000;
        int threads = 32;
        MyStack<Integer> stack = new MyStack<Integer>();
        testOrder(size, stack);
        try {
            concurrentTest(size, threads, stack);
        } catch (Exception e){
            System.out.println("Concurrent test died >:(");
        }
        System.exit(0);
    }
}
