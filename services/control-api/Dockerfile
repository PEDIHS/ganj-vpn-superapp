FROM node:22-alpine AS dependencies
WORKDIR /app
COPY package.json ./
RUN npm install --omit=dev --ignore-scripts --no-audit --no-fund && npm cache clean --force

FROM dependencies AS test
ENV NODE_ENV=test
COPY src ./src
COPY migrations ./migrations
COPY test ./test

FROM node:22-alpine AS runtime
ENV NODE_ENV=production
WORKDIR /app
RUN addgroup -S controlapi && adduser -S -G controlapi controlapi
COPY --from=dependencies /app/node_modules ./node_modules
COPY --chown=controlapi:controlapi package.json README.md ./
COPY --chown=controlapi:controlapi src ./src
COPY --chown=controlapi:controlapi migrations ./migrations
USER controlapi
EXPOSE 8080
CMD ["node", "src/index.js"]
