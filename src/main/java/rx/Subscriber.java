/**
 * Copyright 2014 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package rx;

import rx.internal.util.SubscriptionList;

/**
 * Provides a mechanism for receiving push-based notifications from Observables, and permits manual
 * unsubscribing from these Observables.
 * <p>
 * After a Subscriber calls an {@link Observable}'s {@link Observable#subscribe subscribe} method, the
 * {@link Observable} calls the Subscriber's {@link #onNext} method to emit items. A well-behaved
 * {@link Observable} will call a Subscriber's {@link #onCompleted} method exactly once or the Subscriber's
 * {@link #onError} method exactly once.
 * 
 * @see <a href="http://reactivex.io/documentation/observable.html">ReactiveX documentation: Observable</a>
 * @param <T>
 *          the type of items the Subscriber expects to observe
 */
public abstract class Subscriber<T> implements Observer<T>, Subscription {
    
    /**
     * Used internally only to indicate an unspecified number of requests.
     */
    private static final long NOT_SET = Long.MIN_VALUE;

    /**
     * The list of subscriptions to be unsubscribed when this is unsubscribed.
     */
    private final SubscriptionList subscriptions;
    
    /**
     * If present this subscriber is used TODO  
     */
    private final Subscriber<?> producingSubscriber;
    
    /**
     * If present this is used TODO 
     */
    /* protected by `this` */
    private Producer producer;
    
    /* protected by `this` */
    private long requested = NOT_SET; // default to not set

    /**
     * TODO
     */
    protected Subscriber() {
        this(null, false);
    }

    /**
     * TODO
     * 
     * @param producingSubscriber
     */
    protected Subscriber(Subscriber<?> producingSubscriber) {
        this(producingSubscriber, true);
    }

    /**
     * Construct a Subscriber by using another Subscriber for backpressure and optionally sharing the
     * underlying subscriptions list.
     * <p>
     * To retain the chaining of subscribers, add the created instance to {@code subscriber} via {@link #add}.
     * 
     * @param producingSubscriber
     *            the subscriber that will be delegated the setProducer call
     * @param shareSubscriptions
     *            {@code true} to share the subscription list in {@code subscriber} with this instance
     * @since 1.0.6
     */
    protected Subscriber(Subscriber<?> producingSubscriber, boolean shareSubscriptions) {
        this.producingSubscriber = producingSubscriber;
        this.subscriptions = shareSubscriptions && producingSubscriber != null ? producingSubscriber.subscriptions : new SubscriptionList();
    }

    /**
     * Adds a {@link Subscription} to this Subscriber's list of subscriptions if this list is not marked as
     * unsubscribed. If the list <em>is</em> marked as unsubscribed, {@code add} will indicate this by
     * explicitly unsubscribing the new {@code Subscription} as well.
     *
     * @param s
     *            the {@code Subscription} to add
     */
    public final void add(Subscription s) {
        subscriptions.add(s);
    }

    @Override
    public final void unsubscribe() {
        subscriptions.unsubscribe();
    }

    /**
     * Indicates whether this Subscriber has unsubscribed from its list of subscriptions.
     * 
     * @return {@code true} if this Subscriber has unsubscribed from its subscriptions, {@code false} otherwise
     */
    @Override
    public final boolean isUnsubscribed() {
        return subscriptions.isUnsubscribed();
    }

    /**
     * This method is invoked when the Subscriber and Observable have been connected but the Observable has
     * not yet begun to emit items or send notifications to the Subscriber. Override this method to add any
     * useful initialization to your subscription, for instance to initiate backpressure.
     */
    public void onStart() {
        // do nothing by default
    }
    
    /**
     * Request a certain maximum number of emitted items from the Observable this Subscriber is subscribed to.
     * This is a way of requesting backpressure. To disable backpressure, pass {@code Long.MAX_VALUE} to this
     * method.
     * <p>
     * Requests are additive but if a sequence of requests totals more than {@code Long.MAX_VALUE} then 
     * {@code Long.MAX_VALUE} requests will be actioned and the extras <i>may</i> be ignored. Arriving at 
     * {@code Long.MAX_VALUE} by addition of requests cannot be assumed to disable backpressure. For example, 
     * the code below may result in {@code Long.MAX_VALUE} requests being actioned only.
     * 
     * <pre>
     * request(100);
     * request(Long.MAX_VALUE-1);
     * </pre>
     * 
     * @param n the maximum number of items you want the Observable to emit to the Subscriber at this time, or
     *           {@code Long.MAX_VALUE} if you want the Observable to emit items at its own pace
     * @throws IllegalArgumentException
     *             if {@code n} is negative
     */
    protected final void request(long n) {
        if (n < 0) {
            throw new IllegalArgumentException("number requested cannot be negative: " + n);
        } 
        // this weird bit of code below exists so that the request values and the producer 
        // to use are read safely (taking into account concurrent calls to this method or to setProducer) 
        // and then producer.request() called with no lock (the monitor of this)
        
        Producer producerToRequestFrom = null;
        synchronized (this) {
            // if this method called by onStart then producer would not normallly have been set.
            // the onSubscribe method may call setProducer for a source that supports 
            // backpressure
            if (producer != null) {
                producerToRequestFrom = producer;
            } 
            // else update the number requested
            else if (requested == NOT_SET) {
                requested = n;
            } else { 
                final long total = requested + n;
                // check if overflow occurred
                if (total < 0) {
                    requested = Long.MAX_VALUE;
                } else {
                    requested = total;
                }
            }
        }
        // must release monitor because calling request(n) (which may then call onNext, etc) 
        // is not allowed (TODO explain why exactly)
        if (producerToRequestFrom != null) {
            producerToRequestFrom.request(n);
        }
    }

    /**
     * TODO!!!
     * 
     * @warn javadoc description missing
     * @warn param producer not described
     * @param p
     */
    public void setProducer(Producer p) {
        long toRequest;
        boolean setProducer = false;
        synchronized (this) {
            toRequest = requested;
            producer = p;
            if (producingSubscriber != null) {
                // middle operator ... we pass thru unless a request has been made
                if (toRequest == NOT_SET) {
                    // we pass-thru to the next producer as nothing has been requested
                    setProducer = true;
                }

            }
        }
        // do after releasing lock
        if (setProducer) {
            producingSubscriber.setProducer(producer);
        } else {
            // we execute the request with whatever has been requested (or Long.MAX_VALUE)
            if (toRequest == NOT_SET) {
                producer.request(Long.MAX_VALUE);
            } else {
                producer.request(toRequest);
            }
        }
    }
}
