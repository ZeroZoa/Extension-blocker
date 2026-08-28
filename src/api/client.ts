import type { CustomExtension, FixedExtension, UploadSuccess } from "./types";

const BASE_URL = import.meta.env.VITE_API_BASE_URL;

/**
 * 백엔드의 모든 4xx/5xx 응답은 { message } 형식으로 통일되어 있다(GlobalExceptionHandler).
 * 이 클래스는 그 계약을 프론트 쪽에서 그대로 받아, UI가 항상 error.message 하나만
 * 신경 쓰면 되도록 한다 — 엔드포인트마다 에러 파싱 방식이 갈리는 걸 막기 위함이다.
 */
export class ApiError extends Error {}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, init);

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new ApiError(body?.message ?? "요청 처리 중 오류가 발생했습니다");
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export function getFixedExtensions(): Promise<FixedExtension[]> {
  return request("/api/extensions/fixed");
}

export function patchFixedExtension(extension: string, blocked: boolean): Promise<FixedExtension> {
  return request(`/api/extensions/fixed/${extension}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ blocked }),
  });
}

export function getCustomExtensions(): Promise<CustomExtension[]> {
  return request("/api/extensions/custom");
}

export function addCustomExtension(extension: string): Promise<CustomExtension> {
  return request("/api/extensions/custom", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ extension }),
  });
}

export function deleteCustomExtension(id: number): Promise<void> {
  return request(`/api/extensions/custom/${id}`, { method: "DELETE" });
}

export function uploadFile(file: File): Promise<UploadSuccess> {
  const formData = new FormData();
  formData.append("file", file);
  return request("/api/files/upload", { method: "POST", body: formData });
}
