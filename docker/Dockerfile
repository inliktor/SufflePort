# ===== Frontend build + nginx serve =====
FROM node:20-alpine AS build
WORKDIR /frontend
COPY package.json postcss.config.js tailwind.config.js ./
COPY src ./src
RUN npm install --no-audit --no-fund && npm run build

FROM nginx:alpine AS prod
WORKDIR /usr/share/nginx/html
COPY --from=build /frontend/dist/assets/styles.css ./styles.css
COPY --from=build /frontend/src/index.html ./index.html
COPY --from=build /frontend/src/login.html ./login.html
COPY --from=build /frontend/src/js ./js
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
