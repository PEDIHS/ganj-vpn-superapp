ARG NODE_IMAGE=node:22.20.0-alpine3.22

FROM ${NODE_IMAGE} AS dependencies
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev --ignore-scripts --no-audit --no-fund \
    && npm cache clean --force

FROM dependencies AS test
ENV NODE_ENV=test
COPY src ./src
COPY migrations ./migrations
COPY test ./test

FROM ${NODE_IMAGE} AS runtime
ENV NODE_ENV=production
WORKDIR /app
RUN addgroup -g 10101 -S controlapi \
    && adduser -u 10101 -S -D -H -G controlapi controlapi
COPY --from=dependencies --chown=controlapi:controlapi /app/node_modules ./node_modules
COPY --chown=controlapi:controlapi package.json package-lock.json README.md ./
COPY --chown=controlapi:controlapi src ./src
COPY --chown=controlapi:controlapi migrations ./migrations
USER 10101:10101
EXPOSE 8080
CMD ["node", "src/index.js"]
