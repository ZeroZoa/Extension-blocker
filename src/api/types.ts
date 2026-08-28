// 백엔드 DTO와 1:1로 대응한다. 필드가 바뀌면 여기부터 타입 에러가 나서 알 수 있게
// 하기 위해 백엔드 응답 형식을 그대로 옮겨 적었다.

export interface FixedExtension {
  extension: string;
  blocked: boolean;
}

export interface CustomExtension {
  id: number;
  extension: string;
}

export interface UploadSuccess {
  storedFilename: string;
  originalFilename: string;
  extension: string;
}
