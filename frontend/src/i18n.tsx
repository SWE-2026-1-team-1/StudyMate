import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";

export type LanguageCode = "zh" | "en" | "ko";

type LanguageOption = {
  code: LanguageCode;
  label: string;
  shortLabel: string;
};

type LanguageContextValue = {
  language: LanguageCode;
  currentLanguage: LanguageOption;
  nextLanguage: LanguageOption;
  setLanguage: (language: LanguageCode) => void;
  toggleLanguage: () => void;
  translate: (value: string) => string;
};

const STORAGE_KEY = "studymate.language";

export const LANGUAGE_OPTIONS: LanguageOption[] = [
  { code: "ko", label: "한국어", shortLabel: "KO" },
  { code: "en", label: "English", shortLabel: "EN" },
  { code: "zh", label: "中文", shortLabel: "ZH" },
];

const textTranslations: Record<string, Partial<Record<LanguageCode, string>>> = {
  "로그인": { zh: "登录", en: "Login" },
  "회원가입": { zh: "注册", en: "Sign up" },
  "메인": { zh: "主页", en: "Main" },
  "스터디 탐색": { zh: "学习小组探索", en: "Explore studies" },
  "스터디 상세": { zh: "学习小组详情", en: "Study detail" },
  "마이페이지": { zh: "我的页面", en: "My Page" },
  "개인": { zh: "个人", en: "Personal" },
  "생성 1": { zh: "创建 1", en: "Create 1" },
  "생성 2": { zh: "创建 2", en: "Create 2" },
  "생성 3": { zh: "创建 3", en: "Create 3" },
  "생성": { zh: "创建", en: "Create" },
  "게시판": { zh: "公告板", en: "Board" },
  "출석": { zh: "出勤", en: "Attendance" },
  "팀원": { zh: "成员", en: "Members" },
  "팀": { zh: "团队", en: "Team" },
  "인증": { zh: "认证", en: "Auth" },
  "스터디": { zh: "学习小组", en: "Study" },
  "Home": { zh: "首页", en: "Home", ko: "홈" },
  "Search": { zh: "搜索", en: "Search", ko: "검색" },
  "Create Study": { zh: "创建学习小组", en: "Create Study", ko: "스터디 만들기" },
  "My Page": { zh: "我的页面", en: "My Page", ko: "마이페이지" },
  "주요 화면": { zh: "主导航", en: "Primary navigation" },
  "마이페이지로 이동": { zh: "前往我的页面", en: "Go to My Page" },
  "학사모 아이콘": { zh: "学士帽图标", en: "Graduation cap icon" },
  "언어 전환": { zh: "切换语言", en: "Switch language" },
  "나의 완벽한": { zh: "寻找你的", en: "Find your" },
  "스터디 파트너를": { zh: "完美学习伙伴", en: "perfect study" },
  "찾아보세요.": { zh: "从这里开始。", en: "partner." },
  "전 세계 유학생들과 함께 전공 지식을 나누고,": {
    zh: "与来自世界各地的留学生分享专业知识，",
    en: "Share major knowledge with international students,",
  },
  "언어를 교환하며 함께 성장하는 지적인 커뮤니티입니다.": {
    zh: "交换语言，并在高质量社区中共同成长。",
    en: "exchange languages, and grow together in a smart community.",
  },
  "지금 1,200명의 학우들이 함께": { zh: "现在已有 1,200 名同学", en: "1,200 classmates are" },
  "공부하고 있습니다.": { zh: "正在一起学习。", en: "studying together now." },
  "반가워요, 학우님!": { zh: "欢迎回来，同学！", en: "Welcome back!" },
  "서비스 이용을 위해 학교 계정으로 로그인 해주세요.": {
    zh: "请使用学校账号登录后继续使用服务。",
    en: "Log in with your school account to continue.",
  },
  "대학교 이메일": { zh: "大学邮箱", en: "University email" },
  "비밀번호": { zh: "密码", en: "Password" },
  "로그인 중...": { zh: "正在登录...", en: "Logging in..." },
  "계정이 없으신가요?": { zh: "还没有账号？", en: "No account yet?" },
  "지금 가입하세요": { zh: "立即注册", en: "Sign up now" },
  "새로운 시작을 함께해요!": { zh: "一起开始新的学习旅程！", en: "Start something new together!" },
  "스터디 메이트가 되기 위해 이메일 인증을 진행해 주세요.": {
    zh: "成为 StudyMate 前，请先完成邮箱验证。",
    en: "Verify your email to become a StudyMate.",
  },
  "전송 중...": { zh: "正在发送...", en: "Sending..." },
  "인증코드 보내기": { zh: "发送验证码", en: "Send code" },
  "인증 코드": { zh: "验证码", en: "Verification code" },
  "인증 코드 6자리 입력": { zh: "输入 6 位验证码", en: "Enter 6-digit code" },
  "확인 중...": { zh: "正在确认...", en: "Checking..." },
  "다음 단계": { zh: "下一步", en: "Next step" },
  "거의 다 왔어요!": { zh: "快完成了！", en: "Almost there!" },
  "비밀번호와 관심사를 설정해 주세요.": {
    zh: "请设置密码和感兴趣的主题。",
    en: "Set your password and interests.",
  },
  "이름": { zh: "姓名", en: "Name" },
  "홍길동": { zh: "张同学", en: "Alex Kim" },
  "비밀번호 확인": { zh: "确认密码", en: "Confirm password" },
  "이전 단계": { zh: "上一步", en: "Previous step" },
  "가입 중...": { zh: "正在注册...", en: "Signing up..." },
  "회원가입 완료": { zh: "完成注册", en: "Complete sign up" },
  "이용약관": { zh: "服务条款", en: "Terms" },
  "개인정보처리방침": { zh: "隐私政策", en: "Privacy" },
  "도움말": { zh: "帮助", en: "Help" },
  "로그인에 실패했습니다.": { zh: "登录失败。", en: "Login failed." },
  "서버에 연결할 수 없습니다.": { zh: "无法连接到服务器。", en: "Cannot connect to the server." },
  "이메일 또는 비밀번호가 올바르지 않습니다.": {
    zh: "邮箱或密码不正确。",
    en: "Email or password is incorrect.",
  },
  "가입되지 않은 이메일입니다.": { zh: "该邮箱尚未注册。", en: "This email is not registered." },
  "이메일과 비밀번호를 입력해 주세요.": { zh: "请输入邮箱和密码。", en: "Enter your email and password." },
  "대학교 이메일을 먼저 입력해 주세요.": {
    zh: "请先输入大学邮箱。",
    en: "Enter your university email first.",
  },
  "인증코드를 전송했습니다. 메일함을 확인해 주세요.": {
    zh: "验证码已发送，请查看邮箱。",
    en: "Verification code sent. Please check your inbox.",
  },
  "인증코드 전송에 실패했습니다.": {
    zh: "验证码发送失败。",
    en: "Failed to send verification code.",
  },
  "이메일과 인증 코드를 입력해 주세요.": {
    zh: "请输入邮箱和验证码。",
    en: "Enter your email and verification code.",
  },
  "이메일 인증이 완료되었습니다.": {
    zh: "邮箱验证已完成。",
    en: "Email verification is complete.",
  },
  "인증 코드 확인에 실패했습니다.": {
    zh: "验证码确认失败。",
    en: "Failed to verify the code.",
  },
  "이메일 인증을 먼저 완료해 주세요.": {
    zh: "请先完成邮箱验证。",
    en: "Complete email verification first.",
  },
  "이름을 입력해 주세요.": { zh: "请输入姓名。", en: "Enter your name." },
  "비밀번호를 입력해 주세요.": { zh: "请输入密码。", en: "Enter your password." },
  "비밀번호가 서로 일치하지 않습니다.": {
    zh: "两次输入的密码不一致。",
    en: "Passwords do not match.",
  },
  "비밀번호는 영문과 숫자를 포함한 8~20자여야 합니다.": {
    zh: "密码需包含英文字母和数字，长度为 8-20 位。",
    en: "Password must be 8-20 characters and include letters and numbers.",
  },
  "회원가입에 실패했습니다.": { zh: "注册失败。", en: "Sign up failed." },
  "맞춤 스터디 탐색": { zh: "个性化学习小组探索", en: "Personalized study discovery" },
  "자신의 목표에 맞는 스터디 팀을 찾고, 동료들과 함께 더 멀리 나아가세요.": {
    zh: "找到适合自己目标的学习团队，与伙伴一起走得更远。",
    en: "Find a study team that fits your goals and go further with peers.",
  },
  "관심 있는 스터디 주제나 기술 스택을 검색해보세요": {
    zh: "搜索感兴趣的学习主题或技术栈",
    en: "Search study topics or tech stacks",
  },
  "메인으로 돌아가기": { zh: "返回主页", en: "Back to main" },
  "New": { zh: "新", en: "New", ko: "새 소식" },
  "나만의 스터디를 만들고 함께 성장할 팀원을 모집하세요": {
    zh: "创建自己的学习小组，招募一起成长的队友",
    en: "Create your own study and recruit teammates to grow with",
  },
  "목표, 일정, 모집 인원을 설정하고 팀원 모집부터 운영까지 한곳에서 관리할 수 있습니다.": {
    zh: "设置目标、日程和招募人数，从招募到运营都能在一处管理。",
    en: "Set goals, schedules, and capacity, then manage recruiting and operations in one place.",
  },
  "스터디 만들기": { zh: "创建学习小组", en: "Create study" },
  "My Study": { zh: "我的学习小组", en: "My Study", ko: "내 스터디" },
  "마이페이지  →": { zh: "我的页面  →", en: "My Page  →" },
  "입장하기": { zh: "进入", en: "Enter" },
  "신청하기": { zh: "申请加入", en: "Apply" },
  "전체 스터디": { zh: "全部学习小组", en: "All studies" },
  "현재 모집 중인 모든 스터디 그룹입니다.": {
    zh: "这里展示当前正在招募的所有学习小组。",
    en: "All study groups currently recruiting.",
  },
  "관심사 기반 추천 스터디": { zh: "基于兴趣推荐", en: "Recommended for your interests" },
  "관심사 기반으로 선별한 스터디 그룹입니다.": {
    zh: "根据你的兴趣筛选出的学习小组。",
    en: "Study groups selected based on your interests.",
  },
  "POPULAR TOPICS": { zh: "热门主题", en: "POPULAR TOPICS", ko: "인기 주제" },
  "참여하기": { zh: "参与", en: "Join" },
  "스터디 소개": { zh: "学习小组介绍", en: "Study introduction" },
  "규칙": { zh: "规则", en: "Rules" },
  "참여중인 멤버": { zh: "参与成员", en: "Members" },
  "+ 4 Seats Available": { zh: "+ 4 个名额可用", en: "+ 4 Seats Available", ko: "+ 4자리 남음" },
  "로그인 후 프로필을 관리할 수 있습니다.": {
    zh: "登录后可以管理个人资料。",
    en: "Log in to manage your profile.",
  },
  "프로필을 불러오지 못했습니다. 로그인 후 다시 시도해주세요.": {
    zh: "无法加载个人资料，请登录后重试。",
    en: "Could not load your profile. Log in and try again.",
  },
  "이름을 입력해주세요.": { zh: "请输入姓名。", en: "Enter your name." },
  "프로필이 저장되었습니다.": { zh: "个人资料已保存。", en: "Profile saved." },
  "프로필 저장에 실패했습니다.": { zh: "个人资料保存失败。", en: "Failed to save profile." },
  "프로필과 계정을 삭제하시겠습니까?": {
    zh: "确定要删除个人资料和账号吗？",
    en: "Delete your profile and account?",
  },
  "프로필 삭제에 실패했습니다.": { zh: "个人资料删除失败。", en: "Failed to delete profile." },
  "프로필 이름": { zh: "个人资料姓名", en: "Profile name" },
  "학교 정보": { zh: "学校信息", en: "School" },
  "전공 정보": { zh: "专业信息", en: "Major" },
  "프로필 소개": { zh: "个人简介", en: "Profile bio" },
  "프로필을 불러오는 중": { zh: "正在加载个人资料", en: "Loading profile" },
  "이름 없음": { zh: "未填写姓名", en: "No name" },
  "학교와 전공을 입력해주세요": { zh: "请填写学校和专业", en: "Add your school and major" },
  "저장 중": { zh: "正在保存", en: "Saving" },
  "저장하기": { zh: "保存", en: "Save" },
  "프로필 편집": { zh: "编辑资料", en: "Edit profile" },
  "취소": { zh: "取消", en: "Cancel" },
  "계정 삭제": { zh: "删除账号", en: "Delete account" },
  "참여 중인 스터디": { zh: "正在参与的学习小组", en: "Joined studies" },
  "지원 현황": { zh: "申请状态", en: "Application status" },
  "기본 정보를 입력해주세요": { zh: "请填写基本信息", en: "Enter basic information" },
  "규칙 및 태그를 입력해주세요": { zh: "请填写规则与标签", en: "Enter rules and tags" },
  "일정 설정": { zh: "日程设置", en: "Schedule setup" },
  "새 스터디 만들기": { zh: "创建新的学习小组", en: "Create a new study" },
  "당신의 지적 성장을 이끌어갈 동료들을 찾아보세요.": {
    zh: "寻找能推动你知识成长的伙伴。",
    en: "Find peers who will support your intellectual growth.",
  },
  "× 취소하기": { zh: "× 取消", en: "× Cancel" },
  "← 이전으로": { zh: "← 返回", en: "← Back" },
  "완료": { zh: "完成", en: "Done" },
  "다음 단계로 이동": { zh: "前往下一步", en: "Go to next step" },
  "기본 정보": { zh: "基本信息", en: "Basic info" },
  "규칙 및 태그": { zh: "规则与标签", en: "Rules and tags" },
  "스터디 제목": { zh: "学习小组标题", en: "Study title" },
  "예: [CS 기초] 기술 면접 대비 올인원 스터디": {
    zh: "例：[CS 基础] 技术面试全能学习小组",
    en: "Example: [CS Basics] All-in-one technical interview study",
  },
  "카테고리 선택": { zh: "选择类别", en: "Select category" },
  "스터디 목표 및 소개": { zh: "学习目标与介绍", en: "Study goals and introduction" },
  "스터디를 통해 얻고자 하는 바와 간략한 소개를 적어주세요.": {
    zh: "请写下希望通过学习小组获得的内容和简短介绍。",
    en: "Describe what you want to gain from the study and add a short introduction.",
  },
  "모집 인원": { zh: "招募人数", en: "Capacity" },
  "4                                   명": { zh: "4 人", en: "4 people" },
  "공개 여부": { zh: "公开设置", en: "Visibility" },
  "규칙을 작성해주세요!": { zh: "请填写规则！", en: "Write a rule!" },
  "태그 입력": { zh: "输入标签", en: "Enter tag" },
  "+ Add Tag": { zh: "+ 添加标签", en: "+ Add Tag", ko: "+ 태그 추가" },
  "파이썬 스터디": { zh: "Python 学习小组", en: "Python Study" },
  "Board": { zh: "公告板", en: "Board", ko: "게시판" },
  "Schedule": { zh: "日程", en: "Schedule", ko: "일정" },
  "Attendance": { zh: "出勤", en: "Attendance", ko: "출석" },
  "New Entry": { zh: "新建记录", en: "New Entry", ko: "새 글" },
  "출석체크": { zh: "出勤检查", en: "Attendance" },
  "팀원관리": { zh: "成员管理", en: "Member management" },
  "Team Board": { zh: "团队公告板", en: "Team Board", ko: "팀 게시판" },
  "실시간으로 팀원들과 소통하고 학습 자료를 공유하세요.": {
    zh: "与队友实时沟通并共享学习资料。",
    en: "Communicate with teammates in real time and share study materials.",
  },
  "+ Start Topic": { zh: "+ 发起话题", en: "+ Start Topic", ko: "+ 주제 시작" },
  "Attendance Board": { zh: "出勤看板", en: "Attendance Board", ko: "출석 게시판" },
  "팀원 출석체크를 관리하세요.": { zh: "管理团队成员出勤。", en: "Manage teammate attendance." },
  "Member Name": { zh: "成员姓名", en: "Member Name", ko: "멤버 이름" },
  "Present": { zh: "出席", en: "Present", ko: "출석" },
  "Absent": { zh: "缺席", en: "Absent", ko: "결석" },
  "Scheduled": { zh: "已安排", en: "Scheduled", ko: "예정" },
  "Member Board": { zh: "成员看板", en: "Member Board", ko: "팀원 게시판" },
  "스터디 팀원을 관리하세요.": { zh: "管理学习小组成员。", en: "Manage study teammates." },
  "Active Members": { zh: "活跃成员", en: "Active Members", ko: "활동 중인 멤버" },
  "Total: 3": { zh: "共 3 人", en: "Total: 3", ko: "총 3명" },
  "NAME": { zh: "姓名", en: "NAME", ko: "이름" },
  "ROLE": { zh: "角色", en: "ROLE", ko: "역할" },
  "ATTENDANCE RATE": { zh: "出勤率", en: "ATTENDANCE RATE", ko: "출석률" },
  "Join Requests": { zh: "加入申请", en: "Join Requests", ko: "가입 요청" },
  "Accept": { zh: "接受", en: "Accept", ko: "승인" },
  "Reject": { zh: "拒绝", en: "Reject", ko: "거절" },
  "초대 링크": { zh: "邀请链接", en: "Invite link" },
  "7 Active": { zh: "7 人在线", en: "7 Active", ko: "7명 활동 중" },
  "2h ago": { zh: "2 小时前", en: "2h ago", ko: "2시간 전" },
  "지난주에 수집한 데이터 전처리가 완료되었습니다. 시각화 자료를 확인하시고 추가하고 싶은 차트가 있다면 댓글로 알려주세요.": {
    zh: "上周收集的数据预处理已经完成。请查看可视化资料，如需增加图表请在评论中说明。",
    en: "The data collected last week has been preprocessed. Review the visualizations and comment if you want to add charts.",
  },
  "#추천": { zh: "#推荐", en: "#Recommended" },
  "#전체": { zh: "#全部", en: "#All" },
  "#알고리즘": { zh: "#算法", en: "#Algorithm" },
  "#영어회화": { zh: "#英语会话", en: "#English Speaking" },
  "#프론트엔드": { zh: "#前端", en: "#Frontend" },
  "#백엔드": { zh: "#后端", en: "#Backend" },
  "#UI/UX 디자인": { zh: "#UI/UX设计", en: "#UI/UX Design" },
  "#데이터사이언스": { zh: "#数据科学", en: "#Data Science" },
  "#코딩테스트": { zh: "#编程测试", en: "#Coding Test" },
  "#구현": { zh: "#实现", en: "#Implementation" },
  "#자격증": { zh: "#证书", en: "#Certification" },
  "#English": { zh: "#英语", en: "#English" },
  "#Macroeconomics": { zh: "#宏观经济学", en: "#Macroeconomics" },
  "# BUSINESS": { zh: "#商务", en: "# BUSINESS", ko: "# 비즈니스" },
  "# ENGLISH": { zh: "#英语", en: "# ENGLISH", ko: "# 영어" },
  "# CONVERSATION": { zh: "#会话", en: "# CONVERSATION", ko: "# 회화" },
  "포트폴리오 완성반: 리액트 심화": {
    zh: "作品集完成班：React 进阶",
    en: "Portfolio Finishing: Advanced React",
  },
  "비즈니스 영어 회화 실전": {
    zh: "商务英语会话实战",
    en: "Practical Business English Conversation",
  },
  "파이썬 알고리즘 문풀 (실버)": {
    zh: "Python 算法刷题（银级）",
    en: "Python Algorithm Practice (Silver)",
  },
  "백준 골드 달성반": { zh: "Baekjoon 金级冲刺班", en: "Baekjoon Gold Track" },
  "카카오 코테 대비반": { zh: "Kakao 编程测试备考班", en: "Kakao Coding Test Prep" },
  "Vue.js 입문부터 실전까지": { zh: "Vue.js 从入门到实战", en: "Vue.js From Basics to Practice" },
  "TypeScript 마스터하기": { zh: "TypeScript 掌握班", en: "Mastering TypeScript" },
  "스프링 부트 실전 프로젝트": { zh: "Spring Boot 实战项目", en: "Spring Boot Practical Project" },
  "NestJS 기본기 다지기": { zh: "NestJS 基础强化", en: "NestJS Fundamentals" },
  "데이터베이스 설계와 튜닝": { zh: "数据库设计与调优", en: "Database Design and Tuning" },
  "오픽 AL 달성 목표 스터디": { zh: "OPIc AL 目标学习组", en: "OPIc AL Goal Study" },
  "Figma를 활용한 UI/UX 기초": { zh: "用 Figma 学 UI/UX 基础", en: "UI/UX Basics with Figma" },
  "디자인 시스템 구축 실습": { zh: "设计系统搭建实战", en: "Design System Workshop" },
  "머신러닝 완벽 가이드": { zh: "机器学习完整指南", en: "Complete Machine Learning Guide" },
  "kaggle 컴페티션 도전방": { zh: "Kaggle 竞赛挑战组", en: "Kaggle Competition Challenge" },
  "실무 상황에서 바로 쓰는 영어 회화 스터디": {
    zh: "面向真实职场场景的英语会话学习组",
    en: "English conversation study for real workplace situations",
  },
  "이 스터디는 비즈니스 환경에서 자주 사용되는 영어 표현과 회화 패턴을 실전 중심으로 연습하는 것을 목표로 합니다. 회의, 이메일, 발표, 협상, 네트워킹 등 실제 업무 상황을 바탕으로 영어 표현을 익히고, 매주 롤플레이와 피드백을 통해 자연스럽게 말하는 능력을 향상시킵니다.": {
    zh: "本学习组以实战方式练习商务环境中常用的英语表达和会话模式。围绕会议、邮件、演示、谈判和社交等真实工作场景，每周通过角色扮演和反馈提升自然表达能力。",
    en: "This study focuses on practical English expressions and conversation patterns used in business settings. Through meetings, emails, presentations, negotiations, and networking scenarios, members improve natural speaking with weekly role play and feedback.",
  },
  "영어를 단순히 공부하는 것이 아니라 실제 상황에서 바로 사용할 수 있도록 반복 연습합니다.": {
    zh: "不只是学习英语，而是通过反复练习让它能在真实场景中直接使用。",
    en: "Rather than only studying English, members practice repeatedly so they can use it in real situations.",
  },
  "중앙도서관 4층 세미나실": { zh: "中央图书馆 4 层研讨室", en: "Central Library 4F Seminar Room" },
  "진행 방식": { zh: "进行方式", en: "Format" },
  "온라인 중심": { zh: "线上为主", en: "Mostly online" },
  "모임 시간": { zh: "会议时间", en: "Meeting time" },
  "매주 토요일 14:00": { zh: "每周六 14:00", en: "Every Saturday 14:00" },
  "현재 인원": { zh: "当前人数", en: "Current members" },
  "매주 회화 과제 준비": { zh: "每周准备会话任务", en: "Prepare weekly conversation assignments" },
  "모임 전 지정된 비즈니스 영어 표현과 대화 주제를 미리 학습합니다.": {
    zh: "每次聚会前提前学习指定的商务英语表达和对话主题。",
    en: "Study assigned business English expressions and conversation topics before each meeting.",
  },
  "영어로 말하기 우선": { zh: "优先使用英语交流", en: "Prioritize speaking English" },
  "스터디 시간에는 가능한 한 영어로 대화하며, 실수를 피하기보다 말하는 연습에 집중합니다.": {
    zh: "学习时间尽量使用英语交流，比起避免错误，更注重开口练习。",
    en: "During study time, members speak English as much as possible and focus on practice over avoiding mistakes.",
  },
  "적극적인 피드백 참여": { zh: "积极参与反馈", en: "Participate actively in feedback" },
  "롤플레이와 발표 후 서로의 표현, 발음, 전달 방식에 대해 간단한 피드백을 제공합니다.": {
    zh: "角色扮演和发表后，互相对表达、发音和传达方式提供简短反馈。",
    en: "After role plays and presentations, members share brief feedback on expressions, pronunciation, and delivery.",
  },
  "지각 및 결석 사전 공유": { zh: "提前告知迟到或缺席", en: "Share lateness or absence in advance" },
  "참여가 어려운 경우 모임 전까지 팀 게시판이나 채팅방에 미리 공유합니다.": {
    zh: "无法参加时，请在聚会前通过团队公告板或聊天提前说明。",
    en: "If you cannot attend, share it on the team board or chat before the meeting.",
  },
  "김지우": { zh: "金智友", en: "Jiwon Kim" },
  "Lucas Meyer": { zh: "Lucas Meyer", en: "Lucas Meyer" },
  "Priya Sharma": { zh: "Priya Sharma", en: "Priya Sharma" },
  "Leader / UX Designer": { zh: "负责人 / UX 设计师", en: "Leader / UX Designer", ko: "리더 / UX 디자이너" },
  "Product Designer": { zh: "产品设计师", en: "Product Designer", ko: "프로덕트 디자이너" },
  "UI Engineer": { zh: "UI 工程师", en: "UI Engineer", ko: "UI 엔지니어" },
  "심리학으로 풀어보는 UX 분석 스터디": { zh: "用心理学分析 UX 学习组", en: "UX Analysis Through Psychology" },
  "TOEFL Speaking 80+ 정복하기": { zh: "托福口语 80+ 攻克班", en: "Conquer TOEFL Speaking 80+" },
  "React 프론트엔드 실전 프로젝트": { zh: "React 前端实战项目", en: "React Frontend Practical Project" },
  "기초 타이포그래피 원리 연구": { zh: "基础字体排印原理研究", en: "Basic Typography Principles Study" },
  "2일 전 지원함": { zh: "2 天前申请", en: "Applied 2 days ago" },
  "5일 전 지원함": { zh: "5 天前申请", en: "Applied 5 days ago" },
  "PENDING": { zh: "待处理", en: "PENDING", ko: "대기 중" },
  "ACCEPTED": { zh: "已接受", en: "ACCEPTED", ko: "승인됨" },
  "IT / 프로그래밍": { zh: "IT / 编程", en: "IT / Programming" },
  "언어 / 어학": { zh: "语言 / 外语", en: "Language / Linguistics" },
  "취업 / 직무": { zh: "就业 / 职务", en: "Career / Job skills" },
  "기타": { zh: "其他", en: "Other" },
  "전체 공개": { zh: "公开", en: "Public" },
  "비공개 (링크 전용)": { zh: "私密（仅链接）", en: "Private (link only)" },
  "매주 수요일 16:00": { zh: "每周三 16:00", en: "Every Wednesday 16:00" },
  "스터디 기간": { zh: "学习周期", en: "Study period" },
  "4/29 ~ 6/17": { zh: "4/29 ~ 6/17", en: "4/29 ~ 6/17" },
  "진행방식": { zh: "进行方式", en: "Format" },
  "온라인": { zh: "线上", en: "Online" },
  "중간 발표 자료 준비 및 데이터 분석 공유": {
    zh: "期中发表资料准备与数据分析共享",
    en: "Preparing midterm presentation materials and sharing data analysis",
  },
  "알고리즘 구현 중 엣지 케이스 처리 질문": {
    zh: "算法实现中的边界情况处理问题",
    en: "Question about handling edge cases in algorithm implementation",
  },
  "RESEARCH": { zh: "研究", en: "RESEARCH", ko: "리서치" },
  "ENGINEERING": { zh: "工程", en: "ENGINEERING", ko: "엔지니어링" },
  "김지수": { zh: "金智秀", en: "Jisu Kim" },
  "박민호": { zh: "朴民浩", en: "Minho Park" },
  "박민재": { zh: "朴民宰", en: "Minjae Park" },
  "이리나": { zh: "伊丽娜", en: "Irina Lee" },
  "최하늘": { zh: "崔天空", en: "Haneul Choi" },
  "김민수": { zh: "金民秀", en: "Minsu Kim" },
  "이서윤": { zh: "李瑞允", en: "Seoyun Lee" },
  "박지호": { zh: "朴志浩", en: "Jiho Park" },
  "정우진": { zh: "郑宇镇", en: "Woojin Jung" },
  "한나래": { zh: "韩娜莱", en: "Narae Han" },
  "신청일: 2024.03.12": { zh: "申请日：2024.03.12", en: "Applied: 2024.03.12" },
  "신청일: 2024.03.11": { zh: "申请日：2024.03.11", en: "Applied: 2024.03.11" },
  "10/19 (Next)": { zh: "10/19（下次）", en: "10/19 (Next)" },
  "LEADER": { zh: "负责人", en: "LEADER", ko: "리더" },
  "RESEARCHER": { zh: "研究员", en: "RESEARCHER", ko: "연구원" },
  "DESIGNER": { zh: "设计师", en: "DESIGNER", ko: "디자이너" },
};

