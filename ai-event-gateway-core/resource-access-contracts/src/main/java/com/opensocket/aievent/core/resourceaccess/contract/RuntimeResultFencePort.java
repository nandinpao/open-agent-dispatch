package com.opensocket.aievent.core.resourceaccess.contract;
public interface RuntimeResultFencePort {
    RuntimeResultFenceResult acceptOrQuarantine(RuntimeResultSubmission submission);
}
