# syntax=docker/dockerfile:1.7
# MVX Nexus frontend. Builds from source: no Node needed on your machine.
# (CI that already ran `npm run build` can use Dockerfile.prebuilt instead.)
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.29-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
# The official image runs envsubst over /etc/nginx/templates at start-up, only for variables
# that are defined, so nginx's own $variables are left alone.
ENV HOLMES_BACKEND_UPSTREAM=backend:8080
EXPOSE 80
