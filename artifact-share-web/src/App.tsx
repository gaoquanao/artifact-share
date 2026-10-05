import { useCallback, useEffect, useState } from 'react';
import { api, previewUrl } from './api';
import type { BuildResult, ModelsInfo, ProjectView } from './types';
import ChatPanel from './components/ChatPanel';
import EditorPane from './components/EditorPane';
import FileTree from './components/FileTree';
import ModelPicker from './components/ModelPicker';
import PreviewPane from './components/PreviewPane';
import PublishDialog from './components/PublishDialog';

function errText(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

export default function App() {
  const [modelsInfo, setModelsInfo] = useState<ModelsInfo | null>(null);
  const [model, setModel] = useState('');
  const [projects, setProjects] = useState<ProjectView[]>([]);
  const [project, setProject] = useState<ProjectView | null>(null);
  const [tab, setTab] = useState<'code' | 'preview'>('code');
  const [openPath, setOpenPath] = useState<string | null>(null);
  const [content, setContent] = useState('');
  const [dirty, setDirty] = useState(false);
  const [saving, setSaving] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [building, setBuilding] = useState(false);
  const [buildResult, setBuildResult] = useState<BuildResult | null>(null);
  const [publishing, setPublishing] = useState(false);
  const [showPublish, setShowPublish] = useState(false);
  const [previewBust, setPreviewBust] = useState(() => Date.now());
  const [error, setError] = useState('');

  const refreshProject = useCallback(async (id: string) => {
    const p = await api.project(id);
    setProject(p);
    return p;
  }, []);

  useEffect(() => {
    api.models()
      .then((m) => {
        setModelsInfo(m);
        setModel(m.defaultModel || m.models[0] || '');
      })
      .catch(() => undefined);
    api.listProjects().then(setProjects).catch(() => undefined);
  }, []);

  const openProject = async (id: string) => {
    setError('');
    try {
      const p = await refreshProject(id);
      setBuildResult(null);
      setTab('code');
      setOpenPath(p.files.find((f) => f.path === 'index.html')?.path ?? p.files[0]?.path ?? null);
      if (openPath) setContent(await api.readFile(p.id, openPath).catch(() => ''));
      setDirty(false);
      setPreviewBust(Date.now());
    } catch (e) {
      setError(errText(e));
    }
  };

  const openFile = async (path: string) => {
    if (!project) return;
    try {
      setContent(await api.readFile(project.id, path));
      setOpenPath(path);
      setDirty(false);
    } catch (e) {
      setError(errText(e));
    }
  };

  const generate = async (prompt: string, images: File[]) => {
    if (!project) return;
    setGenerating(true);
    setError('');
    try {
      await api.generate(project.id, prompt, model, images);
      const p = await refreshProject(project.id);
      setProjects(await api.listProjects());
      setPreviewBust(Date.now());
      const next = openPath ?? p.files.find((f) => f.path === 'index.html')?.path ?? null;
      setOpenPath(next);
      if (next) {
        try {
          setContent(await api.readFile(p.id, next));
          setDirty(false);
        } catch {
          // 原打开文件可能已被删除
        }
      }
    } catch (e) {
      setError(errText(e));
    } finally {
      setGenerating(false);
    }
  };

  const runBuild = async () => {
    if (!project) return;
    setBuilding(true);
    setError('');
    try {
      const r = await api.build(project.id);
      setBuildResult(r);
      await refreshProject(project.id);
      setPreviewBust(Date.now());
    } catch (e) {
      setError(errText(e));
    } finally {
      setBuilding(false);
    }
  };

  const saveFile = async () => {
    if (!project || !openPath) return;
    setSaving(true);
    try {
      await api.saveFile(project.id, openPath, content);
      setDirty(false);
      setPreviewBust(Date.now());
    } catch (e) {
      setError(errText(e));
    } finally {
      setSaving(false);
    }
  };

  const doPublish = async (slug: string, title: string) => {
    if (!project) throw new Error('未选择项目');
    const result = await api.publish(project.id, slug, title);
    await refreshProject(project.id);
    setShowPublish(false);
    return result;
  };

  const newProject = async () => {
    const name = window.prompt('项目名称', '未命名项目');
    if (name === null) return;
    try {
      const p = await api.createProject(name.trim() || '未命名项目');
      setProjects(await api.listProjects());
      setProject(p);
      setOpenPath(null);
      setContent('');
      setDirty(false);
      setBuildResult(null);
      setPreviewBust(Date.now());
    } catch (e) {
      setError(errText(e));
    }
  };

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          ⚡ Artifact Studio
          <span className="muted">网页生成工作台</span>
        </div>
        <ModelPicker
          models={modelsInfo?.models ?? []}
          value={model}
          onChange={setModel}
          disabled={generating}
        />
        <select
          className="project-select"
          value={project?.id ?? ''}
          onChange={(e) => {
            if (e.target.value) void openProject(e.target.value);
          }}
        >
          <option value="" disabled>
            选择项目…
          </option>
          {projects.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </select>
        <button className="ghost" onClick={() => void newProject()}>
          + 新建
        </button>
        <div className="spacer" />
        {project?.publishedUrl && (
          <a className="published-link" href={project.publishedUrl} target="_blank" rel="noreferrer">
            {project.publishedUrl.replace(/^https?:\/\//, '')}
          </a>
        )}
        <button className="ghost" disabled={!project || generating || building} onClick={() => void runBuild()}>
          {building ? '构建中…' : '构建'}
        </button>
        <button className="primary" disabled={!project || generating || building} onClick={() => setShowPublish(true)}>
          发布
        </button>
      </header>

      {error && (
        <div className="error-bar">
          <span>{error}</span>
          <button onClick={() => setError('')}>×</button>
        </div>
      )}

      <div className="main">
        <aside className="left">
          <ChatPanel chat={project?.chat ?? []} busy={generating} disabled={!project} onSend={(p, f) => void generate(p, f)} />
        </aside>
        <section className="right">
          <div className="tabs">
            <button className={tab === 'code' ? 'active' : ''} onClick={() => setTab('code')}>
              代码
            </button>
            <button className={tab === 'preview' ? 'active' : ''} onClick={() => setTab('preview')}>
              预览
            </button>
            {project && (
              <span className="status-chip">
                {project.type} · {project.status}
              </span>
            )}
          </div>
          {tab === 'code' ? (
            <div className="code-layout">
              <FileTree files={project?.files ?? []} active={openPath} onOpen={(p) => void openFile(p)} />
              <EditorPane
                path={openPath}
                content={content}
                dirty={dirty}
                saving={saving}
                onChange={(v) => {
                  setContent(v);
                  setDirty(true);
                }}
                onSave={() => void saveFile()}
              />
            </div>
          ) : (
            <PreviewPane
              src={project ? previewUrl(project.id, previewBust) : 'about:blank'}
              onRefresh={() => setPreviewBust(Date.now())}
            />
          )}
          {buildResult && (
            <details className="build-log" open={!buildResult.ok}>
              <summary>
                构建日志({buildResult.ok ? '成功' : '失败'} · {buildResult.type})
              </summary>
              <pre>{buildResult.output}</pre>
              {buildResult.hint && <p className="error-text">{buildResult.hint}</p>}
            </details>
          )}
        </section>
      </div>

      {showPublish && project && (
        <PublishDialog
          publishedUrl={project.publishedUrl}
          busy={publishing}
          onClose={() => setShowPublish(false)}
          onPublish={async (slug, title) => {
            setPublishing(true);
            try {
              return await doPublish(slug, title);
            } finally {
              setPublishing(false);
            }
          }}
        />
      )}
    </div>
  );
}
