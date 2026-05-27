# Frontend

StudyMate 프론트엔드 프로젝트입니다.

## 실행 방법

```bash
npm install
npm run dev
```

브라우저에서 Vite가 안내하는 로컬 주소로 접속하면 됩니다.

## API 프록시 설정

개발 서버의 `/api` 요청은 `API_PROXY_TARGET`으로 프록시됩니다.

```bash
cp .env.example .env
```

필요하면 `.env`에서 백엔드 주소를 변경하세요. 실제 서버 주소가 포함된 `.env`는 커밋하지 않습니다.
