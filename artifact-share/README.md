# artifact-share —— Agent 产物在线分享服务

把 Agent 产出的 HTML / Markdown(连同 CSS、JS、图片等静态资源)一键发布成**在线可访问的分享页**:
每个产物分配一个独立的三级域名 `https://{slug}.s.example.com/`,由 CDN 加速、OSS 承载。

技术栈:**Spring Boot 3(Java 17) + 阿里云 OSS + CDN + Nginx + MySQL/H2**

配套前端工程 [artifact-share-web](../artifact-share-web/README.md):**AI 网页生成工作台**
(模型选择 + 文本/图片输入 → LLM 生成网页工程 → 服务端 npm 构建 → Monaco 编辑 → 预览 → 一键发布到本系统)。

## 架构

```
              发布链路                                   访问链路
┌────────┐  multipart  ┌──────────┐   putObject  ┌─────────────┐
│ Agent  │ ──────────▶ │  Nginx   │ ───────────▶ │     OSS     │
│ /用户  │             │(反代 API)│              │  s/{slug}/* │
└────────┘             └────┬─────┘              └──────▲──────┘
                     ┌──────▼───────┐   预热/刷新        │ 回源
   ┌──────────────┐  │ Spring Boot  │ ─────────▶  ┌─────┴─────┐
   │ artifact-web │◀─│  发布+工作台 │             │    CDN    │◀── DNS 泛解析
   │ Vite+React   │  │  服务+DB     │             └─────▲─────┘   *.s.example.com
   └──────┬───────┘  └──────┬───────┘                   │
          │ prompt+图片     │ LLM(OpenAI 兼容)          │
          └────────────────▶│ 项目工作区 → npm install  │
                            │ → npm run build → dist/   │
                            └───────────────────────────┘
        浏览器访问 https://{slug}.s.example.com/
```

域名如何"分出"三级域名:DNS 对 `*.s.example.com` 做泛解析指向 CDN,CDN/Nginx 按请求的
host 提取 slug、把路径改写回 OSS 的 `s/{slug}/` 前缀。**新产物不需要任何 DNS 操作**,
发布即得域名。

## 快速开始(本地)

```bash
# 1. 准备:开通 OSS,创建 bucket,拿到 AK/SK
export OSS_ACCESS_KEY_ID=xxx
export OSS_ACCESS_KEY_SECRET=yyy

# 2. 改 src/main/resources/application.yml:
#    share.domain   → 你的泛域名后缀(如 s.example.com)
#    share.oss.*    → endpoint / bucket

# 3. 启动(默认本地 H2 文件库,无需装数据库)
mvn spring-boot:run
```

### 发布示例

```bash
# 单个 HTML 文件(自动作为 index.html)
curl -F "files=@report.html" \
     -F "title=周报" \
     http://localhost:8080/api/v1/artifacts

# 整目录(zip,内含 index.html;所有文件在 dist/ 顶层目录下也会自动剥离)
cd dist && zip -r ../site.zip . && cd ..
curl -F "files=@site.zip" http://localhost:8080/api/v1/artifacts

# 多文件 + 指定 slug(同 slug 重复发布 = 覆盖更新)
curl -F "files=@index.html" -F "files=@assets/app.js" -F "slug=demo-report" \
     http://localhost:8080/api/v1/artifacts

# Markdown 自动渲染成带样式的 HTML(index.md / README.md → 首页)
curl -F "files=@README.md" http://localhost:8080/api/v1/artifacts
```

响应(默认 slug = 文件名清洗前缀 + "文件名|时间戳|随机熵" 的短哈希,如 `weekly-report-k3x9m2q7`;
纯中文等无可读字符的文件名退化为纯哈希;指定 `slug` 参数则用自定义值):

```json
{
  "slug": "weekly-report-k3x9m2q7",
  "title": "周报",
  "publicUrl": "https://weekly-report-k3x9m2q7.s.example.com/",
  "entryObjectKey": "s/weekly-report-k3x9m2q7/index.html",
  "sizeBytes": 18234,
  "fileCount": 1,
  "status": "PUBLISHED",
  "publishedAt": "2026-10-04T06:30:00Z",
  "files": [{ "path": "index.html", "contentType": "text/html; charset=utf-8", "size": 18234 }]
}
```

## API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/artifacts` | 发布产物。multipart:`files`(多个文件或一个 zip),可选 `slug`、`title` |
| GET | `/api/v1/artifacts` | 已发布列表 |
| GET | `/api/v1/artifacts/{slug}` | 查询单个 |
| PATCH | `/api/v1/artifacts/{slug}` | 修改分享域名。JSON body:`{"slug": "new-slug"}`;OSS 对象整体迁移到新前缀,旧地址缓存刷新、新地址预热,目标已被占用返回 409 |
| DELETE | `/api/v1/artifacts/{slug}` | 下线:删除 OSS 前缀 + 标记删除 + 刷新 CDN |

```bash
# 修改分享域名:weekly-report-k3x9m2q7 → q3-review
curl -X PATCH -H "Content-Type: application/json" \
     -d '{"slug": "q3-review"}' \
     http://localhost:8080/api/v1/artifacts/weekly-report-k3x9m2q7
```

错误返回 RFC 7807 ProblemDetail(JSON)。

## 网页生成工作台 API

