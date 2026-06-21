import {inject, Injectable, signal} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {tap} from 'rxjs';
import {environment} from '../../../environments/environment';
import {ApiResponse} from '../models/api-response.model';
import {
  ProjectInvitation,
  RespondInvitationRequest,
  SendInvitationRequest,
} from '../models/project-invitation.model';

@Injectable({providedIn: 'root'})
export class ProjectInvitationService {

  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/v1`;

  pendingInvitations = signal<ProjectInvitation[]>([]);
  projectInvitations = signal<ProjectInvitation[]>([]);

  send(projectId: string, request: SendInvitationRequest) {
    return this.http
      .post<ApiResponse<ProjectInvitation>>(
        `${this.base}/projects/${projectId}/invitations`,
        request
      )
      .pipe(
        tap(res =>
          this.projectInvitations.update(list => [res.data, ...list])
        )
      );
  }

  getForProject(projectId: string) {
    return this.http
      .get<ApiResponse<ProjectInvitation[]>>(
        `${this.base}/projects/${projectId}/invitations`
      )
      .pipe(tap(res => this.projectInvitations.set(res.data ?? [])));
  }

  respond(invitationId: string, request: RespondInvitationRequest) {
    return this.http
      .post<ApiResponse<ProjectInvitation>>(
        `${this.base}/invitations/${invitationId}/respond`,
        request
      )
      .pipe(
        tap(res => {
          const update = (list: ProjectInvitation[]) =>
            list.map(i => (i.id === invitationId ? res.data : i));

          this.pendingInvitations.update(update);
          this.projectInvitations.update(update);
        })
      );
  }
}
