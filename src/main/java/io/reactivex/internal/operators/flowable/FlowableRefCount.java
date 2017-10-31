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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.flowables.ConnectableFlowable;
import io.reactivex.functions.Consumer;
import io.reactivex.internal.disposables.DisposableHelper;
import io.reactivex.internal.fuseable.SimplePlainQueue;
import io.reactivex.internal.queue.MpscLinkedQueue;

public class FlowableRefCount<T> extends AbstractFlowableWithUpstream<T, T> {

    private final SimplePlainQueue<Subscriber<? super T>> queue;
    private final AtomicInteger wip = new AtomicInteger();

    private final AtomicInteger subscriptionCount = new AtomicInteger();

    // contains the disposable obtained from the connect
    // disposing this disposable disconnects the ConnectableFlowable from
    // it's source
    private final AtomicReference<Disposable> connectDisposable = new AtomicReference<Disposable>(
            Disposables.disposed());

    public FlowableRefCount(ConnectableFlowable<T> source) {
        super(source);
        this.queue = new MpscLinkedQueue<Subscriber<? super T>>();
    }

    @Override
    protected void subscribeActual(Subscriber<? super T> s) {
        log("subscribeActual");
        queue.offer(s);
        drain();
    }

    private void drain() {
        log("drain called");
        if (wip.getAndIncrement() == 0) {
            log("draining");
            int missed = 1;
            while (true) {
                while (true) {
                    Subscriber<? super T> s = queue.poll();
                    if (s == null) {
                        break;
                    } else {
                        RefCountSubscriber subscriber = new RefCountSubscriber(s);
                        log("subscribing");
                        super.source.subscribe(subscriber);
                        log("subscribed");
                        if (subscriptionCount.incrementAndGet() == 1) {
                            log("connecting");
                            ((ConnectableFlowable<T>) super.source).connect(subscriber);
                            log("connected");
                        } else {
                            subscriber.markAsConnected();
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

    final class RefCountSubscriber implements Subscriber<T>, Subscription, Consumer<Disposable> {

        private final Subscriber<? super T> child;
        private Subscription parentSubscription;

        private static final int CHECKING_CONNECTION = 0;
        private static final int CANCELLED_WHILE_CHECKING = 1;
        private static final int CONNECTED = 2;
        private static final int DONE = 3;
        private final AtomicInteger state = new AtomicInteger();

        RefCountSubscriber(Subscriber<? super T> child) {
            this.child = child;
        }

        @Override
        public void onSubscribe(Subscription s) {
            this.parentSubscription = s;
            child.onSubscribe(this);
            log("called child onSubscribe");
        }

        @Override
        public void onNext(T t) {
            child.onNext(t);
        }

        @Override
        public void onError(Throwable t) {
            log("error arrived");
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
            log("cancelling");
            parentSubscription.cancel();
            done();
        }

        void markAsConnected() {
            while (true) {
                int s = state.get();
                if (s == CANCELLED_WHILE_CHECKING) {
                    if (state.compareAndSet(s, DONE)) {
                        decrementAndCheckForDisposal();
                        return;
                    }
                } else if (s == CHECKING_CONNECTION) {
                    if (state.compareAndSet(s, CONNECTED)) {
                        return;
                    }
                } else if (state.compareAndSet(s, s)) {
                    return;
                }
            }
        }

        private void done() {
            log("done called with state " + state.get());
            while (true) {
                int s = state.get();
                if (s == CONNECTED) {
                    if (state.compareAndSet(s, DONE)) {
                        decrementAndCheckForDisposal();
                        return;
                    }
                } else if (s == CHECKING_CONNECTION) {
                    if (state.compareAndSet(s, CANCELLED_WHILE_CHECKING)) {
                        return;
                    }
                } else if (state.compareAndSet(s, s)) {
                    return;
                }
            }
        }

        private void decrementAndCheckForDisposal() {
            Disposable d = connectDisposable.get();
            log("decrementing sub count to " + (subscriptionCount.get() - 1));
            if (subscriptionCount.decrementAndGet() == 0) {
                log("disposing");
                d.dispose();
                // ensure no memory leak because we hung onto the upstream disposable
                connectDisposable.compareAndSet(d, Disposables.disposed());
                return;
            } else {
                return;
            }
        }

        @Override
        public void accept(Disposable d) throws Exception {
            DisposableHelper.set(connectDisposable, d);
            markAsConnected();
        }

    }

    private static void log(String s) {
        System.out.println(Thread.currentThread().getName() + "|" + s);
    }
}
