import type { ScreenId } from "../types";

export type RouteLayout = "auth" | "app" | "team" | "none";
export type RouteAuth = "publicOnly" | "required" | "optional";
export type TopLevelRoute = "main" | "explore" | "create" | "mypage";

export type RouteConfig = {
  id: string;
  path: string;
  screenId?: ScreenId;
  component: string;
  layout: RouteLayout;
  auth: RouteAuth;
  title: string;
  topLevel?: TopLevelRoute;
};

export const ROUTE_PATHS = {
  login: "/login",
  signup: "/signup",
  home: "/home",
  studies: "/studies",
  studyDetail: (studyId = "business-english") => `/studies/${studyId}`,
  createBasic: "/studies/new/basic",
  createRules: "/studies/new/rules",
  createSchedule: "/studies/new/schedule",
  mypage: "/mypage",
  teamBoard: (teamId = "python-study") => `/teams/${teamId}/board`,
  teamAttendance: (teamId = "python-study") => `/teams/${teamId}/attendance`,
  teamMembers: (teamId = "python-study") => `/teams/${teamId}/members`,
} as const;

export const routingMap: RouteConfig[] = [
  {
    id: "auth.login",
    path: ROUTE_PATHS.login,
    screenId: "login",
    component: "AuthScreen",
    layout: "auth",
    auth: "publicOnly",
    title: "로그인",
  },
  {
    id: "auth.signup",
    path: ROUTE_PATHS.signup,
    screenId: "signup",
    component: "AuthScreen",
    layout: "auth",
    auth: "publicOnly",
    title: "회원가입",
  },
  {
    id: "home",
    path: ROUTE_PATHS.home,
    screenId: "main",
    component: "MainDashboard",
    layout: "app",
    auth: "required",
    title: "메인",
    topLevel: "main",
  },
  {
    id: "studies.index",
    path: ROUTE_PATHS.studies,
    screenId: "explore",
    component: "ExplorePage",
    layout: "app",
    auth: "required",
    title: "스터디 탐색",
    topLevel: "explore",
  },
  {
    id: "studies.detail",
    path: "/studies/:studyId",
    screenId: "detail",
    component: "StudyDetail",
    layout: "app",
    auth: "required",
    title: "스터디 상세",
    topLevel: "explore",
  },
  {
    id: "studies.create.basic",
    path: ROUTE_PATHS.createBasic,
    screenId: "create-basic",
    component: "CreateStudy",
    layout: "app",
    auth: "required",
    title: "스터디 생성 - 기본 정보",
    topLevel: "create",
  },
  {
    id: "studies.create.rules",
    path: ROUTE_PATHS.createRules,
    screenId: "create-rules",
    component: "CreateStudy",
    layout: "app",
    auth: "required",
    title: "스터디 생성 - 규칙 및 태그",
    topLevel: "create",
  },
  {
    id: "studies.create.schedule",
    path: ROUTE_PATHS.createSchedule,
    screenId: "create-schedule",
    component: "CreateStudy",
    layout: "app",
    auth: "required",
    title: "스터디 생성 - 일정 설정",
    topLevel: "create",
  },
  {
    id: "me.profile",
    path: ROUTE_PATHS.mypage,
    screenId: "mypage",
    component: "MyPage",
    layout: "app",
    auth: "required",
    title: "마이페이지",
    topLevel: "mypage",
  },
  {
    id: "teams.board",
    path: "/teams/:teamId/board",
    screenId: "team-board",
    component: "TeamBoard",
    layout: "team",
    auth: "required",
    title: "팀 게시판",
    topLevel: "main",
  },
  {
    id: "teams.attendance",
    path: "/teams/:teamId/attendance",
    screenId: "team-attendance",
    component: "TeamAttendance",
    layout: "team",
    auth: "required",
    title: "팀 출석",
    topLevel: "main",
  },
  {
    id: "teams.members",
    path: "/teams/:teamId/members",
    screenId: "team-members",
    component: "TeamMembers",
    layout: "team",
    auth: "required",
    title: "팀원 관리",
    topLevel: "main",
  },
];

export function getTopLevelRoute(pathname: string): TopLevelRoute {
  if (pathname === ROUTE_PATHS.mypage) return "mypage";
  if (pathname.startsWith("/studies/new")) return "create";
  if (pathname.startsWith("/studies")) return "explore";
  return "main";
}
