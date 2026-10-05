interface Props {
  src: string;
  onRefresh: () => void;
}

export default function PreviewPane({ src, onRefresh }: Props) {
  return (
    <div className="preview-pane">
      <div className="preview-bar">
        <span>预览 · 修改代码后点「刷新」查看效果(NPM 工程需先构建)</span>
        <button className="ghost small" onClick={onRefresh}>
          刷新
        </button>
      </div>
      <iframe key={src} title="preview" src={src} sandbox="allow-scripts allow-forms allow-popups" />
    </div>
  );
}
