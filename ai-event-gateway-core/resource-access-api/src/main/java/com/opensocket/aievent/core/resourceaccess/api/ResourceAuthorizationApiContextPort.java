package com.opensocket.aievent.core.resourceaccess.api;
/** Implemented by the authenticated control-plane runtime; never by request payload data. */
public interface ResourceAuthorizationApiContextPort { ResourceAuthorizationApiContext current(); }
