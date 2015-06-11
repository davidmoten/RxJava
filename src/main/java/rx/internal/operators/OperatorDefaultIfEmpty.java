/**
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package rx.internal.operators;

import java.util.concurrent.atomic.AtomicInteger;

import rx.Observable.Operator;
import rx.Producer;
import rx.Subscriber;

/**
 * Returns the elements of the specified sequence or the specified default value
 * in a singleton sequence if the sequence is empty.
 * 
 * @param <T>
 *            the value type
 */
public class OperatorDefaultIfEmpty<T> implements Operator<T, T> {
    final T defaultValue;

    public OperatorDefaultIfEmpty(T defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    public Subscriber<? super T> call(final Subscriber<? super T> child) {
        final ParentSubscriber<T> parent = new ParentSubscriber<T>(child, defaultValue);
        child.add(parent);
        child.setProducer(new Producer() {
            @Override
            public void request(long n) {
                parent.requestMore(n);
            }
        });
        return parent;
    }

    private static class ParentSubscriber<T> extends Subscriber<T> {

        private final Subscriber<? super T> child;
        private final T defaultValue;
        private static final int REQUESTED = 0;
        private static final int NOT_REQUESTED = 1;
        private static final int DONE = 2;
        private static final int EMPTY_NOT_REQUESTED = 3;
        private static final int EMPTY_REQUESTED = 4;
        private static final int HAS_VALUE = 5;
        private final AtomicInteger state = new AtomicInteger(NOT_REQUESTED);

        ParentSubscriber(Subscriber<? super T> child, T defaultValue) {
            this.child = child;
            this.defaultValue = defaultValue;
        }

        public void requestMore(long n) {
            if (n > 0) {
                drain(n);
            }
        }

        private void drain(long n) {
            // n == 0 when called by onComplete
            boolean isPositive = n>0;
            while (true) {
                int s = state.get();
                if (s == NOT_REQUESTED && isPositive) {
                    if (state.compareAndSet(NOT_REQUESTED, REQUESTED)) {
                        break;
                    }
                } else if (s == EMPTY_NOT_REQUESTED) {
                    if (state.compareAndSet(EMPTY_NOT_REQUESTED, EMPTY_REQUESTED)) {
                        emitDefaultValue();
                        return;
                    }
                } else if (s == DONE) {
                    return;
                } else if (s == HAS_VALUE)
                    break;
            }
            request(n);
        }

        @Override
        public void onNext(T t) {
            state.set(HAS_VALUE);
            child.onNext(t);
        }

        @Override
        public void onError(Throwable e) {
            child.onError(e);
        }

        @Override
        public void onCompleted() {
            
            drain(0);
        }

        private void emitDefaultValue() {
            try {
                child.onNext(defaultValue);
            } catch (Throwable e) {
                child.onError(e);
                return;
            }
            child.onCompleted();
        }
    }

}
