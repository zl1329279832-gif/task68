# ---- Stage 1: 构建 admin 前端 ----
FROM node:16-alpine AS frontend
WORKDIR /app/admin
COPY huaijiuchangpianshoumai/src/main/webapp/admin/package.json ./
RUN npm install --registry=https://registry.npmmirror.com
COPY huaijiuchangpianshoumai/src/main/webapp/admin/ ./
RUN npm run build

# ---- Stage 2: Maven 打 WAR ----
FROM maven:3.8-openjdk-8 AS builder
WORKDIR /build
COPY huaijiuchangpianshoumai/pom.xml ./
RUN mvn dependency:go-offline -B
COPY huaijiuchangpianshoumai/src ./src
# 用最新的 admin/dist 覆盖源码里可能过期的 dist
COPY --from=frontend /app/admin/dist ./src/main/webapp/admin/dist
RUN mvn clean package -Pprod -DskipTests -B

# ---- Stage 3: 运行 Tomcat ----
FROM tomcat:9-jdk8-openjdk
# 清理默认应用
RUN rm -rf /usr/local/tomcat/webapps/*
# 复制 WAR（Tomcat 只读部署）
COPY --from=builder /build/target/huaijiuchangpianshoumai.war /usr/local/tomcat/webapps/huaijiuchangpianshoumai.war
# 创建外部上传目录
RUN mkdir -p /data/upload
# 通过 CATALINA_OPTS 传递上传路径
ENV CATALINA_OPTS="-Dupload.path=/data/upload"
EXPOSE 8080
CMD ["catalina.sh", "run"]
