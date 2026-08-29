import { useRef, useState } from "react";
import { ApiError, uploadFile } from "../api/client";
import type { UploadSuccess } from "../api/types";

export function FileUploadForm() {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [result, setResult] = useState<UploadSuccess | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    const file = fileInputRef.current?.files?.[0];
    if (!file) return;

    setUploading(true);
    setResult(null);
    setError(null);
    try {
      const response = await uploadFile(file);
      setResult(response);
      if (fileInputRef.current) fileInputRef.current.value = "";
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "업로드 중 오류가 발생했습니다");
    } finally {
      setUploading(false);
    }
  }

  return (
    <fieldset>
      <legend>파일 업로드</legend>
      <form className="add-row" onSubmit={handleSubmit}>
        <input type="file" ref={fileInputRef} aria-label="업로드할 파일" required />
        <button type="submit" disabled={uploading}>
          {uploading ? "업로드 중..." : "업로드"}
        </button>
      </form>
      {result && (
        <p role="status">
          업로드 성공: {result.originalFilename} → {result.storedFilename} (.{result.extension})
        </p>
      )}
      {error && <p role="alert">{error}</p>}
    </fieldset>
  );
}
