import axios from "axios";

export const apiClient = axios.create({
  baseURL: "", // Using Vite proxy, so we leave baseURL empty to hit /api directly (e.g. /api/auth/login)
  headers: {
    "Content-Type": "application/json",
  },
});

export const publicApiClient = axios.create({
  baseURL: "",
  headers: {
    "Content-Type": "application/json",
  },
});

function isAuthEndpoint(url = "") {
  return url.startsWith("/api/auth/");
}

// Request Interceptor: Attach Access Token
apiClient.interceptors.request.use(
  (config) => {
    const accessToken = localStorage.getItem("accessToken");
    if (config.headers && isAuthEndpoint(config.url)) {
      delete config.headers.Authorization;
      delete config.headers.authorization;
      return config;
    }

    if (accessToken && config.headers) {
      config.headers.Authorization = `Bearer ${accessToken}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response Interceptor: Handle 401 & Refresh Token
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    const requestUrl = originalRequest?.url ?? "";
    const isAuthRequest = isAuthEndpoint(requestUrl);

    if (error.response?.status === 401 && !originalRequest._retry && !isAuthRequest) {
      originalRequest._retry = true;
      const refreshToken = localStorage.getItem("refreshToken");

      if (!refreshToken) {
        // No refresh token -> completely logged out
        localStorage.removeItem("accessToken");
        localStorage.removeItem("userId");
        window.location.href = "/login";
        return Promise.reject(error);
      }

      try {
        // Request a new access token
        const response = await axios.post("/api/auth/token/refresh", {
          refreshToken,
        });

        const newAccessToken = response.data.accessToken;
        const newRefreshToken = response.data.refreshToken;

        // Store new tokens
        localStorage.setItem("accessToken", newAccessToken);
        if (newRefreshToken) localStorage.setItem("refreshToken", newRefreshToken);

        // Update headers and retry original request
        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
        apiClient.defaults.headers.common["Authorization"] = `Bearer ${newAccessToken}`;
        
        return apiClient(originalRequest);
      } catch (refreshError) {
        // Refresh token failed -> Force logout
        localStorage.removeItem("accessToken");
        localStorage.removeItem("refreshToken");
        localStorage.removeItem("userId");
        window.location.href = "/login";
        return Promise.reject(refreshError);
      }
    }
    return Promise.reject(error);
  }
);
