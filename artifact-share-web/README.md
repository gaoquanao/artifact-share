# artifact-share-web —— 网页生成工作台(前端)

Vite + React + TypeScript + Monaco Editor 的单页应用,配合后端 `artifact-share` 的
工作台 API 实现:**模型选择 → 文本/图片输入生成网页 → 文件编辑 → npm 构建 → 预览 → 一键发布到 slug 域名系统**。

## 页面结构

```
┌────────────────────────────── 顶栏 ──────────────────────────────┐
│ ⚡ Artifact Studio   [模型▾] [项目▾] [+新建]   已发布链接 [构建][发布] │
├───────────────┬──────────────────────────────────────────────────┤
│ 聊天/生成面板  │  tabs:代码 | 预览                     项目状态       │
│  对话消息      │  ┌ 文件树 ┬ Monaco 编辑器(⌘S 保存)┐             │
│  文本+图片输入 │  │        │                        │             │
│               │  └────────┴────────────────────────┘             │
│               │  [构建日志(可折叠)]                               │
└───────────────┴──────────────────────────────────────────────────┘
```

## 本地开发

```bash
# 先启动后端(artifact-share 目录):mvn spring-boot:run
npm install
npm run dev        # http://localhost:5173,/api 代理到 localhost:8080
npm run build      # 产物在 dist/
```

前置条件:后端已配置 `share.agent.enabled=true` 与 `LLM_API_KEY`(生成功能);
构建/编辑/预览/发布不依赖 LLM 配置。

## 部署

`npm run build` 后的 `dist/` 是纯静态文件,两种接法:

1. **Nginx 托管**(与 API 同域,免跨域):

```nginx
server {
    listen 80;
    server_name studio.example.com;
    root /opt/artifact-share-web/dist;
    location / { try_files $uri /index.html; }
    location /api/ { proxy_pass http://127.0.0.1:8080; proxy_set_header Host $host; }
}
```

2. **吃自己的狗粮**:把 `dist/` 用发布 API 打包发布成 `https://studio.s.example.com/`
   —— 但注意工作台页面需要访问 `/api`,发布后的静态站点没有后端,适合纯演示;
   正式使用请走 Nginx 方案(或给发布 API 域名单独反代)。

## 说明

- Monaco 编辑器默认从 CDN 加载内核;内网/离线环境用 `loader.config({ paths: { vs: ... } })`
  指向自托管资源(`@monaco-editor/react` 文档)。
- 生成接口为同步等待(1~3 分钟),面板上有进行中提示;前端不做流式渲染。
- 上传图片在生成时保存进项目 `assets/` 目录,模型被明确要求用相对路径引用它们。
