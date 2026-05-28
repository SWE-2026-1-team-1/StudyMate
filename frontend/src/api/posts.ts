import { apiClient } from "./client";

export type PostType = "NOTICE" | "FREE";

export type PostSummaryResponse = {
  postId: number;
  title: string;
  type: PostType;
  authorName: string;
  createdAt: string;
};

export type PostListResponse = {
  posts: PostSummaryResponse[];
  totalCount: number;
};

export type PostDetailResponse = {
  postId: number;
  title: string;
  content: string;
  type: PostType;
  authorName: string;
  createdAt: string;
  updatedAt: string;
};

export type PostCreateRequest = {
  title: string;
  content: string;
  type: PostType;
};

export type PostCreateResponse = {
  postId: number;
  title: string;
  createdAt: string;
};

export type PostUpdateRequest = {
  title?: string;
  content?: string;
};

export type PostUpdateResponse = {
  postId: number;
  title: string;
  updatedAt: string;
};

export type CommentResponse = {
  commentId: number;
  content: string;
  authorName: string;
  createdAt: string;
  updatedAt: string;
};

export type CommentListResponse = {
  comments: CommentResponse[];
  totalCount: number;
};

export const postsApi = {
  list: async (teamId: number | string, { page = 0, size = 20 }: { page?: number; size?: number } = {}) => {
    const response = await apiClient.get<PostListResponse>(`/api/teams/${teamId}/posts`, {
      params: { page, size },
    });
    return response.data;
  },

  create: async (teamId: number | string, data: PostCreateRequest) => {
    const response = await apiClient.post<PostCreateResponse>(`/api/teams/${teamId}/posts`, data);
    return response.data;
  },

  get: async (teamId: number | string, postId: number | string) => {
    const response = await apiClient.get<PostDetailResponse>(`/api/teams/${teamId}/posts/${postId}`);
    return response.data;
  },

  update: async (teamId: number | string, postId: number | string, data: PostUpdateRequest) => {
    const response = await apiClient.patch<PostUpdateResponse>(`/api/teams/${teamId}/posts/${postId}`, data);
    return response.data;
  },

  delete: async (teamId: number | string, postId: number | string) => {
    await apiClient.delete(`/api/teams/${teamId}/posts/${postId}`);
  },

  listComments: async (teamId: number | string, postId: number | string) => {
    const response = await apiClient.get<CommentListResponse>(`/api/teams/${teamId}/posts/${postId}/comments`);
    return response.data;
  },

  createComment: async (teamId: number | string, postId: number | string, data: { content: string }) => {
    const response = await apiClient.post<CommentResponse>(`/api/teams/${teamId}/posts/${postId}/comments`, data);
    return response.data;
  },

  updateComment: async (teamId: number | string, postId: number | string, commentId: number | string, data: { content: string }) => {
    const response = await apiClient.patch<CommentResponse>(`/api/teams/${teamId}/posts/${postId}/comments/${commentId}`, data);
    return response.data;
  },

  deleteComment: async (teamId: number | string, postId: number | string, commentId: number | string) => {
    await apiClient.delete(`/api/teams/${teamId}/posts/${postId}/comments/${commentId}`);
  },
};
