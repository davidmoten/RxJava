/**
 * Copyright (c) 2016-present, RxJava Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */

package io.reactivex.internal.operators.flowable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import io.reactivex.disposables.Disposable;
import io.reactivex.flowables.ConnectableFlowable;
import io.reactivex.functions.Consumer;
import io.reactivex.internal.fuseable.SimplePlainQueue;
import io.reactivex.internal.queue.MpscLinkedQueue;

public class FlowableRefCount<T> extends AbstractFlowableWithUpstream<T, T>
        implements Consumer<Disposable> {

    private final SimplePlainQueue<Subscriber<? super T>> queue;
    private final AtomicInteger wip = new AtomicInteger();

    private final AtomicInteger subscriptionCount = new AtomicInteger();

    // contains the disposable obtained from the connect option
    // disposing this disposable disconnects the ConnectableFlowable from
    // it's source
    private Disposable connectDisposable;

    public FlowableRefCount(ConnectableFlowable<T> source) {
        super(source);
        this.queue = new MpscLinkedQueue<Subscriber<? super T>>();
    }

    @Override
    protected void subscribeActual(Subscriber<? super T> s) {
        queue.offer(s);
        drain();
    }

    @Override
    public void accept(Disposable d) throws Exception {
        connectDisposable = d;
    }

    private void drain() {
        if (wip.getAndIncrement() == 0) {
            int missed = 1;
            while (true) {
                while (true) {
                    Subscriber<? super T> s = queue.poll();
                    if (s == null) {
                        break;
                    } else {
                        RefCountSubscriber subscriber = new RefCountSubscriber(s);
                        super.source.subscribe(subscriber);
                        if (subscriptionCount.incrementAndGet() == 1) {
                            ((ConnectableFlowable<T>) super.source).connect(this);
                        }
                    }
                }
                missed = wip.addAndGet(-missed);
                if (missed == 0) {
                    return;
                }
            }
        }
    }

    static class SubsAndCancels {
        final int subscriptionCount;
        final int cancelled;

        SubsAndCancels(int subscriptionCount, int cancelled) {
            this.subscriptionCount = subscriptionCount;
            this.cancelled = cancelled;
        }
    }

    final class RefCountSubscriber implements Subscriber<T>, Subscription {

        private final Subscriber<? super T> child;
        private Subscription parentSubscription;
        private AtomicBoolean done = new AtomicBoolean();

        RefCountSubscriber(Subscriber<? super T> child) {
            this.child = child;
        }

        @Override
        public void onSubscribe(Subscription s) {
            this.parentSubscription = s;
            child.onSubscribe(this);
        }

        @Override
        public void onNext(T t) {
            child.onNext(t);
        }

        @Override
        public void onError(Throwable t) {
            child.onError(t);
            done();
        }

        @Override
        public void onComplete() {
            child.onComplete();
            done();
        }

        @Override
        public void request(long n) {
            parentSubscription.request(n);
        }

        @Override
        public void cancel() {
            parentSubscription.cancel();
            done();
        }

        private void done() {
            if (done.compareAndSet(false, true)) {
                if (subscriptionCount.decrementAndGet() == 0) {
                    connectDisposable.dispose();
                    drain();
                }
            }
        }

    }

}
