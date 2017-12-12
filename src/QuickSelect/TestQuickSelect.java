import java.util.*;
import java.util.stream.*;
import java.util.function.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

class TestQuickSelect {
    public static int medianSort(int[] inp) {
        int w[] = Arrays.copyOf(inp, inp.length);
        Arrays.sort(w);
        return w[w.length/2];
    }
    public static int medianPSort(int[] inp) {
        int w[] = Arrays.copyOf(inp, inp.length);
        Arrays.parallelSort(w);
        return w[w.length/2];
    }

    public static int partition(int[] w, int min, int max) {
        int p = min; // use w[p] as pivot
        int left=min+1, right = max-1;
        while(left <= right) {
            while( w[left] <= w[p] && left < right ) left++;
            while( w[right] > w[p] && left <= right ) right--;
            if(left >= right) break;
            int t=w[left]; w[left]=w[right]; w[right]=t;
        }
        int t=w[p]; w[p]=w[right]; w[right]=t;
        return right;
    }

    public static int quickSelect(int[] inp) {
        int w[] = Arrays.copyOf(inp, inp.length);
        return quickSelect(w,0,w.length,w.length/2);
    }
    public static int quickSelect(int[] w, int min, int max, int target) {
        int p = partition(w,min,max);
        if( p < target ) return quickSelect(w,p+1,max,target);
        if( p > target ) return quickSelect(w,min,p,target);
        return w[target]; // p==target
    }

    public static int quickSelectIt(int[] inp) {
        int w[] = Arrays.copyOf(inp, inp.length);
        int target = w.length/2;
        int p = -1, min=0, max=w.length;
        do{
            p = partition(w,min,max);
            if( p < target )  min=p+1;
            if( p > target )  max=p;
        } while(p!=target);
        return w[p];
    }

    public static int quickCountRec(int[] inp, int target) {
        final int p=inp[0], n=inp.length;
        int  count=0;
        for(int i=1;i<n;i++) if(inp[i]<p) count++;
        if(count > target) {
            int m[] = new int[count];
            int j=0;
            for(int i=1;i<n;i++) if(inp[i]<p) m[j++]=inp[i];
            return quickCountRec(m,target);
        }
        if(count < target) {
            int m[] = new int[n-count-1];
            int j=0;
            for(int i=1;i<n;i++) if(inp[i]>=p) m[j++]=inp[i];
            return quickCountRec(m,target-count-1);
        }    
        return p; // we are on target
    }

    public static int quickCountIt(int[] inp) {
        int p=-1, count=0, n=inp.length;
        int target = n/2;
        do {
            p=inp[0];
            count=0;
            n=inp.length;
            for(int i=1;i<n;i++) if(inp[i]<p) count++;
            if(count > target) {
                int m[] = new int[count];
                int j=0;
                for(int i=1;i<n;i++) if(inp[i]<p) m[j++]=inp[i];
                inp = m;
                continue;
            }
            if(count < target) {
                int m[] = new int[n-count-1];
                int j=0;
                for(int i=1;i<n;i++) if(inp[i]>=p) m[j++]=inp[i];
                inp =m;
                target=target-count-1;
                continue;
            }
            break;
        } while( true );
        return p; // we are on target
    }

    // Takes an arr, a partition, the size of the outpuit array and a BiFunction,
    // returning an array of the given size containing elements of arr for which 
    // f.apply(arr[i], partition) returns true.
    public static int[] filter(int[] arr, int partition, int size,  BiFunction<Integer,Integer,Boolean> f){
        int[] m = new int[size];
        ArrayList<Callable<Void>> filterers = new ArrayList<>();
        final AtomicInteger j = new AtomicInteger(0);
        final int step = arr.length/threadCount;
        for(int i=0;i<threadCount;i++) {
            final int from = i==0 ? 1 : i*step;
            final int to = i==threadCount-1 ? arr.length : i*step+step;
            filterers.add(() -> {
                for(int h= from; h<to; h++)
                    if(f.apply(arr[h],partition)) m[j.getAndIncrement()]=arr[h];
                return null;
            });
        }
        try{ executor.invokeAll(filterers);
        } catch (InterruptedException e) { System.err.println("Threads interrupted");}
        return m;
    }

    final static ExecutorService executor = Executors.newWorkStealingPool();
    final static int CUTOFF = 10_000;
    public static int quickCountItTask(int[] in) {
        int target = in.length/2;
        do {
            if (in.length <= CUTOFF) return quickCountIt(in);
            final AtomicInteger count = new AtomicInteger(0);
            final int[] inp = in;
            final int n = inp.length, p = inp[0];
            final int step = n/threadCount;

            //Counting
            ArrayList<Callable<Void>> counters = new ArrayList<>();
            for(int i=0;i<threadCount;i++) {
                final int from = i==0 ? 1 : i*step; //skip pivot
                // for undivisible numbers, just let the last thread take a larger chunk
                final int to = i==threadCount-1 ? inp.length : i*step+step;
                counters.add(() -> {
                    for(int j= from; j<to; j++)
                        if(inp[j]<p) count.getAndIncrement(); 
                    return null;
                });
            }
            try{ executor.invokeAll(counters);
            } catch (InterruptedException e) { System.err.println("Threads interrupted");}

            if (count.get() == target) return p; //Terminated

            //Filtering
            boolean tooLargeP = count.get() > target;
            int size = tooLargeP ? count.get() : n-count.get()-1;
            if(tooLargeP) {
                in = filter(inp, p, size, (x,y) -> x < y);
            } else {
                in = filter(inp, p, size, (x,y) -> x >= y);
                target=target-count.get()-1;
            }
        } while( true );
    }