由配套前端 [artifact-share-web](../artifact-share-web/README.md) 使用;需配置 `share.agent.*`
(LLM OpenAI 兼容接口)与服务器安装 Node.js(npm 构建):

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/models` | 模型选择器数据(enabled/models/defaultModel) |
| POST | `/api/v1/projects` | 新建项目 `{name}` |
| GET | `/api/v1/projects` / `/{id}` | 项目列表 / 详情 |
| POST | `/api/v1/projects/{id}/generate` | 生成/迭代:multipart `prompt` + 可选 `model`、`images[]`(图片存入 `assets/` 并作为视觉输入) |
| POST | `/api/v1/projects/{id}/build` | npm 构建(有 package.json:`npm install` + `npm run build` → `dist/`;纯静态直接可用) |
| GET/PUT/DELETE | `/api/v1/projects/{id}/files/{*path}` | 工作区文件读/写/删(文本) |
| GET | `/api/v1/projects/{id}/preview/{*path}` | 预览产物(NPM 工程 = `dist/`,静态 = 工作区) |
| POST | `/api/v1/projects/{id}/publish` | 发布到 slug 域名系统(复用 ArtifactPublishService,默认 slug 取自项目名) |

Agent 约定:模型以严格 JSON 输出 `{summary, files:{相对路径: 完整内容}}`;默认生成纯静态工程
(入口 index.html),用户明确要求时才生成 Vite/npm 工程;多轮对话历史随项目持久化。

## 关键设计

- **对象布局**:OSS 统一存 `s/{slug}/…`,域名与路径的映射由 CDN 边缘脚本(方案一)或
  Nginx rewrite(方案二)完成,见 `deploy/云资源配置指南.md` 与 `deploy/nginx/`。
- **Content-Type 显式设置**:决定浏览器"渲染还是下载",扩展名映射表在 `ContentTypeResolver`。
- **缓存策略**:HTML `no-cache`(重新发布立即生效),静态资源 `max-age=3600`;CDN 开"遵循源站"。
  发布成功异步预热入口 URL,下线时刷新目录(CDN OpenAPI,`share.cdn.enabled` 开关)。
- **防 zip slip / zip bomb**:相对路径清洗拒绝 `..` 穿越;解包时逐条目做数量/总大小限额。
- **slug 规则**:默认 = 主文件名清洗前缀(20 字符内,index/readme 回退父目录名)+
  "文件名|时间戳|随机熵" 的 SHA-256 短哈希 8 位(去易混淆字符),同名文件多次发布得到不同链接;
  自定义 slug 仅小写字母/数字/中划线 + 保留字黑名单,同 slug 再发布即覆盖更新;
  发布后可通过 PATCH 接口改域名(OSS 前缀整体迁移,旧链接缓存刷新后 404)。
- **HTML 必须有入口**:无 `index.html` 时,单文件 html/md 自动作为首页,否则 400。

## 目录结构

```
src/main/java/com/example/artifactshare/
├── ArtifactShareApplication.java
├── config/            # ShareProperties、AgentProperties、OSS 客户端
├── domain/            # Artifact(发布记录)、Project(工作台项目)
├── exception/         # ApiException
├── repository/        # Spring Data JPA
├── service/
│   ├── ArtifactPublishService.java   # 发布核心流程(产物直传 & 工作台共用)
│   ├── OssStorageService.java        # OSS 读写删/前缀迁移
│   ├── SlugGenerator.java            # slug 生成(文件名+时间戳哈希)/校验
│   ├── UploadExtractor.java          # multipart/zip 抽取
│   ├── MarkdownRenderer.java         # MD → HTML(flexmark GFM)
│   ├── CdnRefreshService.java        # CDN 预热/刷新(OpenAPI)
│   ├── AgentService.java             # 网页生成 Agent(LLM 对话/文件落盘/历史)
│   ├── LLMClient.java                # OpenAI 兼容 chat/completions(含视觉输入)
│   ├── ProjectFileService.java       # 工作区文件读写/快照/产物收集
│   ├── BuildService.java             # npm install/build(静态工程免构建)
│   └── ProjectPublishService.java    # 工作台 → 发布链路
├── util/              # 路径校验、Content-Type、LLM JSON 提取
└── web/               # ArtifactController、ProjectController、异常处理
deploy/
├── nginx/share-api.conf        # API 反向代理
├── nginx/share-origin.conf     # 泛域名回源 OSS(方案二完整实现)
├── sql/schema.sql              # MySQL 建表
├── systemd/artifact-share.service
└── 云资源配置指南.md            # DNS/OSS/CDN/证书 一步步配
```

## 生产清单

- [ ] H2 → MySQL(`deploy/sql/schema.sql`),`ddl-auto` 改 `validate`
- [ ] AK/SK 与 LLM Key 走环境变量或 KMS;RAM 最小权限
- [ ] Nginx HTTPS(`deploy/nginx/share-api.conf`)+ 泛域名证书
- [ ] CDN 开启防盗链/限流;内容合规审计(产物对外公开)
- [ ] 发布接口加鉴权(API Key / 登录态),当前为内网演示形态
- [ ] 预热/刷新有每日配额,高频发布时改批量或关闭 `warmup-on-publish`
- [ ] **npm 构建沙箱化**:工作台会执行模型生成的代码(依赖安装脚本),务必部署在
      隔离容器/独立低权限账号下,环境内不要有敏感凭据;Node.js 与 npm 仓库(内网 mirror)提前就绪
- [ ] 前端 `npm run build` 后由 Nginx 托管,`/api` 与后端同域反代
