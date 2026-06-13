#!/bin/bash
set -e

# ─────────────────────────────────────────────────────────
# docker-entrypoint.sh
# 用途：Tomcat 启动前将外部 upload volume 软链到 webapp 内部，
#       使 web.xml 中 /upload/* → default servlet 的静态文件映射继续生效。
#       WAR 本身保持只读，上传数据持久化在 Docker named volume 中。
# ─────────────────────────────────────────────────────────

WEBAPP_DIR=/usr/local/tomcat/webapps/huaijiuchangpianshoumai

# Tomcat 首次访问时会自动解压 WAR；这里提前解压以确保软链目标目录存在
if [ ! -d "$WEBAPP_DIR" ] && [ -f "$WEBAPP_DIR.war" ]; then
    mkdir -p "$WEBAPP_DIR"
    cd "$WEBAPP_DIR"
    jar xf "$WEBAPP_DIR.war"
fi

# 将外部 upload volume (/data/upload) 软链到 webapp 内部的 upload 目录
# 这样 web.xml 中 <url-pattern>/upload/*</url-pattern> 映射到 default servlet
# 的静态文件服务继续正常工作，无需修改 web.xml
if [ -d "/data/upload" ]; then
    rm -rf "$WEBAPP_DIR/upload"
    ln -sfn /data/upload "$WEBAPP_DIR/upload"
    echo "[entrypoint] upload volume symlinked: /data/upload -> $WEBAPP_DIR/upload"
fi

# 执行传入的命令（默认 catalina.sh run）
exec "$@"
