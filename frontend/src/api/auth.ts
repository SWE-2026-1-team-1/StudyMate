import { publicApiClient } from "./client";

export type SignupData = {
  email: string;
  password: string;
  name: string;
};

export const authApi = {
  // 로그인
  login: async (data: Record<string, string>) => {
    const response = await publicApiClient.post("/api/auth/login", data);
    return response.data;
  },
  // 회원가입
  signup: async (data: SignupData) => {
    const response = await publicApiClient.post("/api/auth/signup", data);
    return response.data;
  },
  // 로그아웃
  logout: async (data: { refreshToken: string }) => {
    const response = await publicApiClient.post("/api/auth/logout", data);
    return response.data;
  },
  // 인증코드 전송
  sendEmailCode: async (data: { email: string }) => {
    const response = await publicApiClient.post("/api/auth/email/send-code", data);
    return response.data;
  },
  // 인증코드 검증
  verifyEmail: async (data: { email: string; code: string }) => {
    const response = await publicApiClient.post("/api/auth/email/verify", data);
    return response.data;
  },
};