Object.assign(textTranslations, {
  "Globe Icon": { zh: "地球图标", en: "Globe Icon", ko: "지구본 아이콘" },
  "프로필 메뉴 열기": { zh: "打开个人菜单", en: "Open profile menu" },
  "로그아웃": { zh: "退出登录", en: "Log out" },
  "관심 태그를 입력해 주세요": { zh: "请输入兴趣标签", en: "Enter interest tags" },
  "관심 태그를 하나 이상 선택해 주세요.": { zh: "请至少选择一个兴趣标签。", en: "Select at least one interest tag." },
  "프로필을 완성해요": { zh: "完善个人资料", en: "Complete your profile" },
  "스터디 매칭에 사용할 기본 정보를 입력해 주세요.": { zh: "请输入用于学习小组匹配的基本信息。", en: "Enter the basic information used for study matching." },
  "학교": { zh: "学校", en: "School" },
  "전공": { zh: "专业", en: "Major" },
  "자기소개": { zh: "自我介绍", en: "Bio" },
  "관심 분야와 함께 공부하고 싶은 목표를 적어주세요.": { zh: "写下你的兴趣领域和学习目标。", en: "Write your interests and study goals." },
  "학교를 입력해 주세요.": { zh: "请输入学校。", en: "Enter your school." },
  "전공을 입력해 주세요.": { zh: "请输入专业。", en: "Enter your major." },
  "자기소개를 입력해 주세요.": { zh: "请输入自我介绍。", en: "Enter your bio." },
  "비밀번호를 다시 확인해 주세요.": { zh: "请重新确认密码。", en: "Check your password again." },
  "스터디 목록을 불러오는 중입니다.": { zh: "正在加载学习小组列表。", en: "Loading studies." },
  "스터디 목록을 불러오지 못해 샘플 데이터를 표시합니다.": { zh: "无法加载学习小组列表，正在显示示例数据。", en: "Could not load studies, showing sample data." },
  "지원일 확인 불가": { zh: "无法确认申请日期", en: "Application date unavailable" },
  "지원": { zh: "申请", en: "applied" },
  "모집중": { zh: "招募中", en: "Open" },
  "마감": { zh: "已截止", en: "Closed" },
  "스터디 상세 정보를 불러오는 중입니다.": { zh: "正在加载学习小组详情。", en: "Loading study details." },
  "스터디 상세 정보를 불러오지 못해 샘플 데이터를 표시합니다.": { zh: "无法加载学习小组详情，正在显示示例数据。", en: "Could not load study details, showing sample data." },
  "샘플 스터디에는 입장할 수 없습니다.": { zh: "示例学习小组无法进入。", en: "You cannot enter a sample study." },
  "샘플 스터디에는 지원할 수 없습니다.": { zh: "示例学习小组无法申请。", en: "You cannot apply to a sample study." },
  "처리 중...": { zh: "处理中...", en: "Processing..." },
  "신청 취소": { zh: "取消申请", en: "Cancel application" },
  "스터디 지원이 완료되었습니다.": { zh: "学习小组申请已完成。", en: "Study application submitted." },
  "스터디 지원을 취소했습니다.": { zh: "已取消学习小组申请。", en: "Study application canceled." },
  "스터디에 지원하지 못했습니다.": { zh: "未能申请学习小组。", en: "Could not apply to the study." },
  "스터디 지원을 취소하지 못했습니다.": { zh: "未能取消学习小组申请。", en: "Could not cancel the study application." },
  "스터디를 생성하지 못했습니다. 입력 내용을 확인한 뒤 다시 시도해 주세요.": {
    zh: "未能创建学习小组。请检查输入内容后重试。",
    en: "Could not create the study. Check your input and try again.",
  },
  "제목, 소개, 태그, 사용 언어/기술, 모임 주기를 모두 입력해 주세요.": {
    zh: "请填写标题、介绍、标签、使用语言/技术和会议周期。",
    en: "Enter title, introduction, tags, languages/tech, and meeting cycle.",
  },
  "사용 언어/기술": { zh: "使用语言/技术", en: "Languages/Tech" },
  "예: JavaScript, React, Spring": { zh: "例：JavaScript, React, Spring", en: "Example: JavaScript, React, Spring" },
  "생성 중...": { zh: "正在创建...", en: "Creating..." },
  "모임 주기": { zh: "会议周期", en: "Meeting cycle" },
  "예: 매주 수요일 16:00": { zh: "例：每周三 16:00", en: "Example: Every Wednesday 16:00" },
  "스터디 정보 보기": { zh: "查看学习小组信息", en: "View study info" },
  "게시글 작성": { zh: "写帖子", en: "Create post" },
  "제목을 입력하세요": { zh: "请输入标题", en: "Enter a title" },
  "게시글 제목": { zh: "帖子标题", en: "Post title" },
  "새로운 소식을 공유해보세요...": { zh: "分享新的消息...", en: "Share something new..." },
  "게시글 내용": { zh: "帖子内容", en: "Post content" },
  "게시글 유형": { zh: "帖子类型", en: "Post type" },
  "공지": { zh: "公告", en: "Notice" },
  "자유": { zh: "自由", en: "Free" },
  "게시글을 불러오는 중입니다.": { zh: "正在加载帖子。", en: "Loading posts." },
  "아직 게시글이 없습니다.": { zh: "还没有帖子。", en: "No posts yet." },
  "게시글을 작성했습니다.": { zh: "帖子已发布。", en: "Post created." },
  "게시글을 작성하지 못했습니다.": { zh: "未能发布帖子。", en: "Could not create post." },
  "게시글을 수정했습니다.": { zh: "帖子已更新。", en: "Post updated." },
  "게시글을 수정하지 못했습니다.": { zh: "未能更新帖子。", en: "Could not update post." },
  "게시글을 삭제했습니다.": { zh: "帖子已删除。", en: "Post deleted." },
  "게시글을 삭제하지 못했습니다.": { zh: "未能删除帖子。", en: "Could not delete post." },
  "수정": { zh: "编辑", en: "Edit" },
  "삭제": { zh: "删除", en: "Delete" },
  "댓글": { zh: "评论", en: "Comments" },
  "아직 댓글이 없습니다.": { zh: "还没有评论。", en: "No comments yet." },
  "댓글 입력": { zh: "输入评论", en: "Comment input" },
  "댓글 전송": { zh: "发送评论", en: "Send comment" },
  "Write a comment...": { zh: "写评论...", en: "Write a comment...", ko: "댓글을 입력하세요..." },
  "출석정보 저장되었습니다.": { zh: "出勤信息已保存。", en: "Attendance saved." },
  "출석 저장": { zh: "保存出勤", en: "Save attendance" },
  "JOINED": { zh: "加入日期", en: "JOINED", ko: "가입일" },
  "스터디 삭제": { zh: "删除学习小组", en: "Delete study" },
  "삭제 중...": { zh: "正在删除...", en: "Deleting..." },
  "팀 나가기": { zh: "退出团队", en: "Leave team" },
  "나가는 중...": { zh: "正在退出...", en: "Leaving..." },
  "kick": { zh: "移除", en: "kick", ko: "내보내기" },
  "가입 요청을 불러오는 중입니다.": { zh: "正在加载加入申请。", en: "Loading join requests." },
  "대기 중인 가입 요청이 없습니다.": { zh: "没有待处理的加入申请。", en: "No pending join requests." },
  "팀원 목록을 불러오는 중입니다.": { zh: "正在加载成员列表。", en: "Loading members." },
  "표시할 팀원이 없습니다.": { zh: "没有可显示的成员。", en: "No members to show." },
  "7 Active": { zh: "7 人在线", en: "7 Active", ko: "7명 활동 중" },
  "Total": { zh: "共", en: "Total", ko: "총" },
  "명": { zh: "人", en: " people" },
  "스터디 정보 없음": { zh: "无学习小组信息", en: "No study info" },
  "태그": { zh: "标签", en: "Tags" },
  "출석 상태": { zh: "出勤状态", en: "attendance status" },
  "present": { zh: "出席", en: "present", ko: "출석" },
  "absent": { zh: "缺席", en: "absent", ko: "결석" },
  "scheduled": { zh: "已安排", en: "scheduled", ko: "예정" },
  "NOTICE": { zh: "公告", en: "NOTICE", ko: "공지" },
  "FREE": { zh: "自由", en: "FREE", ko: "자유" },
  "프로필 정보를 불러오는 중입니다.": { zh: "正在加载个人资料。", en: "Loading profile." },
  "프로필을 먼저 저장해 주세요.": { zh: "请先保存个人资料。", en: "Save your profile first." },
  "프로필 정보를 불러오지 못했습니다.": { zh: "无法加载个人资料。", en: "Could not load profile." },
  "프로필을 저장하지 못했습니다.": { zh: "未能保存个人资料。", en: "Could not save profile." },
  "프로필을 삭제하지 못했습니다.": { zh: "未能删除个人资料。", en: "Could not delete profile." },
  "지원 현황을 불러오는 중입니다.": { zh: "正在加载申请状态。", en: "Loading applications." },
  "지원 현황을 불러오지 못했습니다.": { zh: "无法加载申请状态。", en: "Could not load applications." },
  "아직 지원한 스터디가 없습니다.": { zh: "还没有申请的学习小组。", en: "No study applications yet." },
  "취소 중...": { zh: "正在取消...", en: "Canceling..." },
  "로그인 후 이용할 수 있습니다.": { zh: "登录后可以使用。", en: "Log in to continue." },
  "로그인 후 스터디를 생성할 수 있습니다.": { zh: "登录后可以创建学习小组。", en: "Log in to create a study." },
  "서버에서 요청 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.": {
    zh: "服务器处理请求时发生错误。请稍后重试。",
    en: "The server hit an error while processing the request. Try again later.",
  },
  "로그인 후 프로필을 확인할 수 있습니다.": { zh: "登录后可以查看个人资料。", en: "Log in to view your profile." },
  "프로필 정보를 찾을 수 없습니다.": { zh: "找不到个人资料。", en: "Profile not found." },
  "서버에서 프로필 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.": {
    zh: "服务器处理个人资料时发生错误。请稍后重试。",
    en: "The server hit an error while processing your profile. Try again later.",
  },
  "게시글 목록을 불러오지 못했습니다.": { zh: "无法加载帖子列表。", en: "Could not load posts." },
  "댓글을 불러오지 못했습니다.": { zh: "无法加载评论。", en: "Could not load comments." },
  "댓글을 작성했습니다.": { zh: "评论已发布。", en: "Comment created." },
  "댓글을 작성하지 못했습니다.": { zh: "未能发布评论。", en: "Could not create comment." },
  "댓글을 수정했습니다.": { zh: "评论已更新。", en: "Comment updated." },
  "댓글을 수정하지 못했습니다.": { zh: "未能更新评论。", en: "Could not update comment." },
  "댓글을 삭제했습니다.": { zh: "评论已删除。", en: "Comment deleted." },
  "댓글을 삭제하지 못했습니다.": { zh: "未能删除评论。", en: "Could not delete comment." },
  "샘플 팀에서는 게시글을 저장할 수 없습니다.": { zh: "示例团队无法保存帖子。", en: "You cannot save posts in a sample team." },
  "샘플 팀에서는 댓글을 저장할 수 없습니다.": { zh: "示例团队无法保存评论。", en: "You cannot save comments in a sample team." },
  "팀 보드 접근 권한이 없습니다.": { zh: "没有团队公告板访问权限。", en: "You do not have access to this team board." },
  "게시판 권한이 없습니다.": { zh: "没有公告板权限。", en: "You do not have board permission." },
  "대상을 찾을 수 없습니다.": { zh: "找不到对象。", en: "Target not found." },
  "서버에서 게시판 요청 처리 중 오류가 발생했습니다.": { zh: "服务器处理公告板请求时发生错误。", en: "The server hit an error while processing the board request." },
  "스터디를 삭제할 권한이 없습니다.": { zh: "没有删除学习小组的权限。", en: "You do not have permission to delete this study." },
  "삭제할 스터디를 찾을 수 없습니다.": { zh: "找不到要删除的学习小组。", en: "Could not find the study to delete." },
  "서버에서 스터디 삭제 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.": {
    zh: "服务器删除学习小组时发生错误。请稍后重试。",
    en: "The server hit an error while deleting the study. Try again later.",
  },
  "샘플 팀에서는 팀원 목록을 불러올 수 없습니다.": { zh: "示例团队无法加载成员列表。", en: "Sample teams cannot load member lists." },
  "팀원 목록을 불러오지 못했습니다.": { zh: "无法加载成员列表。", en: "Could not load members." },
  "샘플 팀에서는 가입 요청을 불러올 수 없습니다.": { zh: "示例团队无法加载加入申请。", en: "Sample teams cannot load join requests." },
  "가입 요청을 불러오지 못했습니다.": { zh: "无法加载加入申请。", en: "Could not load join requests." },
  "스터디를 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.": {
    zh: "未能删除学习小组。请稍后重试。",
    en: "Could not delete the study. Try again later.",
  },
  "가입 요청을 승인했습니다.": { zh: "已批准加入申请。", en: "Join request approved." },
  "가입 요청을 승인하지 못했습니다.": { zh: "未能批准加入申请。", en: "Could not approve join request." },
  "가입 요청을 거절했습니다.": { zh: "已拒绝加入申请。", en: "Join request rejected." },
  "가입 요청을 거절하지 못했습니다.": { zh: "未能拒绝加入申请。", en: "Could not reject join request." },
  "팀원을 내보냈습니다.": { zh: "已移除成员。", en: "Member removed." },
  "팀원을 내보내지 못했습니다.": { zh: "未能移除成员。", en: "Could not remove member." },
  "팀에서 나갔습니다.": { zh: "已退出团队。", en: "Left the team." },
  "팀에서 나가지 못했습니다.": { zh: "未能退出团队。", en: "Could not leave the team." },
  "게시글 제목을 수정해 주세요.": { zh: "请修改帖子标题。", en: "Edit the post title." },
  "게시글 내용을 수정해 주세요.": { zh: "请修改帖子内容。", en: "Edit the post content." },
  "게시글을 삭제하시겠습니까?": { zh: "确定要删除帖子吗？", en: "Delete this post?" },
  "댓글 내용을 수정해 주세요.": { zh: "请修改评论内容。", en: "Edit the comment." },
  "댓글을 삭제하시겠습니까?": { zh: "确定要删除评论吗？", en: "Delete this comment?" },
  "스터디를 삭제하시겠습니까? 삭제 후에는 되돌릴 수 없습니다.": {
    zh: "确定要删除学习小组吗？删除后无法恢复。",
    en: "Delete this study? This cannot be undone.",
  },
  "거절 사유를 입력해 주세요.": { zh: "请输入拒绝原因。", en: "Enter a rejection reason." },
  "조건이 맞지 않아 거절합니다.": { zh: "因条件不符而拒绝。", en: "Rejected because the conditions do not match." },
  "팀에서 내보내시겠습니까?": { zh: "确定要将 {name} 移出团队吗？", en: "Remove {name} from the team?" },
  "이 팀에서 나가시겠습니까?": { zh: "确定要退出这个团队吗？", en: "Leave this team?" },
} satisfies Record<string, Partial<Record<LanguageCode, string>>>);

