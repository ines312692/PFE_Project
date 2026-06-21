-- V28__fix_pending_invitation_constraint.sql
ALTER TABLE project_invitations
DROP CONSTRAINT uq_pending_invitation;

CREATE UNIQUE INDEX uq_pending_invitation
    ON project_invitations (project_id, invited_user)
    WHERE status = 'PENDING';