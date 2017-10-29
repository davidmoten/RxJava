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

    // cancellation of downstrem subscribers is tracked
    private final AtomicInteger cancelled = new AtomicInteger();

    // mutable
    private int subscriptionCount;

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
            while (true) {
                checkToDisposeConnect();
                boolean firstTime = true;
                Subscriber<? super T> s;
                while ((s = queue.poll()) != null) {
                    if (!firstTime) {
                        checkToDisposeConnect();
                    } else {
                        firstTime = false;
                    }
                    subscriptionCount++;
                    if (subscriptionCount == 1) {
                        ((ConnectableFlowable<T>) super.source).connect(this);
                    }
                    RefCountSubscriber subscriber = new RefCountSubscriber(s);
                    s.onSubscribe(subscriber);
                    super.source.subscribe(subscriber);
                }
                if (wip.decrementAndGet() == 0) {
                    return;
                }
            }
        }
    }

    private void checkToDisposeConnect() {
        int c = cancelled.get();
        if (subscriptionCount == c) {
            if (connectDisposable != null) {
                connectDisposable.dispose();
            }
            subscriptionCount = 0;
            cancelled.addAndGet(-c);
        }
    }

    final class RefCountSubscriber implements Subscriber<T>, Subscription {

        private final Subscriber<? super T> child;
        private Subscription parentSubscription;

        RefCountSubscriber(Subscriber<? super T> child) {
            this.child = child;
        }

        @Override
        public void onSubscribe(Subscription s) {
            this.parentSubscription = s;
        }

        @Override
        public void onNext(T t) {
            child.onNext(t);
        }

        @Override
        public void onError(Throwable t) {
            child.onError(t);
        }

        @Override
        public void onComplete() {
            child.onComplete();
        }

        @Override
        public void request(long n) {
            parentSubscription.request(n);
        }

        @Override
        public void cancel() {
            parentSubscription.cancel();
            cancelled.incrementAndGet();
            drain();
        }

    }

}
