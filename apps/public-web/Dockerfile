# Server-rendered public pages. Build first: npm run build -w apps/public-web
FROM node:22.20.0-alpine
WORKDIR /app
COPY dist/ /app/
ENV NODE_ENV=production PORT=8093
USER node
EXPOSE 8093
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=6 CMD wget -qO- http://127.0.0.1:8093/healthz >/dev/null || exit 1
CMD ["node", "server.mjs"]
