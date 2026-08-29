import { useEffect, useRef, useState } from "react";
import { getFixedExtensions, patchFixedExtension } from "../api/client";
import type { FixedExtension } from "../api/types";

export function FixedExtensionList() {
  const [extensions, setExtensions] = useState<FixedExtension[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [toggleError, setToggleError] = useState<string | null>(null);
  const latestRequestId = useRef<Map<string, number>>(new Map());

  useEffect(() => {
    getFixedExtensions()
      .then(setExtensions)
      .catch(() => setLoadError("고정 확장자 목록을 불러오지 못했습니다"))
      .finally(() => setLoading(false));
  }, []);

  async function toggle(extension: string, nextBlocked: boolean) {
    setToggleError(null);
    const requestId = (latestRequestId.current.get(extension) ?? 0) + 1;
    latestRequestId.current.set(extension, requestId);
    setExtensions((prev) =>
      prev.map((e) => (e.extension === extension ? { ...e, blocked: nextBlocked } : e)),
    );
    try {
      await patchFixedExtension(extension, nextBlocked);
    } catch {
      if (latestRequestId.current.get(extension) !== requestId) return;
      setExtensions((prev) =>
        prev.map((e) => (e.extension === extension ? { ...e, blocked: !nextBlocked } : e)),
      );
      setToggleError(`${extension} 변경에 실패했습니다`);
    }
  }

  if (loading) return <p>불러오는 중...</p>;
  if (loadError) return <p role="alert">{loadError}</p>;

  return (
    <fieldset>
      <legend>고정 확장자</legend>
      {toggleError && <p role="alert">{toggleError}</p>}
      {extensions.map((e) => (
        <label key={e.extension} className="switch">
          <input
            type="checkbox"
            checked={e.blocked}
            onChange={(event) => toggle(e.extension, event.target.checked)}
          />
          <span className="track" aria-hidden="true" />
          <span className="label">{e.extension}</span>
        </label>
      ))}
    </fieldset>
  );
}
