/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  async rewrites() {
    return [
      {
        source: '/api/proxy/:path*',
        destination: process.env.AUTHSERVER_API_URL || 'http://localhost:9080/api/:path*',
      },
    ];
  },
};

module.exports = nextConfig;
