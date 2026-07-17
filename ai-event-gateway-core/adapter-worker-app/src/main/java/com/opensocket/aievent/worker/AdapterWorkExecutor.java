package com.opensocket.aievent.worker;

import com.opensocket.aievent.service.adapter.AdapterWorkItem;

public interface AdapterWorkExecutor {
    boolean supports(AdapterWorkItem item);

    AdapterWorkResult execute(AdapterWorkItem item);
}
