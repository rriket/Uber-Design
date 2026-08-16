package com.uberclone.common.events;

/**
 * Kafka topic name constants — kept in the shared library so publishers
 * and consumers never disagree.
 */
public final class Topics {
    private Topics() {}
    public static final String RIDE_REQUESTED  = "ride.requested";
    public static final String RIDE_SCHEDULED  = "ride.scheduled";
    public static final String RIDE_ACCEPTED   = "ride.accepted";
    public static final String RIDE_COMPLETED  = "ride.completed";
    public static final String RIDE_CANCELLED  = "ride.cancelled";
    public static final String PAYMENT_CHARGED = "payment.charged";
}
