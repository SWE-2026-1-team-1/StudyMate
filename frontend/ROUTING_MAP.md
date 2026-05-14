# StudyMate Frontend Routing Map

> Purpose: StudyMate frontend의 URL routing 기준을 정리한 점검용 문서입니다.
> Status: `react-router-dom` 기반 라우팅으로 구현되어 있으며, 실제 route 기준은 `src/routes/routingMap.ts`에서 관리합니다.

## Current Routing Status

| 항목 | 현재 상태 |
| --- | --- |
| Routing library | `react-router-dom` |
| Current mechanism | URL path 기반 route |
| Entry component | `src/App.tsx` |
| Route renderer | `src/routes/AppRoutes.tsx` |
| Route source map | `src/routes/routingMap.ts` |
| Layouts | `src/layouts/AuthLayout.tsx`, `src/layouts/AppLayout.tsx`, `src/layouts/TeamLayout.tsx` |
| Legacy screen id | `ScreenId`는 route map 호환/문서 식별용으로 유지 |

## Route Map

| Route ID | URL Path | Current ScreenId | Component | Layout | Auth Guard | Description |
| --- | --- | --- | --- | --- | --- | --- |
| `auth.login` | `/login` | `login` | `AuthScreen mode="login"` | Auth layout | Public only | 로그인 화면 |
| `auth.signup` | `/signup` | `signup` | `AuthScreen mode="signup"` | Auth layout | Public only | 회원가입 화면 |
| `home` | `/` | `main` | `MainDashboard` | App frame + top bar | Required | 메인 대시보드 |
| `studies.index` | `/studies` | `explore` | `ExplorePage` | App frame + top bar | Required | 스터디 탐색/목록 |
| `studies.detail` | `/studies/:studyId` | `detail` | `StudyDetail` | App frame + top bar | Required | 스터디 상세 |
| `studies.create.basic` | `/studies/new/basic` | `create-basic` | `CreateStudy step={1}` | App frame + top bar | Required | 스터디 생성 1단계: 기본 정보 |
| `studies.create.rules` | `/studies/new/rules` | `create-rules` | `CreateStudy step={2}` | App frame + top bar | Required | 스터디 생성 2단계: 규칙 및 태그 |
| `studies.create.schedule` | `/studies/new/schedule` | `create-schedule` | `CreateStudy step={3}` | App frame + top bar | Required | 스터디 생성 3단계: 일정 설정 |
| `me.profile` | `/mypage` | `mypage` | `MyPage` | App frame + top bar | Required | 내 프로필/마이페이지 |
| `teams.board` | `/teams/:teamId/board` | `team-board` | `TeamBoard` | App frame + top bar + team side nav | Required | 팀 게시판 |
| `teams.attendance` | `/teams/:teamId/attendance` | `team-attendance` | `TeamAttendance` | App frame + top bar + team side nav | Required | 팀 출석 관리 |
| `teams.members` | `/teams/:teamId/members` | `team-members` | `TeamMembers` | App frame + top bar + team side nav | Required | 팀원 관리 |
| `not-found` | `*` | N/A | `NotFound` | App frame or minimal layout | Optional | 존재하지 않는 경로 |

## Redirect Rules

| From | To | Condition | Reason |
| --- | --- | --- | --- |
| `/` | `/login` | Not authenticated | 비로그인 사용자의 기본 진입점 |
| `/` | `/studies` or `/home` | Authenticated | 로그인 후 기본 진입점 |
| `/studies/new` | `/studies/new/basic` | Always | 생성 플로우 기본 단계 |
| `/teams/:teamId` | `/teams/:teamId/board` | Always | 팀 화면 기본 탭 |
| `/login` | `/` | Already authenticated | 로그인 사용자의 인증 화면 재진입 방지 |
| `/signup` | `/` | Already authenticated | 로그인 사용자의 회원가입 화면 재진입 방지 |

## Navigation Flow Map

| Source | UI Action | Current Target ScreenId | Proposed Target Path |
| --- | --- | --- | --- |
| `AuthScreen` login form | 로그인 버튼 | `main` | `/` |
| `AuthScreen` signup tab | 회원가입 탭 | `signup` | `/signup` |
| `AuthScreen` login tab | 로그인 탭 | `login` | `/login` |
| `AuthScreen` signup step 2 | 회원가입 완료 | `main` | `/` |
| `TopBar` brand | StudyMate logo click | `main` | `/` |
| `TopBar` Home | Home click | `main` | `/` |
| `TopBar` Search | Search click | `explore` | `/studies` |
| `TopBar` Create Study | Create Study click | `create-basic` | `/studies/new/basic` |
| `TopBar` My Page | My Page click | `mypage` | `/mypage` |
| `TopBar` avatar | Avatar click | `mypage` | `/mypage` |
| `MainDashboard` CTA | 스터디 만들기 | `create-basic` | `/studies/new/basic` |
| `MainDashboard` My Study card | 입장하기 | `team-board` | `/teams/:teamId/board` |
| `ExplorePage` study card | 신청하기 | `detail` | `/studies/:studyId` |
| `CreateStudy step 1` | 다음 단계로 이동 | `create-rules` | `/studies/new/rules` |
| `CreateStudy step 2` | 다음 단계로 이동 | `create-schedule` | `/studies/new/schedule` |
| `CreateStudy step 3` | 완료 | `main` | `/` |
| `TeamShell` board tab | 게시판 | `team-board` | `/teams/:teamId/board` |
| `TeamShell` attendance tab | 출석체크 | `team-attendance` | `/teams/:teamId/attendance` |
| `TeamShell` members tab | 팀원관리 | `team-members` | `/teams/:teamId/members` |

