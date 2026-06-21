FROM node:20-alpine AS deps

WORKDIR /app

COPY Frontend/package.json Frontend/package-lock.json ./
RUN npm ci --ignore-scripts --legacy-peer-deps && npm cache clean --force


FROM node:20-alpine AS dev

WORKDIR /app

COPY --from=deps /app/node_modules ./node_modules
COPY Frontend/ .

ENV NODE_ENV=development \
    CHOKIDAR_USEPOLLING=true \
    CHOKIDAR_INTERVAL=300

EXPOSE 4200

RUN apk add --no-cache wget

HEALTHCHECK --interval=20s --timeout=5s --start-period=30s --retries=5 \
    CMD wget -qO- http://localhost:4200/ || exit 1

CMD ["npx", "ng", "serve","--host", "0.0.0.0","--port", "4200","--poll", "300","--configuration", "docker"]


FROM deps AS builder

WORKDIR /app

COPY --from=deps /app/node_modules ./node_modules
COPY Frontend/ .

RUN npm run build -- --configuration=production --output-hashing=all


FROM nginx:1.27-alpine AS prod

LABEL org.opencontainers.image.title="Scraping Platform — Frontend" \
      org.opencontainers.image.description="Angular SPA servie par Nginx" \
      org.opencontainers.image.version="1.0.0"

RUN rm -f /etc/nginx/conf.d/default.conf

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist/*/browser /usr/share/nginx/html

RUN addgroup -g 1001 -S nginx-group && \
    adduser -u 1001 -S nginx-user -G nginx-group && \
    chown -R nginx-user:nginx-group /usr/share/nginx/html /var/cache/nginx /var/log/nginx && \
    touch /var/run/nginx.pid && \
    chown nginx-user:nginx-group /var/run/nginx.pid

USER nginx-user

EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://localhost:8080/health || exit 1