    public static int quickCountStreamP(int[] inp) {
        int partition=-1;
        int target = inp.length/2;
        // Since we have to be working with boxed Integers. We start off by converting.
        List<Integer> list = Arrays.stream(inp).boxed().collect(Collectors.toList());
        do {
            partition = list.get(0);
            final Integer p = partition;
            Map<Boolean, List<Integer>> res = list.stream().skip(1).parallel()
                .collect(Collectors.partitioningBy(i -> i < p));

            List<Integer> smaller = res.get(true);
            List<Integer> bigger = res.get(false);

            if (smaller.size() == target) break;
            if (smaller.size() > target) list = smaller;
            else {
                target=target-smaller.size()-1;
                list = bigger;
            }
        } while( true );
        return partition; // we are on target
    }

    public static int quickCountStream(int[] inp) {
        int partition=-1;
        int target = inp.length/2;
        // Since we have to be working with boxed Integers. We start off by converting.
        List<Integer> list = Arrays.stream(inp).boxed().collect(Collectors.toList());
        do {
            partition = list.get(0);
            final Integer p = partition;
            Map<Boolean, List<Integer>> res = list.stream().skip(1)
                .collect(Collectors.partitioningBy(i -> i < p));

            List<Integer> smaller = res.get(true);
            List<Integer> bigger = res.get(false);

            if (smaller.size() == target) break;
            if (smaller.size() > target) list = smaller;
            else {
                target=target-smaller.size()-1;
                list = bigger;
            }
        } while( true );
        return partition; // we are on target
    }

    public static final int threadCount = 4;
    public static void main( String [] args ) {
        SystemInfo();
        int a[] = new int[Integer.parseInt(args[0])];
        Random rnd = new Random();
        if( args.length == 1 ) {
            int nrIt = 10;
            for(int ll=0;ll<nrIt;ll++) {
                rnd.setSeed(23434+ll); // seed
                for(int i=0;i<a.length;i++) a[i] = rnd.nextInt(4*a.length);
                final int ra = quickCountRec(a,a.length/2);
                final int rb = medianPSort(a);
                if( ra !=rb ) { 
                    System.out.println(ll);
                    System.out.println(ra);
                    System.out.println(rb);
                    System.exit(0);
                }
            }
            System.out.println();
        } else {
            rnd.setSeed(23434+Integer.parseInt(args[1])); // seed
            for(int i=0;i<a.length;i++) a[i] = rnd.nextInt(4*a.length);
            System.out.println(medianPSort(a));
            System.out.println(quickCountRec(a,a.length/2));
        }
        double d=0.0;
        d += Mark9("serial sort", a.length, x -> medianSort(a));
        d += Mark9("parall sort", a.length, x -> medianPSort(a));
        d += Mark9("serial qsel", a.length, x -> quickSelect(a));
        d += Mark9("ser countRc", a.length,x -> quickCountRec(a,a.length/2));
        d += Mark9("ser countIt", a.length,x -> quickCountIt(a));
        d += Mark9("par countIt", a.length,x -> quickCountItTask(a));
        d += Mark9("countStream", a.length,x -> quickCountStream(a));
        d += Mark9("countStreamP", a.length,x -> quickCountStreamP(a));
        System.out.println(d);
    }

    public static double Mark9(String msg, int size, IntToDoubleFunction f) {
        int n = 5, count = 1, totalCount = 0;
        double dummy = 0.0, runningTime = 0.0, st = 0.0, sst = 0.0;
        do { 
            count *= 2;
            st = sst = 0.0;
            for (int j=0; j<n; j++) {
                Timer t = new Timer();
                for (int i=0; i<count; i++) 
                    dummy += f.applyAsDouble(i);
                runningTime = t.check();
                double time = runningTime * 1e9 / count; // microseconds
                st += time; 
                sst += time * time;
                totalCount += count;
            }
        } while (runningTime < 0.25 && count < Integer.MAX_VALUE/2);
        double mean = st/n/size, sdev = Math.sqrt((sst - mean*mean*n)/(n-1))/size;
        System.out.printf("%-25s %15.1f ns %10.2f %10d%n", msg, mean, sdev, count);
        return dummy / totalCount;
    }

    public static void SystemInfo() {
        System.out.printf("# OS:   %s; %s; %s%n", 
                System.getProperty("os.name"), 
                System.getProperty("os.version"), 
                System.getProperty("os.arch"));
        System.out.printf("# JVM:  %s; %s%n", 
                System.getProperty("java.vendor"), 
                System.getProperty("java.version"));
        // The processor identifier works only on MS Windows:
        System.out.printf("# CPU:  %s; %d \"cores\"%n", 
                System.getenv("PROCESSOR_IDENTIFIER"),
                Runtime.getRuntime().availableProcessors());
        java.util.Date now = new java.util.Date();
        System.out.printf("# Date: %s%n", 
                new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(now));
    }
}
