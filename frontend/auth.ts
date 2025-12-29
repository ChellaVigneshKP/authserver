import NextAuth from "next-auth";
import CredentialsProvider from "next-auth/providers/credentials";
import { cookies } from "next/headers";

export const { handlers, signIn, signOut, auth } = NextAuth({
  providers: [
    CredentialsProvider({
      name: "Credentials",
      credentials: {
        username: { label: "Username", type: "text" },
        password: { label: "Password", type: "password" },
      },
      async authorize(credentials) {
        if (!credentials?.username || !credentials?.password) {
          return null;
        }

        try {
          // Call the backend login endpoint
          const response = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/login`, {
            method: "POST",
            headers: {
              "Content-Type": "application/x-www-form-urlencoded",
            },
            body: new URLSearchParams({
              username: credentials.username as string,
              password: credentials.password as string,
            }),
            credentials: "include",
          });

          if (!response.ok) {
            return null;
          }

          // Extract session cookie
          const setCookieHeader = response.headers.get("set-cookie");
          
          // Store the session cookie for future requests
          if (setCookieHeader) {
            const cookieStore = await cookies();
            const sessionMatch = setCookieHeader.match(/SESSION=([^;]+)/);
            if (sessionMatch) {
              cookieStore.set("BACKEND_SESSION", sessionMatch[1], {
                httpOnly: true,
                secure: process.env.NODE_ENV === "production",
                sameSite: "lax",
                maxAge: 1800, // 30 minutes
              });
            }
          }

          // Return user object
          return {
            id: credentials.username as string,
            name: credentials.username as string,
            email: `${credentials.username}@example.com`,
          };
        } catch (error) {
          console.error("Authentication error:", error);
          return null;
        }
      },
    }),
  ],
  pages: {
    signIn: "/auth/signin",
  },
  session: {
    strategy: "jwt",
    maxAge: 30 * 60, // 30 minutes
  },
  callbacks: {
    async jwt({ token, user }) {
      if (user) {
        token.id = user.id;
        token.name = user.name;
      }
      return token;
    },
    async session({ session, token }) {
      if (token) {
        session.user.id = token.id as string;
        session.user.name = token.name as string;
      }
      return session;
    },
  },
  secret: process.env.NEXTAUTH_SECRET,
});
