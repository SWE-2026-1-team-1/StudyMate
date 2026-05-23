export type ScreenId =
  | "login"
  | "signup"
  | "main"
  | "explore"
  | "detail"
  | "mypage"
  | "create-basic"
  | "create-rules"
  | "create-schedule"
  | "team-board"
  | "team-attendance"
  | "team-members";

export type Study = {
  title: string;
  tags: string[];
  people: string;
  duration: string;
  tone: "tech" | "english" | "algorithm" | "design";
};

export type StudyRule = {
  no: string;
  title: string;
  desc: string;
};

export type StudyMember = {
  name: string;
  role: string;
  avatar: string;
};

export type StudyInfoItem = {
  label: string;
  value: string;
};

export type StudyDetailData = {
  id: string;
  title: string;
  subtitle: string;
  description: string[];
  tags: string[];
  location: string;
  info: StudyInfoItem[];
  rules: StudyRule[];
  members: StudyMember[];
};

export type ProgressStudy = {
  title: string;
  people: string;
  time: string;
  value: string;
};

export type StatusItem = {
  title: string;
  meta: string;
  status: string;
};

export type AttendanceState = "present" | "absent" | "scheduled";

export type AttendanceMember = {
  name: string;
  avatar: string;
  checks: AttendanceState[];
};

export type TeamMember = {
  name: string;
  role: string;
  rate: string;
  avatar: string;
};

export type JoinRequest = {
  name: string;
  date: string;
  avatar: string;
};

export type TeamPost = {
  tag: string;
  title: string;
  excerpt: string;
  time: string;
  comments: number;
  likes: number;
  author: string;
  avatar: string;
};
