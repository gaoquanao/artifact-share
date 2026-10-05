import { useRef, useState } from 'react';
import type { ChatMessage } from '../types';

interface ImageItem {
  file: File;
  url: string;
}

interface Props {
  chat: ChatMessage[];
  busy: boolean;
  disabled?: boolean;
  onSend: (prompt: string, images: File[]) => void;
}

export default function ChatPanel({ chat, busy, disabled, onSend }: Props) {
  const [prompt, setPrompt] = useState('');
  const [images, setImages] = useState<ImageItem[]>([]);
  const fileRef = useRef<HTMLInputElement>(null);

  const submit = () => {
    if (!prompt.trim() || busy || disabled) return;
    onSend(prompt.trim(), images.map((i) => i.file));
    setPrompt('');
    setImages([]);
    if (fileRef.current) fileRef.current.value = '';
  };

  const removeImage = (index: number) => {
    URL.revokeObjectURL(images[index].url);
    setImages(images.filter((_, i) => i !== index));
  };

  return (
    <div className="chat-panel">
      <div className="chat-list">
        {chat.length === 0 && !busy && (
          <div className="chat-empty">
            描述你想生成的网页,例如:
            <em>「做一个深色风格的 Q3 数据看板,含 KPI 卡片和折线图」</em>
            。可附上设计参考图。
          </div>
        )}
        {chat.map((m, i) => (
          <div key={i} className={`chat-msg ${m.role}`}>
            <b>{m.role === 'user' ? '你' : '助手'}</b>
            <p>{m.content}</p>
          </div>
        ))}
        {busy && (
          <div className="chat-msg assistant">
            <b>助手</b>
            <p>生成中,大模型正在编写代码…(通常需要 1~3 分钟)</p>
          </div>
        )}
      </div>
      <div className="chat-input">
        {images.length > 0 && (
          <div className="attach-row">
            {images.map((item, i) => (
              <span key={i} className="chip">
                <img src={item.url} alt={item.file.name} />
                <span className="chip-name">{item.file.name}</span>
                <button onClick={() => removeImage(i)}>×</button>
              </span>
            ))}
          </div>
        )}
        <textarea
          value={prompt}
          placeholder={disabled ? '先选择或新建项目' : '描述需求或继续修改…(Cmd/Ctrl+Enter 发送)'}
          disabled={disabled || busy}
          onChange={(e) => setPrompt(e.target.value)}
          onKeyDown={(e) => {
            if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') submit();
          }}
        />
        <div className="chat-actions">
          <input
            ref={fileRef}
            type="file"
            accept="image/png,image/jpeg,image/gif,image/webp"
            multiple
            hidden
            onChange={(e) => {
              const files = Array.from(e.target.files ?? []);
              setImages([...images, ...files.map((file) => ({ file, url: URL.createObjectURL(file) }))]);
            }}
          />
          <button className="ghost" disabled={busy || disabled} onClick={() => fileRef.current?.click()}>
            + 图片
          </button>
          <button className="primary" disabled={busy || disabled || !prompt.trim()} onClick={submit}>
            {chat.length === 0 ? '生成' : '继续修改'}
          </button>
        </div>
      </div>
    </div>
  );
}
