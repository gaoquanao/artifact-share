import Editor from '@monaco-editor/react';
import { useEffect } from 'react';

interface Props {
  path: string | null;
  content: string;
  dirty: boolean;
  saving: boolean;
  onChange: (value: string) => void;
  onSave: () => void;
}

const LANGUAGES: Record<string, string> = {
  html: 'html',
  htm: 'html',
  css: 'css',
  js: 'javascript',
  jsx: 'javascript',
  mjs: 'javascript',
  cjs: 'javascript',
  ts: 'typescript',
  tsx: 'typescript',
  json: 'json',
  md: 'markdown',
  svg: 'xml',
  xml: 'xml',
  yml: 'yaml',
  yaml: 'yaml',
};

function languageOf(path: string): string {
  const ext = path.includes('.') ? (path.split('.').pop() ?? '') : '';
  return LANGUAGES[ext.toLowerCase()] ?? 'plaintext';
}

export default function EditorPane({ path, content, dirty, saving, onChange, onSave }: Props) {
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 's') {
        e.preventDefault();
        if (dirty && !saving) onSave();
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [dirty, saving, onSave]);

  if (!path) {
    return <div className="editor-empty">从左侧选择文件开始编辑;生成完成后会自动打开 index.html</div>;
  }
  return (
    <div className="editor-pane">
      <div className="editor-bar">
        <span className="editor-path">
          {path}
          {dirty && ' *'}
        </span>
        <button className="primary small" disabled={!dirty || saving} onClick={onSave}>
          {saving ? '保存中…' : '保存 (⌘S)'}
        </button>
      </div>
      <Editor
        height="calc(100% - 42px)"
        theme="vs-dark"
        language={languageOf(path)}
        value={content}
        onChange={(v) => onChange(v ?? '')}
        options={{ fontSize: 13, minimap: { enabled: false }, tabSize: 2, automaticLayout: true }}
      />
    </div>
  );
}