## Route Object Draft

```ts
type RouteConfig = {
  id: string;
  path: string;
  screenId?: ScreenId;
  component: string;
  layout: "auth" | "app" | "team" | "none";
  auth: "publicOnly" | "required" | "optional";
  title: string;
};

export const routes: RouteConfig[] = [
  {
    id: "auth.login",
    path: "/login",
    screenId: "login",
    component: "AuthScreen",
    layout: "auth",
    auth: "publicOnly",
    title: "로그인",
  },
  {
    id: "auth.signup",
    path: "/signup",
    screenId: "signup",
    component: "AuthScreen",
    layout: "auth",
    auth: "publicOnly",
    title: "회원가입",
  },
  {
    id: "home",
    path: "/",
    screenId: "main",
    component: "MainDashboard",
    layout: "app",
    auth: "required",
    title: "메인",
  },
  {
    id: "studies.index",
    path: "/studies",
    screenId: "explore",
    component: "ExplorePage",
    layout: "app",
    auth: "required",
    title: "스터디 탐색",
  },
  {
    id: "studies.detail",
    path: "/studies/:studyId",
    screenId: "detail",
    component: "StudyDetail",
    layout: "app",
    auth: "required",
    title: "스터디 상세",
  },
  {
    id: "studies.create.basic",
    path: "/studies/new/basic",
    screenId: "create-basic",
    component: "CreateStudy",
    layout: "app",
    auth: "required",
    title: "스터디 생성 - 기본 정보",
  },
  {
    id: "studies.create.rules",
    path: "/studies/new/rules",
    screenId: "create-rules",
    component: "CreateStudy",
    layout: "app",
    auth: "required",
    title: "스터디 생성 - 규칙 및 태그",
  },
  {
    id: "studies.create.schedule",
    path: "/studies/new/schedule",
    screenId: "create-schedule",
    component: "CreateStudy",
    layout: "app",
    auth: "required",
    title: "스터디 생성 - 일정 설정",
  },
  {
    id: "me.profile",
    path: "/mypage",
    screenId: "mypage",
    component: "MyPage",
    layout: "app",
    auth: "required",
    title: "마이페이지",
  },
  {
    id: "teams.board",
    path: "/teams/:teamId/board",
    screenId: "team-board",
    component: "TeamBoard",
    layout: "team",
    auth: "required",
    title: "팀 게시판",
  },
  {
    id: "teams.attendance",
    path: "/teams/:teamId/attendance",
    screenId: "team-attendance",
    component: "TeamAttendance",
    layout: "team",
    auth: "required",
    title: "팀 출석",
  },
  {
    id: "teams.members",
    path: "/teams/:teamId/members",
    screenId: "team-members",
    component: "TeamMembers",
    layout: "team",
    auth: "required",
    title: "팀원 관리",
  },
];
```

## Implementation Files

| File | Role |
| --- | --- |
| `src/App.tsx` | `BrowserRouter`와 app shell 연결 |
| `src/routes/AppRoutes.tsx` | 실제 `<Routes>` 구성 |
| `src/routes/routingMap.ts` | route id, path, component, layout, auth metadata 관리 |
| `src/layouts/AuthLayout.tsx` | 로그인/회원가입 레이아웃 |
| `src/layouts/AppLayout.tsx` | 일반 페이지 공통 `Frame` + `TopBar` 레이아웃 |
| `src/layouts/TeamLayout.tsx` | 팀 페이지 공통 `Frame` + `TopBar` + team side nav 레이아웃 |
| `src/components/Common.tsx` | `TopBar`의 route link/active state 관리 |

## Layout Boundaries

| Layout | Applies To | Current Implementation |
| --- | --- | --- |
| Auth layout | `/login`, `/signup` | `AuthScreen` 단독 렌더링 |
| App layout | `/`, `/studies`, `/studies/:studyId`, `/studies/new/*`, `/mypage` | `Frame` + `TopBar` + page component |
| Team layout | `/teams/:teamId/*` | `Frame` + `TopBar` + `TeamShell` |

## Implementation Notes

- 기존 `ScreenSwitcher`는 라우팅용 UI가 아니라 Figma 12개 화면 점검용 전환기였고, 실제 앱 shell에서는 제거되었습니다.
- `ScreenId`는 현재 `routingMap`의 호환/문서 식별 필드로만 유지됩니다.
- `StudyDetail`은 현재 고정 데이터를 사용하므로 `/studies/:studyId` 적용 시 `studyId` 기반 데이터 조회가 필요합니다.
- 팀 화면도 현재 고정 팀 데이터를 사용하므로 `/teams/:teamId/*` 적용 시 `teamId` 기반 데이터 조회가 필요합니다.
- 회원가입 내부 단계는 현재 `AuthScreen`의 local state입니다. 필요하면 `/signup/email`, `/signup/profile`처럼 별도 route로 분리할 수 있습니다.
