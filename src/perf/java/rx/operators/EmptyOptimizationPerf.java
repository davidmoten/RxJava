package rx.operators;

import org.openjdk.jmh.annotations.Benchmark;

import rx.Observable;

public class EmptyOptimizationPerf {
    
    /*
Benchmark                                        Mode   Samples        Score  Score error    Units
r.o.EmptyOptimizationPerf.testMergeWithEmpty    thrpt         5  1044473.792   141571.867    ops/s
r.o.EmptyOptimizationPerf.testNoMerge           thrpt         5  9946250.934   874336.704    ops/s

Benchmark                                        Mode   Samples        Score  Score error    Units
r.o.EmptyOptimizationPerf.testMergeWithEmpty    thrpt         5   568297.084   352210.482    ops/s
r.o.EmptyOptimizationPerf.testNoMerge           thrpt         5  4695608.585   209774.224    ops/s

Benchmark                                        Mode   Samples        Score  Score error    Units
r.o.EmptyOptimizationPerf.testMergeWithEmpty    thrpt         5  4977685.689   162826.714    ops/s
r.o.EmptyOptimizationPerf.testNoMerge           thrpt         5  4990233.899   196174.346    ops/s



     */
    
    /*
./gradlew benchmarks "-Pjmh=-f 1 -tu s -bm thrpt -wi 5 -i 5 -r 1 .*EmptyOptimizationPerf.*"
     */

    private static final Observable<Integer> o = Observable.just(1, 2, 3, 4, 5);
    
    @Benchmark
    public void testMergeWithEmpty() {
        o.mergeWith(Observable.<Integer>empty()).subscribe();
    }
    
    @Benchmark
    public void testNoMerge() {
        o.subscribe();
    }

}
