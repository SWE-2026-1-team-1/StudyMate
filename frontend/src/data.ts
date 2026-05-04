import type { AttendanceMember, JoinRequest, ProgressStudy, ScreenId, StatusItem, Study, StudyMember, StudyRule, TeamMember, TeamPost } from "./types";

export const screens: { id: ScreenId; label: string; group: string }[] = [
  { id: "login", label: "로그인", group: "인증" },
  { id: "signup", label: "회원가입", group: "인증" },
  { id: "main", label: "메인", group: "메인" },
  { id: "explore", label: "스터디 탐색", group: "스터디" },
  { id: "detail", label: "스터디 상세", group: "스터디" },
  { id: "mypage", label: "마이페이지", group: "개인" },
  { id: "create-basic", label: "생성 1", group: "생성" },
  { id: "create-rules", label: "생성 2", group: "생성" },
  { id: "create-schedule", label: "생성 3", group: "생성" },
  { id: "team-board", label: "게시판", group: "팀" },
  { id: "team-attendance", label: "출석", group: "팀" },
  { id: "team-members", label: "팀원", group: "팀" },
];

export const studies: Study[] = [
  {
    title: "포트폴리오 완성반: 리액트 심화",
    tags: ["#REACT", "#FRONTEND"],
    people: "8/10",
    duration: "12주",
    tone: "tech",
  },
  {
    title: "비즈니스 영어 회화 실전",
    tags: ["#ENGLISH", "#CONVERSATION"],
    people: "4/6",
    duration: "8주",
    tone: "english",
  },
  {
    title: "파이썬 알고리즘 문풀 (실버)",
    tags: ["#CS", "#PYTHON"],
    people: "12/15",
    duration: "16주",
    tone: "algorithm",
  },
];

export const exploreStudies = [...studies, ...studies, ...studies];

export const topics = ["#전체", "#알고리즘", "#영어회화", "#프론트엔드", "#백엔드", "#UI/UX 디자인", "#데이터사이언스"];

export const authInterestTags = ["#알고리즘", "#English", "#UI_Design", "#Macroeconomics"];

export const studyDetail = {
  tags: ["# BUSINESS", "# ENGLISH", "# CONVERSATION"],
  location: "중앙도서관 4층 세미나실",
  info: [
    { label: "진행 방식", value: "온라인 중심" },
    { label: "모임 시간", value: "매주 토요일 14:00" },
    { label: "현재 인원", value: "4 / 8명" },
  ],
  rules: [
    { no: "01", title: "매주 회화 과제 준비", desc: "모임 전 지정된 비즈니스 영어 표현과 대화 주제를 미리 학습합니다." },
    { no: "02", title: "영어로 말하기 우선", desc: "스터디 시간에는 가능한 한 영어로 대화하며, 실수를 피하기보다 말하는 연습에 집중합니다." },
    { no: "03", title: "적극적인 피드백 참여", desc: "롤플레이와 발표 후 서로의 표현, 발음, 전달 방식에 대해 간단한 피드백을 제공합니다." },
    { no: "04", title: "지각 및 결석 사전 공유", desc: "참여가 어려운 경우 모임 전까지 팀 게시판이나 채팅방에 미리 공유합니다." },
  ] satisfies StudyRule[],
  members: [
    { name: "김지우", role: "Leader / UX Designer", avatar: "a" },
    { name: "Lucas Meyer", role: "Product Designer", avatar: "b" },
    { name: "Priya Sharma", role: "UI Engineer", avatar: "c" },
  ] satisfies StudyMember[],
};

export const profile = {
  keywords: ["#ProductDesign", "#English_Advanced", "#Startups"],
  interestKeywords: ["UI/UX Design", "AI Research", "Photography", "Startups", "Economics", "Biking"],
  progressStudies: [
    { title: "심리학으로 풀어보는 UX 분석 스터디", value: "75%" },
    { title: "TOEFL Speaking 80+ 정복하기", value: "50%" },
  ] satisfies ProgressStudy[],
  applications: [
    { title: "React 프론트엔드 실전 프로젝트", meta: "2일 전 지원함", status: "PENDING" },
    { title: "기초 타이포그래피 원리 연구", meta: "5일 전 지원함", status: "ACCEPTED" },
  ] satisfies StatusItem[],
};

export const createStudy = {
  steps: ["기본 정보", "규칙 및 태그", "일정 설정"],
  categories: [
    { icon: "‹›", label: "IT / 프로그래밍", selected: true },
    { icon: "文", label: "언어 / 어학", selected: false },
    { icon: "✣", label: "취업 / 직무", selected: false },
    { icon: "…", label: "기타", selected: false },
  ],
  visibilityOptions: [
    { label: "전체 공개", selected: true },
    { label: "비공개 (링크 전용)", selected: false },
  ],
  schedule: [
    { label: "모임 시간", value: "매주 수요일 16:00" },
    { label: "스터디 기간", value: "4/29 ~ 6/17" },
    { label: "진행방식", value: "온라인" },
  ],
};

export const teamPosts: TeamPost[] = [
  { tag: "RESEARCH", title: "중간 발표 자료 준비 및 데이터 분석 공유", author: "김지수", avatar: "c" },
  { tag: "ENGINEERING", title: "알고리즘 구현 중 엣지 케이스 처리 질문", author: "박민호", avatar: "a" },
  { tag: "ENGINEERING", title: "알고리즘 구현 중 엣지 케이스 처리 질문", author: "박민호", avatar: "a" },
];

export const attendanceDates = ["10/01", "10/03", "10/05", "10/08", "10/10", "10/12", "10/15", "10/17", "10/19 (Next)"];

export const attendanceMembers: AttendanceMember[] = [
  { name: "김지수", avatar: "user", checks: ["present", "present", "absent", "present", "present", "present", "present", "present", "scheduled"] },
  { name: "박민재", avatar: "a", checks: ["present", "present", "present", "present", "present", "present", "present", "present", "scheduled"] },
  { name: "이리나", avatar: "", checks: ["present", "absent", "present", "present", "present", "absent", "present", "present", "scheduled"] },
  { name: "최하늘", avatar: "", checks: ["present", "present", "present", "present", "present", "present", "present", "present", "scheduled"] },
];

export const teamMembers: TeamMember[] = [
  { name: "김민수", role: "LEADER", rate: "98%", avatar: "user" },
  { name: "이서윤", role: "RESEARCHER", rate: "92%", avatar: "c" },
  { name: "박지호", role: "DESIGNER", rate: "88%", avatar: "b" },
];

export const joinRequests: JoinRequest[] = [
  { name: "정우진", date: "신청일: 2024.03.12", avatar: "b" },
  { name: "한나래", date: "신청일: 2024.03.11", avatar: "c" },
];
