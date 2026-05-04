# Contributing Guide

## 브랜치 전략

`main` 브랜치를 기반으로 작업 브랜치를 생성하고 PR을 통해 병합합니다.  
`main`에 직접 푸시는 레포 룰로 차단되어 있습니다.

```
main
├── feat/login
├── feat/study-room
└── fix/session-timeout
```

### 브랜치 네이밍

```
<type>/<작업 내용>
```

| type | 설명 |
|------|------|
| `feat` | 새로운 기능 |
| `fix` | 버그 수정 |
| `refactor` | 리팩토링 |
| `docs` | 문서 수정 |
| `chore` | 빌드, 설정 등 기타 |

**예시**

```
feat/oauth-login
fix/signup-validation
docs/api-guide
```

---

## 커밋 컨벤션

```
<type>: <내용>
```

- 내용은 **현재형 동사**로 시작 (한국어 가능)
- 제목은 **50자 이내**

**예시**

```
feat: 구글 소셜 로그인 추가
fix: 비밀번호 유효성 검사 오류 수정
docs: API 명세 업데이트
```

---

## PR 규칙

- 브랜치는 항상 **최신 `main`에서 생성**
- PR 제목은 커밋 컨벤션과 동일한 형식 사용
- PR 본문은 템플릿에 맞게 작성
- 병합 후 작업 브랜치는 삭제
