import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Self-contained server build for the Docker image (web microservice).
  output: "standalone",
  // The proxy layer reads server-only env at request time; never bundle it.
  env: {},
};

export default nextConfig;
