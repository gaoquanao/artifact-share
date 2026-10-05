import type { FileEntry } from '../types';

interface Props {
  files: FileEntry[];
  active: string | null;
  onOpen: (path: string) => void;
}

function sizeLabel(size: number): string {
  return size < 1024 ? `${size}B` : `${(size / 1024).toFixed(1)}K`;
}

export default function FileTree({ files, active, onOpen }: Props) {
  return (
    <div className="file-tree">
      {files.length === 0 && <div className="tree-empty">暂无文件</div>}
      {files.map((f) => {
        const depth = f.path.split('/').length - 1;
        return (
          <button
            key={f.path}
            className={`tree-item ${active === f.path ? 'active' : ''}`}
            style={{ paddingLeft: 10 + depth * 14 }}
            onClick={() => onOpen(f.path)}
            title={f.path}
          >
            <span className="tree-name">{f.path.split('/').pop()}</span>
            <span className="tree-size">{sizeLabel(f.size)}</span>
          </button>
        );
      })}
    </div>
  );
}
