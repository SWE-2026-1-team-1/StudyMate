import { apiClient } from "./client";

export type ProfilePayload = {
  name: string;
  school?: string | null;
  major?: string | null;
  bio?: string | null;
  interestTags: string[];
};

export type ProfileResponse = ProfilePayload & {
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
  create: async (data: ProfilePayload) => {
    const response = await apiClient.post<ProfileResponse>("/api/profile", data);
    return response.data;
  },
  update: async (data: ProfilePayload) => {
    const response = await apiClient.put<ProfileResponse>("/api/profile", data);
    return response.data;
  },
  remove: async () => {
    await apiClient.delete("/api/profile");
  },
};