const LanguageContext = createContext<LanguageContextValue | null>(null);

function getInitialLanguage(): LanguageCode {
  if (typeof window === "undefined") return "ko";
  const storedLanguage = window.localStorage.getItem(STORAGE_KEY);
  return storedLanguage === "en" || storedLanguage === "zh" ? storedLanguage : "ko";
}

function getLanguageOption(code: LanguageCode) {
  return LANGUAGE_OPTIONS.find((option) => option.code === code) ?? LANGUAGE_OPTIONS[0];
}

function getNextLanguage(language: LanguageCode): LanguageCode {
  if (language === "ko") return "en";
  if (language === "en") return "zh";
  return "ko";
}

export function translateText(value: string, language: LanguageCode): string {
  const direct = textTranslations[value]?.[language];
  if (direct) return direct;
  if (language === "ko") return value;

  const topicTitleMatch = value.match(/^(.+) 추천 스터디$/);
  if (topicTitleMatch) {
    const topic = translateText(topicTitleMatch[1], language).replace("#", "");
    return language === "zh" ? `${topic} 推荐学习小组` : `${topic} recommended studies`;
  }

  const topicSubtitleMatch = value.match(/^(#?.+) 관련 선별된 스터디 그룹입니다\.$/);
  if (topicSubtitleMatch) {
    const topic = translateText(topicSubtitleMatch[1], language);
    return language === "zh"
      ? `与 ${topic} 相关的精选学习小组。`
      : `Curated study groups related to ${topic}.`;
  }

  const dateAppliedMatch = value.match(/^(\d{4}\.\d{2}\.\d{2}) 지원$/);
  if (dateAppliedMatch) {
    return language === "zh" ? `${dateAppliedMatch[1]} 申请` : `Applied ${dateAppliedMatch[1]}`;
  }

  const weekMatch = value.match(/^(\d+)주$/);
  if (weekMatch) {
    return language === "zh" ? `${weekMatch[1]}周` : `${weekMatch[1]} weeks`;
  }

  const weekStudyMatch = value.match(/^(\d+)주 스터디$/);
  if (weekStudyMatch) {
    return language === "zh" ? `${weekStudyMatch[1]}周学习小组` : `${weekStudyMatch[1]}-week study`;
  }

  const memberMatch = value.match(/^(\d+)\s*\/\s*(\d+)명$/);
  if (memberMatch) {
    return language === "zh"
      ? `${memberMatch[1]} / ${memberMatch[2]}人`
      : `${memberMatch[1]} / ${memberMatch[2]} people`;
  }

  return value;
}

export function LanguageProvider({ children }: { children: ReactNode }) {
  const [language, setLanguageState] = useState<LanguageCode>(getInitialLanguage);

  useEffect(() => {
    document.documentElement.lang = language === "zh" ? "zh-CN" : language;
    window.localStorage.setItem(STORAGE_KEY, language);
  }, [language]);

  const setLanguage = useCallback((nextLanguage: LanguageCode) => {
    setLanguageState(nextLanguage);
  }, []);

  const toggleLanguage = useCallback(() => {
    setLanguageState((current) => getNextLanguage(current));
  }, []);

  const translate = useCallback((value: string) => translateText(value, language), [language]);
  const currentLanguage = getLanguageOption(language);
  const nextLanguage = getLanguageOption(getNextLanguage(language));

  const value = useMemo(
    () => ({
      language,
      currentLanguage,
      nextLanguage,
      setLanguage,
      toggleLanguage,
      translate,
    }),
    [currentLanguage, language, nextLanguage, setLanguage, toggleLanguage, translate]
  );

  return <LanguageContext.Provider value={value}>{children}</LanguageContext.Provider>;
}

export function useLanguage() {
  const context = useContext(LanguageContext);
  if (!context) throw new Error("useLanguage must be used inside LanguageProvider");
  return context;
}
