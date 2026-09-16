package com.opensocket.aievent.core.resourceaccess.contract;
/** One explicitly declared resource action for a background job. */
public record BackgroundJobResourceRequest(ResourceAction action,ResourceRef resourceRef,VisibilityLevel requestedVisibility){public BackgroundJobResourceRequest{if(action==null||resourceRef==null)throw new IllegalArgumentException("action and resourceRef are required");requestedVisibility=requestedVisibility==null?VisibilityLevel.NONE:requestedVisibility;}}
