package com.opensocket.aievent.core.iam.identity.application.command;

public record UpdateHumanUserProfileCommand(String userId, String displayName, String email, long expectedVersion,
                                            String actorId, String correlationId) { }
