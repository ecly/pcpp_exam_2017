import java.util.concurrent.atomic.*;
import java.util.concurrent.*;
import java.util.*;

public class MyUnionFind {
    public static void main(String[] args) throws Exception {
        final int itemCount = 10_000;
        {   // Example of a working execution
            UnionFindTest test = new UnionFindTest();
            test.sequential(new FineUnionFind(5));
            test.concurrent(itemCount, new FineUnionFind(itemCount));
            test.deadlock(itemCount, new FineUnionFind(itemCount));
        }
        {   // Question 4.3
            UnionFindTest test = new UnionFindTest();
            test.sequential(new BogusFineUnionFind(5));
            test.concurrent(itemCount, new BogusFineUnionFind(itemCount));
            test.deadlock(itemCount, new BogusFineUnionFind(itemCount));
        }
    }
}

interface UnionFind {
    int find(int x);
    void union(int x, int y);
    boolean sameSet(int x, int y);
}

// Test of union-find data structures, adapted from Florian Biermann's
// MSc thesis, ITU 2014
class UnionFindTest extends Tests {

    public void sequential(UnionFind uf) throws Exception {
        System.out.printf("Testing %s ... ", uf.getClass());
        // Find
        assertEquals(uf.find(0), 0);
        assertEquals(uf.find(1), 1);
        assertEquals(uf.find(2), 2);
        // Union
        uf.union(1, 2);
        assertEquals(uf.find(1), uf.find(2));

        uf.union(2, 3);
        assertEquals(uf.find(1), uf.find(2));
        assertEquals(uf.find(1), uf.find(3));
        assertEquals(uf.find(2), uf.find(3));

        uf.union(1, 4);
        assertEquals(uf.find(1), uf.find(2));
        assertEquals(uf.find(1), uf.find(3));
        assertEquals(uf.find(2), uf.find(3));
        assertEquals(uf.find(1), uf.find(4));
        assertEquals(uf.find(2), uf.find(4));
        assertEquals(uf.find(3), uf.find(4));
    }

    public void deadlock(final int size, final UnionFind uf) throws Exception {
        final int[] numbers = new int[size];
        for (int i = 0; i < numbers.length; ++i) numbers[i] = i;
        // Populate threads
        final int threadCount = 32;
        final CyclicBarrier startBarrier = new CyclicBarrier(threadCount+1), 
              stopBarrier = startBarrier;
        Collections.shuffle(Arrays.asList(numbers));
        for (int i = 0; i < threadCount; ++i) {
            final boolean reverse = i%2==0;
            Thread ti = new Thread(new Runnable() { public void run() {
                try { startBarrier.await(); } catch (Exception exn) { }
                    for (int j=0; j<100; j++)
                        for (int i = 0; i < numbers.length - 1; ++i) 
                            if (reverse) uf.union(numbers[i + 1], numbers[i]);
                            else uf.union(numbers[i], numbers[i + 1]);
                try { stopBarrier.await(); } catch (Exception exn) { }
            }});
            ti.start();
        }
        startBarrier.await();
        stopBarrier.await();
        final int root = uf.find(0);
        for (int i : numbers) 
            assertEquals(uf.find(i), root);
        System.out.println("No deadlocks");
    }

    public void concurrent(final int size, final UnionFind uf) throws Exception {
        final int[] numbers = new int[size];
        for (int i = 0; i < numbers.length; ++i) 
            numbers[i] = i;
        // Populate threads
        final int threadCount = 32;
        final CyclicBarrier startBarrier = new CyclicBarrier(threadCount+1), 
              stopBarrier = startBarrier;
        Collections.shuffle(Arrays.asList(numbers));
        for (int i = 0; i < threadCount; ++i) {
            Thread ti = new Thread(new Runnable() { public void run() {
                try { startBarrier.await(); } catch (Exception exn) { }
                for (int j=0; j<100; j++)
                    for (int i = 0; i < numbers.length - 1; ++i) 
                        uf.union(numbers[i], numbers[i + 1]);
                try { stopBarrier.await(); } catch (Exception exn) { }
            }});
            ti.start();
        }
        startBarrier.await();
        stopBarrier.await();
        final int root = uf.find(0);
        for (int i : numbers)
            assertEquals(uf.find(i), root);
        System.out.println("passed");
    }
}

