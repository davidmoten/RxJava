package rx.jmh;

import java.util.concurrent.CountDownLatch;

import org.openjdk.jmh.infra.Blackhole;

import rx.Subscriber;

public class LatchedBackpressureSubscriber<T> extends Subscriber<T> {

    public CountDownLatch latch = new CountDownLatch(1);
    private final Blackhole bh;

    public LatchedBackpressureSubscriber(Blackhole bh) {
        this.bh = bh;
    }
    
    @Override
    public void onStart() {
        request(1);
    }

    @Override
    public void onCompleted() {
        latch.countDown();
    }

    @Override
    public void onError(Throwable e) {
        latch.countDown();
    }

    @Override
    public void onNext(T t) {
        bh.consume(t);
        request(1);
    }
}
