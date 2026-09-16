package com.opensocket.aievent.core.iam.organization.application.port.in;
import com.opensocket.aievent.core.iam.organization.application.command.*;import com.opensocket.aievent.core.iam.organization.domain.*;
public interface GroupCommandPort { Group createGroup(CreateGroupCommand command); Group updateGroup(UpdateGroupCommand command); Group changeGroupStatus(ChangeGroupStatusCommand command); GroupMembership addGroupMembership(AddGroupMembershipCommand command); GroupMembership updateGroupMembership(UpdateGroupMembershipCommand command); }
