import { apiClient } from "./client";
import type { StudyStatus } from "./studies";

export type ApplicationStatus = "PENDING" | "APPROVED" | "REJECTED";

export type MyApplicationResponse = {
  applicationId: number;
  studyId: number;
  studyTitle: string;
  studyStatus: StudyStatus;
  status: ApplicationStatus;
  appliedAt: string;
  processedAt: string | null;
};

export type MyApplicationListResponse = {
  applications: MyApplicationResponse[];
  totalCount: number;
  page: number;
  size: number;
};

export type TeamApplicationResponse = {
  applicationId: number;
  applicantUserId: number;
  applicantName: string;
  message: string;
  appliedAt: string;
};

export type TeamApplicationListResponse = {
  applications: TeamApplicationResponse[];
  totalCount: number;
  page: number;
  size: number;
};

export type ApplicationApproveResponse = {
  applicationId: number;
  status: ApplicationStatus;
  processedAt: string;
  studyStatusAfter: StudyStatus;
};

export type ApplicationRejectResponse = {
  applicationId: number;
  status: ApplicationStatus;
  processedAt: string;
  rejectReason: string;
};

export const applicationsApi = {
  listMine: async ({ page = 0, size = 20 }: { page?: number; size?: number } = {}) => {
    const response = await apiClient.get<MyApplicationListResponse>("/api/mypage/applications", {
      params: { page, size },
    });
    return response.data;
  },

  listTeam: async (teamId: number | string, { page = 0, size = 20 }: { page?: number; size?: number } = {}) => {
    const response = await apiClient.get<TeamApplicationListResponse>(`/api/teams/${teamId}/applications`, {
      params: { page, size },
    });
    return response.data;
  },

  approveTeamApplication: async (teamId: number | string, applicationId: number | string) => {
    const response = await apiClient.post<ApplicationApproveResponse>(`/api/teams/${teamId}/applications/${applicationId}/approve`);
    return response.data;
  },

  rejectTeamApplication: async (teamId: number | string, applicationId: number | string, data: { rejectReason: string }) => {
    const response = await apiClient.post<ApplicationRejectResponse>(`/api/teams/${teamId}/applications/${applicationId}/reject`, data);
    return response.data;
  },
};
