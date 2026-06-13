FROM tomcat:9-jdk8

# 删除默认 webapps 内容，保持只读 WAR 部署
RUN rm -rf /usr/local/tomcat/webapps/*

# 复制 prod profile 构建的 WAR
COPY target/huaijiuchangpianshoumai.war /usr/local/tomcat/webapps/huaijiuchangpianshoumai.war

# 创建外部上传目录（运行时由 Docker volume 挂载覆盖）
RUN mkdir -p /data/upload

# 入口脚本：启动前将 upload volume 软链到 webapp 内部
COPY docker-entrypoint.sh /usr/local/tomcat/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/tomcat/bin/docker-entrypoint.sh

ENTRYPOINT ["/usr/local/tomcat/bin/docker-entrypoint.sh"]
CMD ["catalina.sh", "run"]
