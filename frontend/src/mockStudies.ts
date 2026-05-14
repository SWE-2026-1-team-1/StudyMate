import type { Study } from "./types";

export const allMockStudies: Study[] = [
  // 알고리즘
  {
    title: "파이썬 알고리즘 문풀 (실버)",
    tags: ["#알고리즘", "#PYTHON", "#코딩테스트"],
    people: "4/6",
    duration: "16주",
    tone: "algorithm",
  },
  {
    title: "백준 골드 달성반",
    tags: ["#알고리즘", "#JAVA", "#C++"],
    people: "8/10",
    duration: "12주",
    tone: "algorithm",
  },
  {
    title: "카카오 코테 대비반",
    tags: ["#알고리즘", "#코딩테스트", "#구현"],
    people: "3/5",
    duration: "8주",
    tone: "algorithm",
  },
  
  // 프론트엔드
  {
    title: "포트폴리오 완성반: 리액트 심화",
    tags: ["#프론트엔드", "#REACT", "#JAVASCRIPT"],
    people: "8/10",
    duration: "12주",
    tone: "tech",
  },
  {
    title: "Vue.js 입문부터 실전까지",
    tags: ["#프론트엔드", "#VUE", "#WEB"],
    people: "2/6",
    duration: "10주",
    tone: "tech",
  },
  {
    title: "TypeScript 마스터하기",
    tags: ["#프론트엔드", "#TYPESCRIPT", "#WEB"],
    people: "5/8",
    duration: "8주",
    tone: "tech",
  },

  // 백엔드
  {
    title: "스프링 부트 실전 프로젝트",
    tags: ["#백엔드", "#JAVA", "#SPRING"],
    people: "6/8",
    duration: "12주",
    tone: "tech",
  },
  {
    title: "NestJS 기본기 다지기",
    tags: ["#백엔드", "#NODE.JS", "#NESTJS"],
    people: "3/4",
    duration: "10주",
    tone: "tech",
  },
  {
    title: "데이터베이스 설계와 튜닝",
    tags: ["#백엔드", "#DATABASE", "#SQL"],
    people: "2/5",
    duration: "8주",
    tone: "tech",
  },

  // 영어회화
  {
    title: "비즈니스 영어 회화 실전",
    tags: ["#영어회화", "#BUSINESS", "#CONVERSATION"],
    people: "4/6",
    duration: "8주",
    tone: "english",
  },
  {
    title: "오픽 AL 달성 목표 스터디",
    tags: ["#영어회화", "#OPIC", "#자격증"],
    people: "3/4",
    duration: "4주",
    tone: "english",
  },

  // UI/UX 디자인
  {
    title: "Figma를 활용한 UI/UX 기초",
    tags: ["#UI/UX 디자인", "#FIGMA", "#DESIGN"],
    people: "5/6",
    duration: "8주",
    tone: "design",
  },
  {
    title: "디자인 시스템 구축 실습",
    tags: ["#UI/UX 디자인", "#SYSTEM", "#PROTOTYPING"],
    people: "2/4",
    duration: "10주",
    tone: "design",
  },

  // 데이터사이언스
  {
    title: "머신러닝 완벽 가이드",
    tags: ["#데이터사이언스", "#PYTHON", "#ML"],
    people: "7/10",
    duration: "16주",
    tone: "tech",
  },
  {
    title: "kaggle 컴페티션 도전방",
    tags: ["#데이터사이언스", "#KAGGLE", "#DATA"],
    people: "3/5",
    duration: "12주",
    tone: "tech",
  }
];