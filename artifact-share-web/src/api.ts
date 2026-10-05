import type {
  ArtifactView,
  BuildResult,
  FileEntry,
  GenerateResult,
  ModelsInfo,
  ProjectView,
} from './types';

async function json<T>(r: Response): Promise<T> {
  if (r.ok) return (await r.json()) as T;
  let detail = r.statusText;
  try {
    const body = (await r.json()) as { detail?: string };
    if (body && typeof body.detail === 'string') detail = body.detail;
  } catch {
    // 非 JSON 错误体,保留 statusText
  }
  throw new Error(detail);
}

async function get<T>(url: string): Promise<T> {
  return json<T>(await fetch(url));
}

async function post<T>(url: string, body?: unknown): Promise<T> {
  const r = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  return json<T>(r);
}

export function fileUrl(id: string, path: string): string {
  return `/api/v1/projects/${id}/files/${path.split('/').map(encodeURIComponent).join('/')}`;
}

export const api = {
  models: () => get<ModelsInfo>('/api/v1/models'),

  listProjects: () => get<ProjectView[]>('/api/v1/projects'),

  createProject: (name: string) => post<ProjectView>('/api/v1/projects', { name }),

  project: (id: string) => get<ProjectView>(`/api/v1/projects/${id}`),

  generate: (id: string, prompt: string, model: string, images: File[]) => {
    const fd = new FormData();
    fd.append('prompt', prompt);
    if (model) fd.append('model', model);
    images.forEach((f) => fd.append('images', f));
    return fetch(`/api/v1/projects/${id}/generate`, { method: 'POST', body: fd }).then((r) =>
      json<GenerateResult>(r),
    );
  },

  build: (id: string) => post<BuildResult>(`/api/v1/projects/${id}/build`),

  files: (id: string) => get<FileEntry[]>(`/api/v1/projects/${id}/files`),

  readFile: async (id: string, path: string): Promise<string> => {
    const r = await fetch(fileUrl(id, path));
    if (!r.ok) throw new Error(`读取文件失败(HTTP ${r.status})`);
    return r.text();
  },

  saveFile: async (id: string, path: string, content: string): Promise<void> => {
    const r = await fetch(fileUrl(id, path), {
      method: 'PUT',
      headers: { 'Content-Type': 'text/plain; charset=utf-8' },
      body: content,
    });
    if (!r.ok) throw new Error(`保存失败(HTTP ${r.status})`);
  },

  publish: (id: string, slug: string, title: string) =>
    post<ArtifactView>(`/api/v1/projects/${id}/publish`, {
      slug: slug || undefined,
      title: title || undefined,
    }),
};

export function previewUrl(id: string, bust: number): string {
  return `/api/v1/projects/${id}/preview/?t=${bust}`;
}
