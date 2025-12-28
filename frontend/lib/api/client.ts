import axios, { AxiosInstance } from "axios";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:9080/services";

// Client-side API client (for use in Client Components)
export const createClientApiClient = (): AxiosInstance => {
  const client = axios.create({
    baseURL: API_BASE_URL,
    headers: {
      "Content-Type": "application/json",
      "x-request-datetime": new Date().toISOString(),
    },
    withCredentials: true, // Important for sending cookies
  });

  // Response interceptor for error handling
  client.interceptors.response.use(
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

  return client;
};

// Server-side API client (for use in Server Components and API routes)
// Import dynamically to avoid SSR issues with next/headers
export async function createServerApiClient(): Promise<AxiosInstance> {
  // Only import cookies on server side
  const { cookies } = await import("next/headers");
  const cookieStore = await cookies();
  const sessionCookie = cookieStore.get("BACKEND_SESSION");
  
  return axios.create({
    baseURL: API_BASE_URL,
    headers: {
      "Content-Type": "application/json",
      "Cookie": sessionCookie ? `SESSION=${sessionCookie.value}` : "",
      "x-request-datetime": new Date().toISOString(),
    },
    withCredentials: true,
  });
}

// Default export for backward compatibility
const apiClient = createClientApiClient();
export default apiClient;