class Tests {
    public static void assertEquals(int x, int y) throws Exception {
        if (x != y) 
            throw new Exception(String.format("ERROR: %d not equal to %d%n", x, y));
    }

    public static void assertTrue(boolean b) throws Exception {
        if (!b) 
            throw new Exception(String.format("ERROR: assertTrue"));
    }
}
// Fine-locking union-find.  Union and sameset lock on the intrinsic
// locks of the two root Nodes involved.  Find is wait-free, takes no
// locks, and performs no compression.  

// The nodes[] array entries are never updated after initialization
// inside the constructor, so no need to worry about their visibility.
// But the fields of Node objects are written (by union and compress
// while holding locks), and read by find without holding locks, so
// must be made volatile.
class FineUnionFind implements UnionFind {
    private final Node[] nodes;

    public FineUnionFind(int count) {
        this.nodes = new Node[count];
        for (int x=0; x<count; x++)
            nodes[x] = new Node(x);
    }

    public int find(int x) {
        while (nodes[x].next != x) 
            x = nodes[x].next;
        return x;
    }

    public void union(final int x, final int y) {
        while (true) {
            int rx = find(x), ry = find(y);
            if (rx == ry)
                return;
            else if (rx > ry) { 
                int tmp = rx; rx = ry; ry = tmp; 
            }
            // Now rx < ry; take locks in consistent order
            synchronized (nodes[rx]) { 
                synchronized (nodes[ry]) {
                    // Check rx, ry are still roots, else restart
                    if (nodes[rx].next != rx || nodes[ry].next != ry)
                        continue;
                    if (nodes[rx].rank > nodes[ry].rank) {
                        int tmp = rx; rx = ry; ry = tmp;
                    }
                    // Now nodes[rx].rank <= nodes[ry].rank
                    nodes[rx].next = ry;
                    if (nodes[rx].rank == nodes[ry].rank)
                        nodes[ry].rank++;
                    compress(x, ry);
                    compress(y, ry);
                } }  
        } 
    }

    // Assumes lock is held on nodes[root]
    private void compress(int x, final int root) {
        while (nodes[x].next != x) {
            int next = nodes[x].next;
            nodes[x].next = root;
            x = next;
        }
    }

    public boolean sameSet(int x, int y) {
        return find(x) == find(y);
    }

    class Node {
        private volatile int next, rank;

        public Node(int next) {
            this.next = next;
        }
    }
}

// Bogus Fine-locking union-find.  Union and sameset lock on the intrinsic
// locks of the two root Nodes involved.  Find is wait-free, takes no
// locks, and performs no compression.  

// The nodes[] array entries are never updated after initialization
// inside the constructor, so no need to worry about their visibility.
// But the fields of Node objects are written (by union and compress
// while holding locks), and read by find without holding locks, so
// must be made volatile.

class BogusFineUnionFind implements UnionFind {
    private final Node[] nodes;

    public BogusFineUnionFind(int count) {
        this.nodes = new Node[count];
        for (int x=0; x<count; x++)
            nodes[x] = new Node(x);
    }

    public int find(int x) {
        while (nodes[x].next != x) 
            x = nodes[x].next;
        return x;
    }

    public void union(final int x, final int y) {
        while (true) {
            int rx = find(x), ry = find(y);
            if (rx == ry)
                return;
            synchronized (nodes[rx]) { 
                synchronized (nodes[ry]) {
                    // Check rx, ry are still roots, else restart
                    if (nodes[rx].next != rx || nodes[ry].next != ry)
                        continue;
                    if (nodes[rx].rank > nodes[ry].rank) {
                        int tmp = rx; rx = ry; ry = tmp;
                    }
                    // Now nodes[rx].rank <= nodes[ry].rank
                    nodes[rx].next = ry;
                    if (nodes[rx].rank == nodes[ry].rank)
                        nodes[ry].rank++;
                    compress(x, ry);
                    compress(y, ry);
                }
            }  
        } 
    }

    // Assumes lock is held on nodes[root]
    private void compress(int x, final int root) {
        while (nodes[x].next != x) {
            int next = nodes[x].next;
            nodes[x].next = root;
            x = next;
        }
    }

    public boolean sameSet(int x, int y) {
        return find(x) == find(y);
    }

    class Node {
        private volatile int next, rank;
        public Node(int next) {
            this.next = next;
        }
    }
}
