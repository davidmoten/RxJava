package rx.internal.operators;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import rx.Observable;
import rx.Observable.Operator;
import rx.Producer;
import rx.Subscriber;

public class OperatorMerge2<T> implements Operator<T, Observable<? extends T>> {

    @Override
    public Subscriber<? super Observable<? extends T>> call(Subscriber<? super T> child) {
        Coordinator<T> coordinator = new Coordinator<T>(child);
        child.setProducer(new MergeProducer<T>(coordinator));
        Subscriber<Observable<? extends T>> parent = new MergeSubscriber<T>(coordinator);
        // if child unsubscribes it should unsubscribe the parent, but not the
        // other way around
        child.add(parent);
        return parent;

    }

    private static class Coordinator<T> {

        private final NotificationLite<T> on = NotificationLite.instance();
        private final Subscriber<? super T> child;
        private final List<InnerSubscriber<T>> innerSubscribers = new CopyOnWriteArrayList<InnerSubscriber<T>>();;
        private final AtomicLong expecting = new AtomicLong();
        private volatile boolean requestedAll = false;
        private volatile boolean innersComplete = true;
        final ConcurrentLinkedQueue<Object> queue = new ConcurrentLinkedQueue<Object>();

        public Coordinator(Subscriber<? super T> child) {
            this.child = child;
        }

        public void onError(Throwable e) {
            child.onError(e);
        }

        public void add(Observable<? extends T> inner) {
            InnerSubscriber<T> sub = new InnerSubscriber<T>(this);
            innerSubscribers.add(sub);
            inner.subscribe(sub);
        }

        public synchronized void arrived(T t, long innerExpecting, InnerSubscriber<T> inner) {
            queue.add(on.next(t));
            drainQueue();
            if (expecting.get() > 0 && innerExpecting == 0) {
                inner.requestMore(InnerSubscriber.DEFAULT_INNER_REQUEST);
            }
        }

        private void drainQueue() {
            while (expecting.get() > 0) {
                Object o = queue.poll();
                if (o == null)
                    return;
                else {
                    if (!on.isCompleted(o) && !on.isError(o))
                        expecting.decrementAndGet();
                    on.accept(child, o);
                }
            }
        }

        public void request(long n) {
            if (requestedAll || n == 0)
                return;
            // because we cannot predict which of the inner observables will
            // respond
            if (n == Long.MAX_VALUE) {
                requestedAll = true;
                for (InnerSubscriber<T> innerSubscriber : innerSubscribers)
                    innerSubscriber.requestMore(n);
            } else {
                expecting.addAndGet(n);
                for (InnerSubscriber<T> innerSubscriber : innerSubscribers)
                    innerSubscriber.requestMore(n);
            }
        }

        public void onCompleted() {
            innersComplete = false;
        }

        public void onCompleted(InnerSubscriber<T> inner) {
            innerSubscribers.remove(inner);
            if (innerSubscribers.size() == 0 && !innersComplete) {
                queue.add(on.completed());
            }
        }

    }

    private static class MergeProducer<T> implements Producer {

        private Coordinator<T> coordinator;

        MergeProducer(Coordinator<T> coordinator) {
            this.coordinator = coordinator;
        }

        @Override
        public void request(long n) {
            coordinator.request(n);
        }

    }

    private static class MergeSubscriber<T> extends Subscriber<Observable<? extends T>> {

        private Coordinator<T> coordinator;

        public MergeSubscriber(Coordinator<T> coordinator) {
            this.coordinator = coordinator;
        }

        @Override
        public void onCompleted() {
            coordinator.onCompleted();
        }

        @Override
        public void onError(Throwable e) {
            coordinator.onError(e);
        }

        @Override
        public void onNext(Observable<? extends T> inner) {
            coordinator.add(inner);
        }

    }

    private static class InnerSubscriber<T> extends Subscriber<T> {

        static final int DEFAULT_INNER_REQUEST = 128;
        private final AtomicLong expecting = new AtomicLong(0);
        private final Coordinator<T> coordinator;

        InnerSubscriber(Coordinator<T> coordinator) {
            this.coordinator = coordinator;
        }

        public synchronized void requestMore(long n) {
            long more = Math.min(DEFAULT_INNER_REQUEST - expecting.get(), n);
            expecting.addAndGet(more);
            request(more);
        }

        @Override
        public void onCompleted() {
            coordinator.onCompleted(this);
        }

        @Override
        public void onError(Throwable e) {
            coordinator.onError(e);
        }

        @Override
        public void onNext(T t) {
            coordinator.arrived(t, expecting.decrementAndGet(), this);
        }

    }

}
