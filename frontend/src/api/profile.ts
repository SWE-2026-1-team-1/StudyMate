import { apiClient } from "./client";

export type ProfileSaveRequest = {
  name: string;
  school: string;
  major: string;
  bio: string;
  interestTags: string[];
};

export type ProfileResponse = ProfileSaveRequest & {
  userId: number;
  email: string;
  createdAt: string;
  updatedAt: string;
};

export const profileApi = {
  get: async () => {
    const response = await apiClient.get<ProfileResponse>("/api/profile");
    return response.data;
  },

  create: async (data: ProfileSaveRequest) => {
    const response = await apiClient.post<ProfileResponse>("/api/profile", data);
    return response.data;
  },

  update: async (data: ProfileSaveRequest) => {
    const response = await apiClient.put<ProfileResponse>("/api/profile", data);
    return response.data;
  },

  delete: async () => {
    await apiClient.delete("/api/profile");
  },
};
