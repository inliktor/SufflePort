# ===== Frontend build + nginx serve =====
FROM node:20-alpine AS build
WORKDIR /frontend
COPY package.json postcss.config.js tailwind.config.js ./
COPY src ./src
COPY scripts ./scripts
RUN npm install --no-audit --no-fund && npm run dev -- & sleep 2 && npm run build || npm run build

FROM nginx:alpine AS prod
WORKDIR /usr/share/nginx/html
# Copy built css and html (index.html references styles.css in same folder)
COPY --from=build /frontend/dist/assets/styles.css ./styles.css
COPY --from=build /frontend/src/index.html ./index.html
# nginx config with API proxy
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80

