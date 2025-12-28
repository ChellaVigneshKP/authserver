import axios, { AxiosInstance, AxiosRequestConfig } from "axios";
import { cookies } from "next/headers";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:9080/services";

// Create axios instance
const apiClient: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    "Content-Type": "application/json",
  },
  withCredentials: true, // Important for sending cookies
});

// Helper function to get session cookie
async function getSessionCookie() {
  const cookieStore = await cookies();
  const sessionCookie = cookieStore.get("BACKEND_SESSION");
  return sessionCookie?.value;
}

// Request interceptor to add session cookie
apiClient.interceptors.request.use(
  async (config) => {
    const session = await getSessionCookie();
    if (session) {
      config.headers.Cookie = `SESSION=${session}`;
    }
    
    // Add request datetime header for security validation
    config.headers["x-request-datetime"] = new Date().toISOString();
    
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor for error handling
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Unauthorized - redirect to login
      if (typeof window !== "undefined") {
        window.location.href = "/auth/signin";
      }
    }
    return Promise.reject(error);
  }
);

export default apiClient;

// Server-side API client (for use in Server Components and API routes)
export async function createServerApiClient(): Promise<AxiosInstance> {
  const session = await getSessionCookie();
  
  return axios.create({
    baseURL: API_BASE_URL,
    headers: {
      "Content-Type": "application/json",
      "Cookie": session ? `SESSION=${session}` : "",
      "x-request-datetime": new Date().toISOString(),
    },
    withCredentials: true,
  });
}

// Client-side API client (for use in Client Components)
export const createClientApiClient = (): AxiosInstance => {
  return axios.create({
    baseURL: API_BASE_URL,
    headers: {
      "Content-Type": "application/json",
      "x-request-datetime": new Date().toISOString(),
    },
    withCredentials: true,
  });
};
