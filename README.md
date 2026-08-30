# 파일 확장자 차단 시스템

파일 확장자 차단 정책을 관리하고, 실제 파일 업로드 시 그 정책을 강제하는 시스템입니다. Spring Boot 백엔드와 React 프론트엔드로 구성된 모노레포입니다.

- **배포된 사이트**: _(배포 후 URL 기입 예정)_
- **고려사항 문서**: [CONSIDERATIONS.md](CONSIDERATIONS.md)
- **AI 활용 기록**: [PROMPT_LOG.md](PROMPT_LOG.md)

---

## 목차

- [기술 스택](#기술-스택)
- [프로젝트 구조](#프로젝트-구조)
- [Table Schema](#table-schema)
- [API 명세](#api-명세)
- [실행 방법](#실행-방법)
- [배포](#배포)

---

## 기술 스택

| 구분 | 스택 |
|---|---|
| Backend | Java 21, Spring Boot 4.1.1, Spring Data JPA, Bean Validation, Gradle |
| Frontend | React, TypeScript, Vite |
| DB | PostgreSQL 16 |
| 배포(예정) | Render(백엔드) + Vercel(프론트엔드) |

---

## 프로젝트 구조

백엔드와 프론트엔드를 하나의 저장소(모노레포)로 관리합니다. **백엔드가 저장소 루트**이고, **프론트엔드는 `frontend/` 하위 디렉터리**입니다.

```
.
├── src/                        # 백엔드 소스 (Spring Boot)
│   ├── main/java/com/feb/extension_blocker/
│   │   ├── common/             # 공통 설정, 전역 예외 처리
│   │   ├── extension/          # 확장자 정책 관리 도메인
│   │   └── upload/             # 파일 업로드 검증/저장 도메인
│   └── main/resources/
│       ├── schema.sql          # 테이블 정의
│       ├── data.sql            # 초기 데이터(고정 확장자 7개)
│       └── application.yaml
├── frontend/                   # 프론트엔드 소스 (React + Vite)
│   └── src/
│       ├── api/                # 백엔드 API 클라이언트
│       └── components/         # 정책 관리 / 업로드 UI
├── Dockerfile                  # 백엔드 배포용 멀티스테이지 빌드
├── CONSIDERATIONS.md
└── PROMPT_LOG.md
```

---

## Table Schema

PostgreSQL 기준, `src/main/resources/schema.sql`에 정의되어 있습니다.

### `extension_policy` — 확장자 차단 정책

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | `PRIMARY KEY` | 식별자 |
| `extension` | `VARCHAR(20)` | `NOT NULL` | 확장자 문자열(소문자로 정규화되어 저장) |
| `type` | `VARCHAR(10)` | `NOT NULL` | `FIXED`(고정 7종) / `CUSTOM`(사용자 추가) |
| `is_blocked` | `BOOLEAN` | `NOT NULL DEFAULT FALSE` | 차단 여부. `FIXED`만 토글 대상이고 `CUSTOM`은 등록되는 순간 항상 차단 |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | 생성 시각 |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | 수정 시각 |

```sql
CREATE UNIQUE INDEX uq_extension_policy_lower ON extension_policy (LOWER(extension));
```

`type` 구분과 무관하게 **대소문자 무시 유니크 인덱스**를 걸어서, 커스텀 확장자가 고정 확장자와 겹치거나(`exe` 재등록 등) 대소문자만 다른 값(`sh`/`SH`)이 동시에 등록되는 것을 DB 레벨에서 원천 차단합니다.

### `upload_file` — 업로드 시도 이력

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | `PRIMARY KEY` | 식별자 |
| `original_filename` | `VARCHAR(500)` | `NOT NULL` | 클라이언트가 보낸 원본 파일명 |
| `stored_filename` | `VARCHAR(255)` | nullable | 실제 저장된 논리 파일명(거부된 경우 없음) |
| `detected_extension` | `VARCHAR(20)` | nullable | 매직 넘버로 판별한 실제 확장자 |
| `status` | `VARCHAR(10)` | `NOT NULL` | `SUCCESS` / `REJECTED` |
| `reject_reason` | `VARCHAR(255)` | nullable | 거부 사유(성공 시 없음) |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | 생성 시각 |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | 수정 시각 |

`stored_filename`/`detected_extension`이 nullable인 이유: null byte가 섞인 파일명처럼 콘텐츠를 읽기도 전에 거부되는 케이스는 애초에 실제 확장자 자체가 존재하지 않기 때문입니다.

---

## API 명세

모든 4xx/5xx 응답은 `{ "message": "..." }` 형식으로 통일되어 있습니다.

### 확장자 정책

| Method | Endpoint | 설명 | 성공 응답 |
|---|---|---|---|
| GET | `/api/extensions/fixed` | 고정 확장자 7종 조회 | `200` |
| PATCH | `/api/extensions/fixed/{extension}` | 고정 확장자 차단 여부 토글 (`{ "blocked": boolean }`) | `200` |
| GET | `/api/extensions/custom` | 커스텀 확장자 목록 조회 | `200` |
| POST | `/api/extensions/custom` | 커스텀 확장자 추가 (`{ "extension": string }`) | `201` |
| DELETE | `/api/extensions/custom/{id}` | 커스텀 확장자 삭제 | `204` |

### 파일 업로드

| Method | Endpoint | 설명 | 성공 응답 |
|---|---|---|---|
| POST | `/api/files/upload` | 파일 업로드(`multipart/form-data`, 필드명 `file`) | `201` |

---

## 실행 방법

### 사전 준비물

- Java 21
- Node.js 20+
- Docker (로컬 PostgreSQL 실행용) — 또는 직접 구동 중인 PostgreSQL 16 인스턴스

### 1. 저장소 클론

```bash
git clone https://github.com/ZeroZoa/Extension-blocker-backend.git
cd Extension-blocker-backend
```

### 2. 로컬 PostgreSQL 실행

```bash
docker run -d --name ext-blocker-db \
  -e POSTGRES_DB=extblocker \
  -e POSTGRES_USER=dev \
  -e POSTGRES_PASSWORD=dev \
  -p 5432:5432 \
  postgres:16
```

> 로컬에 5432 포트를 이미 쓰는 다른 Postgres가 있다면 `-p 5433:5432`처럼 호스트 포트만 바꾸고, 아래 `DB_URL`도 그에 맞게 바꿔주세요.

### 3. 백엔드 실행

```bash
DB_URL=jdbc:postgresql://localhost:5432/extblocker \
DB_USERNAME=dev \
DB_PASSWORD=dev \
./gradlew bootRun
```

기본 포트는 `8080`입니다. 처음 실행 시 `schema.sql`/`data.sql`이 자동으로 실행되어 테이블 생성과 고정 확장자 7개 초기 데이터 삽입까지 끝납니다.

**환경변수 전체 목록**

| 변수 | 기본값 | 설명 |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/extblocker` | DB 접속 URL |
| `DB_USERNAME` | `dev` | DB 계정 |
| `DB_PASSWORD` | `dev` | DB 비밀번호 |
| `PORT` | `8080` | 서버 포트 |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | 허용할 프론트엔드 오리진(콤마로 여러 개 지정 가능) |
| `UPLOAD_DIR` | `uploads-data` | 업로드 파일 저장 경로 |

### 4. 프론트엔드 실행

```bash
cd frontend
npm install
npm run dev
```

`http://localhost:5173`에서 접속 가능하며, `.env.development`의 `VITE_API_BASE_URL`이 백엔드 주소(`http://localhost:8080`)를 가리키고 있습니다.

### 5. 테스트 실행 (선택)

```bash
DB_URL=jdbc:postgresql://localhost:5432/extblocker ./gradlew test
```

---

## 배포

- **백엔드**: Render, `Dockerfile` 기반 배포
- **프론트엔드**: Vercel, Root Directory를 `frontend`로 지정
- 배포 시 백엔드의 `CORS_ALLOWED_ORIGINS`를 실제 Vercel 배포 URL로, 프론트엔드의 `VITE_API_BASE_URL`을 실제 Render 배포 URL로 설정해야 합니다.

_(배포 완료 후 실제 URL과 세부 사항을 이 섹션에 갱신 예정)_
