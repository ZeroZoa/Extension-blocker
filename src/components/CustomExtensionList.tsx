import { useEffect, useState } from "react";
import { ApiError, addCustomExtension, deleteCustomExtension, getCustomExtensions } from "../api/client";
import type { CustomExtension } from "../api/types";

const MAX_COUNT = 200;
const MAX_LENGTH = 20;

export function CustomExtensionList() {
  const [extensions, setExtensions] = useState<CustomExtension[]>([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  useEffect(() => {
    getCustomExtensions()
      .then(setExtensions)
      .catch(() => setLoadError("커스텀 확장자 목록을 불러오지 못했습니다"))
      .finally(() => setLoading(false));
  }, []);

  async function handleAdd() {
    setActionError(null);
    try {
      // 최종 판단(trim, 대소문자, 문자셋, 중복, 상한)은 전부 서버가 한다. 여기서는
      // 서버 왕복 없이 즉시 알 수 있는 실수(빈 입력)만 최소한으로 걸러 UX를 돕는다.
      const created = await addCustomExtension(input.trim());
      setExtensions((prev) => [...prev, created]);
      setInput("");
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : "추가에 실패했습니다");
    }
  }

  async function handleDelete(id: number, extension: string) {
    setActionError(null);
    const previous = extensions;
    setExtensions((prev) => prev.filter((e) => e.id !== id));
    try {
      await deleteCustomExtension(id);
    } catch {
      setExtensions(previous);
      setActionError(`${extension} 삭제에 실패했습니다`);
    }
  }

  if (loading) return <p>불러오는 중...</p>;
  if (loadError) return <p role="alert">{loadError}</p>;

  return (
    <fieldset>
      <legend>커스텀 확장자</legend>
      <div className="add-row">
        <input
          type="text"
          value={input}
          maxLength={MAX_LENGTH}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && handleAdd()}
          placeholder="확장자를 입력해주세요."
          aria-label="커스텀 확장자 입력"
        />
        <button type="button" onClick={handleAdd} disabled={!input.trim()}>
          추가
        </button>
      </div>
      <p className="count">
        {extensions.length}/{MAX_COUNT}
      </p>
      {actionError && <p role="alert">{actionError}</p>}
      <ul>
        {extensions.map((e) => (
          <li key={e.id}>
            {e.extension}
            <button
              type="button"
              className="delete-button"
              onClick={() => handleDelete(e.id, e.extension)}
              aria-label={`${e.extension} 삭제`}
            >
              ✕
            </button>
          </li>
        ))}
      </ul>
    </fieldset>
  );
}
