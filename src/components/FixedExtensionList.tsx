import { useEffect, useState } from "react";
import { getFixedExtensions, patchFixedExtension } from "../api/client";
import type { FixedExtension } from "../api/types";

export function FixedExtensionList() {
  const [extensions, setExtensions] = useState<FixedExtension[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [toggleError, setToggleError] = useState<string | null>(null);

  useEffect(() => {
    getFixedExtensions()
      .then(setExtensions)
      .catch(() => setLoadError("고정 확장자 목록을 불러오지 못했습니다"))
      .finally(() => setLoading(false));
  }, []);

  async function toggle(extension: string, nextBlocked: boolean) {
    setToggleError(null);
    // 낙관적 업데이트: 서버 응답을 기다리지 않고 즉시 화면에 반영한다. 실패하면
    // 원래 상태로 되돌리고 에러 메시지를 보여준다 — 체크박스처럼 잦은 토글 조작은
    // 매번 로딩 스피너를 보여주는 것보다 이쪽이 자연스럽다.
    setExtensions((prev) =>
      prev.map((e) => (e.extension === extension ? { ...e, blocked: nextBlocked } : e)),
    );
    try {
      await patchFixedExtension(extension, nextBlocked);
    } catch {
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
