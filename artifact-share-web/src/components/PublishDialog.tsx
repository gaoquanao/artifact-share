import { useState } from 'react';
import type { ArtifactView } from '../types';

interface Props {
  publishedUrl: string | null;
  busy: boolean;
  onClose: () => void;
  onPublish: (slug: string, title: string) => Promise<ArtifactView>;
}

export default function PublishDialog({ publishedUrl, busy, onClose, onPublish }: Props) {
  const [slug, setSlug] = useState('');
  const [title, setTitle] = useState('');
  const [result, setResult] = useState<ArtifactView | null>(null);
  const [error, setError] = useState('');

  const publish = async () => {
    setError('');
    try {
      setResult(await onPublish(slug, title));
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  };

  return (
    <div className="modal-mask" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>发布到线上</h3>
        <p className="muted">
          复用 slug 域名系统:产物上传 OSS,通过 CDN 从
          <code>https://{'{slug}'}.&lt;泛域名&gt;/</code>
          访问。留空 slug 则按项目名自动生成「可读前缀 + 哈希」的域名。
        </p>
        {result ? (
          <div className="publish-result">
            <p className="ok">发布成功 🎉</p>
            <a href={result.publicUrl} target="_blank" rel="noreferrer">
              {result.publicUrl}
            </a>
            <p className="muted">
              slug: {result.slug} · {result.fileCount} 个文件 · {(result.sizeBytes / 1024).toFixed(1)}K
            </p>
            <div className="modal-actions">
              <button
                className="primary"
                onClick={() => void navigator.clipboard.writeText(result.publicUrl)}
              >
                复制链接
              </button>
              <button className="ghost" onClick={onClose}>
                完成
              </button>
            </div>
          </div>
        ) : (
          <>
            <label>
              自定义 slug(可选,小写字母/数字/中划线)
              <input value={slug} onChange={(e) => setSlug(e.target.value)} placeholder="如 q3-dashboard" />
            </label>
            <label>
              标题(可选)
              <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="默认使用项目名" />
            </label>
            {publishedUrl && (
              <p className="muted">
                当前已发布:
                <a href={publishedUrl} target="_blank" rel="noreferrer">
                  {publishedUrl}
                </a>
                (同名 slug 再次发布即覆盖更新)
              </p>
            )}
            {error && <p className="error-text">{error}</p>}
            <div className="modal-actions">
              <button className="ghost" onClick={onClose}>
                取消
              </button>
              <button className="primary" disabled={busy} onClick={() => void publish()}>
                {busy ? '发布中…' : '发布'}
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
