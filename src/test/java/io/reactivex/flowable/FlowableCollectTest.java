package io.reactivex.flowable;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import io.reactivex.Flowable;
import io.reactivex.functions.BiConsumer;
import io.reactivex.functions.Consumer;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.subscribers.TestSubscriber;

public class FlowableCollectTest {

    @Test
    public void testFactoryFailureResultsInErrorEmission() {
        TestSubscriber<Object> ts = TestSubscriber.create();
        final RuntimeException e = new RuntimeException();
        Flowable.just(1).collect(new Callable<List<Integer>>() {

            @Override
            public List<Integer> call() throws Exception {
                throw e;
            }
        }, new BiConsumer<List<Integer>, Integer>() {

            @Override
            public void accept(List<Integer> list, Integer t) {
                list.add(t);
            }
        }).subscribe(ts);
        ts.assertNoValues();
        ts.assertError(e);
        ts.assertNotComplete();
    }

    @Test
    public void testCollectorFailureDoesNotResultInTwoErrorEmissions() {
        try {
            final List<Throwable> list = new CopyOnWriteArrayList<Throwable>();
            RxJavaPlugins.setErrorHandler(new Consumer<Throwable>() {

                @Override
                public void accept(Throwable t) {
                    list.add(t);
                }
            });
            final RuntimeException e1 = new RuntimeException();
            final RuntimeException e2 = new RuntimeException();
            TestSubscriber<List<Integer>> ts = TestSubscriber.create();

            new Flowable<Integer>() {

                @Override
                protected void subscribeActual(final Subscriber<? super Integer> s) {
                    s.onSubscribe(new Subscription() {

                        @Override
                        public void request(long n) {
                            if (n > 0) {
                                s.onNext(1);
                                s.onError(e2);
                            }
                        }

                        @Override
                        public void cancel() {
                            // can do nothing
                        }
                    });

                }
            }.collect(
                    new Callable<List<Integer>>() {

                        @Override
                        public List<Integer> call() {
                            return new ArrayList<Integer>();
                        }
                    }, //
                    new BiConsumer<List<Integer>, Integer>() {

                        @Override
                        public void accept(List<Integer> t1, Integer t2) {
                            throw e1;
                        }
                    })
                .subscribe(ts);
            ts.assertError(e1);
            ts.assertNotComplete();
            assertEquals(Arrays.asList(e2), list);
        } finally {
            RxJavaPlugins.reset();
        }
    }

    // @Test
    // public void
    // testCollectorFailureDoesNotResultInErrorAndCompletedEmissions() {
    // final RuntimeException e1 = new RuntimeException();
    // TestSubscriber<List<Integer>> ts = TestSubscriber.create();
    // Observable.create(new OnSubscribe<Integer>() {
    //
    // @Override
    // public void call(final Subscriber<? super Integer> sub) {
    // sub.setProducer(new Producer() {
    //
    // @Override
    // public void request(long n) {
    // if (n > 0) {
    // sub.onNext(1);
    // sub.onCompleted();
    // }
    // }
    // });
    // }
    // }).collect(new Func0<List<Integer>>() {
    //
    // @Override
    // public List<Integer> call() {
    // return new ArrayList<Integer>();
    // }
    // }, //
    // new Action2<List<Integer>, Integer>() {
    //
    // @Override
    // public void call(List<Integer> t1, Integer t2) {
    // throw e1;
    // }
    // }).unsafeSubscribe(ts);
    // assertEquals(Arrays.asList(e1), ts.getOnErrorEvents());
    // ts.assertNotCompleted();
    // }
    //
    // @Test
    // public void testCollectorFailureDoesNotResultInErrorAndOnNextEmissions()
    // {
    // final RuntimeException e1 = new RuntimeException();
    // TestSubscriber<List<Integer>> ts = TestSubscriber.create();
    // final AtomicBoolean added = new AtomicBoolean();
    // Observable.create(new OnSubscribe<Integer>() {
    //
    // @Override
    // public void call(final Subscriber<? super Integer> sub) {
    // sub.setProducer(new Producer() {
    //
    // @Override
    // public void request(long n) {
    // if (n > 0) {
    // sub.onNext(1);
    // sub.onNext(2);
    // }
    // }
    // });
    // }
    // }).collect(new Func0<List<Integer>>() {
    //
    // @Override
    // public List<Integer> call() {
    // return new ArrayList<Integer>();
    // }
    // }, //
    // new Action2<List<Integer>, Integer>() {
    // boolean once = true;
    // @Override
    // public void call(List<Integer> list, Integer t) {
    // if (once) {
    // once = false;
    // throw e1;
    // } else {
    // added.set(true);
    // }
    // }
    // }).unsafeSubscribe(ts);
    // assertEquals(Arrays.asList(e1), ts.getOnErrorEvents());
    // ts.assertNoValues();
    // ts.assertNotCompleted();
    // assertFalse(added.get());
    // }

}
