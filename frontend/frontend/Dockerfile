# syntax=docker/dockerfile:1
# ===== Frontend build + nginx serve =====
FROM node:20-alpine AS build
WORKDIR /frontend

# Copy only package manifests and config files first.
# npm install is cached as long as these files don't change —
# changing src/**/* won't invalidate the npm install layer.
COPY package.json postcss.config.js tailwind.config.js ./
COPY scripts ./scripts

# Install dependencies using BuildKit cache mount.
# /root/.npm is persisted across builds on the host (never added to the image layer).
RUN --mount=type=cache,target=/root/.npm \
    npm install --no-audit --no-fund

# Now copy sources and build. npm install layer above stays cached.
# SKIP_COPY_FOR_BUILD tells postbuild script not to copy into a backend dir
# (the backend is a separate container — nginx COPY directives do the job below)
COPY src ./src
ENV SKIP_COPY_FOR_BUILD=true
RUN npm run build

FROM nginx:alpine AS prod
WORKDIR /usr/share/nginx/html
COPY --from=build /frontend/dist/assets/styles.css ./styles.css
COPY --from=build /frontend/src/index.html ./index.html
COPY --from=build /frontend/src/login.html ./login.html
COPY --from=build /frontend/src/js ./js
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
