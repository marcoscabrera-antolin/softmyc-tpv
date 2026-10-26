FROM node:22-alpine
ENV NODE_ENV=production
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force
COPY src ./src
COPY scripts ./scripts
USER node
EXPOSE 3100
HEALTHCHECK --interval=15s --timeout=4s --retries=3 CMD wget -qO- http://127.0.0.1:3100/health || exit 1
CMD ["node", "src/server.js"]
