import { apiClient } from "./client";

export type StudyStatus = "OPEN" | "CLOSED";

export type StudySummaryResponse = {
  studyId: number;
  title: string;
  tags: string[];
  status: StudyStatus;
  currentMembers: number;
  maxMembers: number;
};

export type StudyListResponse = {
  studies: StudySummaryResponse[];
  totalCount: number;
  page: number;
  size: number;
};

export type MyStudyItemResponse = {
  studyId: number;
  teamId: number;
  title: string;
  tags: string[];
  status: StudyStatus;
  role: string;
  currentMembers: number;
  maxMembers: number;
  meetingCycle: string;
  durationWeeks: number;
};

export type MyStudyListResponse = {
  studies: MyStudyItemResponse[];
};

export type StudyDetailResponse = {
  studyId: number;
  title: string;
  description: string;
  tags: string[];
  languages: string[];
  maxMembers: number;
  currentMembers: number;
  meetingCycle: string;
  durationWeeks: number;
  status: StudyStatus;
  createdBy: {
    userId: number;
    name: string;
  };
  createdAt: string;
};

export type CreateStudyRequest = {
  title: string;
  description: string;
  tags: string[];
  languages: string[];
  maxMembers: number;
  durationWeeks: number;
  meetingCycle: string;
};

export type CreateStudyResponse = {
  studyId: number;
  title: string;
  status: StudyStatus;
  currentMembers: number;
  maxMembers: number;
};

export type UpdateStudyRequest = Partial<CreateStudyRequest> & {
  status?: StudyStatus;
};

export type UpdateStudyResponse = {
  studyId: number;
  title: string;
  status: StudyStatus;
  updatedAt: string;
};

export type StudyApplicationResponse = {
  applicationId: number;
  studyId: number;
  status: "PENDING" | "APPROVED" | "REJECTED";
  appliedAt: string;
};

export const studiesApi = {
  list: async ({ page = 0, size = 20 }: { page?: number; size?: number } = {}) => {
    const response = await apiClient.get<StudyListResponse>("/api/studies", {
      params: { page, size },
    });
    return response.data;
  },

  listMine: async ({ page = 0, size = 20 }: { page?: number; size?: number } = {}) => {
    const response = await apiClient.get<MyStudyListResponse>("/api/studies/my", {
      params: { page, size },
    });
    return response.data;
  },

  create: async (data: CreateStudyRequest) => {
    const response = await apiClient.post<CreateStudyResponse>("/api/studies", data);
    return response.data;
  },

  get: async (studyId: number | string) => {
    const response = await apiClient.get<StudyDetailResponse>(`/api/studies/${studyId}`);
    return response.data;
  },

  update: async (studyId: number | string, data: UpdateStudyRequest) => {
    const response = await apiClient.patch<UpdateStudyResponse>(`/api/studies/${studyId}`, data);
    return response.data;
  },

  delete: async (studyId: number | string) => {
    await apiClient.delete(`/api/studies/${studyId}`);
  },

  apply: async (studyId: number | string, data: { message?: string }) => {
    const response = await apiClient.post<StudyApplicationResponse>(`/api/studies/${studyId}/applications`, data);
    return response.data;
  },

  cancelApplication: async (studyId: number | string) => {
    await apiClient.delete(`/api/studies/${studyId}/applications/my`);
  },
};
