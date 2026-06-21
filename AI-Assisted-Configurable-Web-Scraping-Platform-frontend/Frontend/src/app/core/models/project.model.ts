export type ProjectStatus = 'ACTIVE' | 'ARCHIVED';
export type ProjectRole = 'OWNER' | 'MEMBER' | 'VIEWER';

export interface Project {
  id: string;
  name: string;
  description: string | null;
  color: string | null;
  status: ProjectStatus;
  ownerId: string;
  ownerUsername: string | null;
  memberCount: number;
  jobCount: number;
  createdBy: string;
  updatedBy: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ProjectMember {
  id: string;
  projectId: string;
  userId: string;
  username: string;
  email: string;
  role: ProjectRole;
  joinedAt: string;
}

export interface CreateProjectRequest {
  name: string;
  description: string | null;
  color: string | null;
}

export interface UpdateProjectRequest {
  name: string;
  description: string | null;
  color: string | null;
}

export interface UpdateMemberRoleRequest {
  role: ProjectRole;
}

export const PROJECT_COLORS = [
  '#3B82F6', '#8B5CF6', '#10B981', '#F59E0B',
  '#EF4444', '#EC4899', '#14B8A6', '#F97316',
  '#6366F1', '#84CC16',
] as const;


export interface ProjectSearchParams {
  page?: number;
  size?: number;
  search?: string;
  status?: ProjectStatus | 'ALL';
  createdFrom?: string;
  createdTo?: string;
  sortBy?: 'createdAt' | 'updatedAt' | 'name';
  sortDirection?: 'asc' | 'desc';
}

export interface ProjectStats {
  total: number;
  active: number;
  archived: number;
}
