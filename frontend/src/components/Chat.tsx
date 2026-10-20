import { FormEvent, ReactNode, useEffect, useRef } from 'react';
import myFamDoctorLogo from '../logo.png';
import { Message } from '../types';

type ChatProps = {
  messages: Message[];
  question: string;
  isAsking: boolean;
  isUploading: boolean;
  setQuestion: (value: string) => void;
  onSubmit: (event: FormEvent) => void;
  onAddReport: () => void;
  uploadedFileName: string;
  isGuestMode?: boolean;
};

function formatInline(text: string): ReactNode[] {
  return text.split(/(\*\*[^*]+\*\*)/g).filter(Boolean).map((part, index) => {
    if (part.startsWith('**') && part.endsWith('**')) {
      return <strong key={index}>{part.slice(2, -2)}</strong>;
    }
    return part;
  });
}

function formatAssistantMessage(content: string): ReactNode[] {
  const blocks: ReactNode[] = [];
  const lines = content.replace(/\r/g, '').split('\n');
  let paragraph: string[] = [];
  let listItems: { text: string; ordered: boolean }[] = [];

  const flushParagraph = () => {
    if (paragraph.length) {
      blocks.push(<p key={`p-${blocks.length}`}>{formatInline(paragraph.join(' '))}</p>);
      paragraph = [];
    }
  };
  const flushList = () => {
    if (!listItems.length) return;
    const ordered = listItems[0].ordered;
    const List = ordered ? 'ol' : 'ul';
    blocks.push(<List key={`list-${blocks.length}`}>{listItems.map((item, index) => <li key={index}>{formatInline(item.text)}</li>)}</List>);
    listItems = [];
  };

  lines.forEach((rawLine) => {
    const line = rawLine.trim();
    const listMatch = line.match(/^([-*•])\s+(.+)$/);
    const orderedMatch = line.match(/^\d+[.)]\s+(.+)$/);
    const headingMatch = line.match(/^#{1,4}\s+(.+)$/) || line.match(/^\*\*(.+?)\*\*:?$/);

    if (!line) {
      flushParagraph();
      flushList();
    } else if (listMatch || orderedMatch) {
      flushParagraph();
      const ordered = Boolean(orderedMatch);
      if (listItems.length && listItems[0].ordered !== ordered) flushList();
      listItems.push({ text: (listMatch?.[2] ?? orderedMatch?.[1] ?? '').trim(), ordered });
    } else if (headingMatch) {
      flushParagraph();
      flushList();
      blocks.push(<h3 key={`heading-${blocks.length}`}>{formatInline(headingMatch[1].replace(/:$/, ''))}</h3>);
    } else {
      flushList();
      paragraph.push(line);
    }
  });

  flushParagraph();
  flushList();
  return blocks.length ? blocks : [content];
}

export function Chat({ messages, question, isAsking, isUploading, setQuestion, onSubmit, onAddReport, uploadedFileName, isGuestMode = false }: ChatProps) {
  const messagesRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!messagesRef.current) return;
    messagesRef.current.scrollTo({
      top: messagesRef.current.scrollHeight,
      behavior: 'smooth',
    });
  }, [messages, isAsking]);

  return (
    <aside className="chat card">
      <div className="chat-head">
        <div className="bot" aria-hidden="true">
          <img src={myFamDoctorLogo} alt="MyFamDoctor" className="chat-head-logo" />
        </div>
        <div><h2>Ask about Your Health</h2><p>Uses your saved health timeline</p></div>
      </div>
      <div className="notice">For education only — not a diagnosis. Contact emergency services for urgent symptoms.</div>
      <div ref={messagesRef} className="messages" aria-live="polite" aria-busy={isAsking}>
        {messages.map((message) => (
          <div key={message.id} className={`message ${message.role}`}>
            {message.role === 'assistant' ? formatAssistantMessage(message.content) : message.content}
          </div>
        ))}
        {isAsking && <div className="thinking" role="status"><span className="thinking-spinner" aria-hidden="true" />The assistant is thinking…</div>}
      </div>
      <form onSubmit={onSubmit} className="chat-form">
        {isGuestMode && (
          <button type="button" className="chat-upload-button" aria-label="Add PDF" onClick={onAddReport} title="Add PDF" disabled={isUploading}>
            {isUploading ? <span className="send-spinner" aria-hidden="true" /> : <span aria-hidden="true">＋</span>}
          </button>
        )}
        <input value={question} onChange={(event) => setQuestion(event.target.value)} placeholder={isAsking ? 'Waiting for the assistant…' : 'Ask about Your Health'} disabled={isAsking || isUploading} />
        <button type="submit" aria-label="Send" disabled={isAsking || isUploading} className={isAsking ? 'loading' : ''}>
          {isAsking ? <span className="send-spinner" aria-hidden="true" /> : '→'}
        </button>
      </form>
      {uploadedFileName && <div className="upload-status">PDF added: {uploadedFileName}</div>}
      {isUploading && <div className="upload-status">Uploading report…</div>}
      <p className="privacy">🔒 Your reports stay in this browser.</p>
    </aside>
  );
}
