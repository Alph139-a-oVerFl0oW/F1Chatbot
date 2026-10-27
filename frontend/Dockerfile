# ---------- build ----------
FROM node:22-alpine AS build
WORKDIR /build

COPY package*.json ./
RUN npm ci

COPY . .
RUN npx ng build --configuration production

# ---------- serve ----------
FROM nginx:alpine
COPY --from=build /build/dist/frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
