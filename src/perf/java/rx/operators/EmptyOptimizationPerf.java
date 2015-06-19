package rx.operators;

import org.openjdk.jmh.annotations.Benchmark;

import rx.Observable;

public class EmptyOptimizationPerf {
    
    /*
Benchmark                                        Mode   Samples        Score  Score error    Units
r.o.EmptyOptimizationPerf.testMergeWithEmpty    thrpt         5  1044473.792   141571.867    ops/s
r.o.EmptyOptimizationPerf.testNoMerge           thrpt         5  9946250.934   874336.704    ops/s
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
