import { apiClient } from "./client";

export type TeamMemberResponse = {
  memberId: number;
  userId: number;
  userName: string;
  roleCode: string;
  joinedAt: string;
};

export type TeamMemberListResponse = {
  members: TeamMemberResponse[];
  totalCount: number;
};

export const teamMembersApi = {
  list: async (teamId: number | string) => {
    const response = await apiClient.get<TeamMemberListResponse>(`/api/teams/${teamId}/members`);
    return response.data;
  },

  kick: async (teamId: number | string, memberId: number | string) => {
    await apiClient.delete(`/api/teams/${teamId}/members/${memberId}`);
  },

  leave: async (teamId: number | string) => {
    await apiClient.delete(`/api/teams/${teamId}/members/me`);
  },
};
