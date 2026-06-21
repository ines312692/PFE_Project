import {ProjectRole} from './project.model';

export type InvitationStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'EXPIRED';

export interface ProjectInvitation {
  id: string;
  projectId: string;
  projectName: string;
  invitedBy: string;
  invitedByUsername: string;
  role: ProjectRole;
  status: InvitationStatus;
  createdAt: string;
  expiresAt: string;
}

export interface SendInvitationRequest {
  email: string;
  role: ProjectRole;
}

export interface RespondInvitationRequest {
  action: 'ACCEPT' | 'DECLINE';
}
