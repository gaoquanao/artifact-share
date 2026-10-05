export interface FileEntry {
  path: string;
  size: number;
}

export interface ChatMessage {
  role: string;
  content: string;
}

export interface ModelsInfo {
  enabled: boolean;
  models: string[];
  defaultModel: string;
}

export interface ProjectView {
  id: string;
  name: string;
  model: string | null;
  type: string;
  status: string;
  files: FileEntry[];
  chat: ChatMessage[];
  publishedSlug: string | null;
  publishedUrl: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface GenerateResult {
  summary: string;
  files: string[];
  warnings: string[];
  chat: ChatMessage[];
}

export interface BuildResult {
  ok: boolean;
  type: string;
  output: string;
  hint: string | null;
}

export interface ArtifactView {
  slug: string;
  title: string | null;
  publicUrl: string;
  entryObjectKey: string;
  sizeBytes: number;
  fileCount: number;
  status: string;
  publishedAt: string;
}
