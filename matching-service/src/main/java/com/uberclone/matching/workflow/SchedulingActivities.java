package com.uberclone.matching.workflow;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface SchedulingActivities {
    @ActivityMethod
    void activateRide(String rideId);
}
