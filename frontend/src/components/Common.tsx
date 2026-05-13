import { screens } from "../data";
import type { ScreenId, Study } from "../types";
import type { ReactNode } from "react";
import { NavLink, useLocation } from "react-router-dom";
import { getTopLevelRoute, ROUTE_PATHS } from "../routes/routingMap";
import graduationCapIcon from "../assets/graduation-cap.svg";
import userProfileIcon from "../assets/user_profile.svg";
import techStudyIcon from "../assets/tech_study.svg";
import englishStudyIcon from "../assets/english_study.svg";
import algorithmStudyIcon from "../assets/algorithm_study.svg";

type Navigate = (screen: ScreenId) => void;
type TopLevelNav = "main" | "explore" | "create" | "mypage";

export function ScreenSwitcher({ current, onChange }: { current: ScreenId; onChange: Navigate }) {
  return (
    <nav className="screen-switcher" aria-label="Figma 12 screens">
      {screens.map((item) => (
        <button className={current === item.id ? "active" : ""} key={item.id} type="button" onClick={() => onChange(item.id)}>
          <small>{item.group}</small>
          {item.label}
        </button>
      ))}
    </nav>
  );
}

export function Frame({ children }: { children: ReactNode }) {
  return <div className="figma-frame">{children}</div>;
}

export function TopBar() {
  const { pathname } = useLocation();
  const activeNav: TopLevelNav = getTopLevelRoute(pathname);
  const activeIndex = activeNav === "main" ? 0 : activeNav === "explore" ? 1 : activeNav === "create" ? 2 : 3;

  return (
    <header className="topbar">
      <NavLink className="brand-group" to={ROUTE_PATHS.home}>
        <div className="brand-img"></div>
        <span className="brand-link">StudyMate</span>
      </NavLink>
      <nav className={`top-nav active-${activeIndex}`} aria-label="주요 화면">
        <NavLink className={activeNav === "main" ? "active" : ""} to={ROUTE_PATHS.home}>Home</NavLink>
        <NavLink className={activeNav === "explore" ? "active" : ""} to={ROUTE_PATHS.studies}>Search</NavLink>
        <NavLink className={activeNav === "create" ? "active" : ""} to={ROUTE_PATHS.createBasic}>Create Study</NavLink>
        <NavLink className={activeNav === "mypage" ? "active" : ""} to={ROUTE_PATHS.mypage}>My Page</NavLink>
      </nav>
      <div className="top-actions">
        <span className="lang-toggle"><b>KR</b><b>EN</b></span>
        <NavLink className="avatar-button" aria-label="마이페이지로 이동" to={ROUTE_PATHS.mypage}>
          <Avatar className="mini-avatar" name="user" />
        </NavLink>
      </div>
    </header>
  );
}

export function Shell({ children, sidebar = false }: { children: ReactNode; sidebar?: boolean }) {
  return (
    <div className={sidebar ? "app-layout has-sidebar" : "app-layout"}>
      {sidebar && <StudySideNav />}
      {children}
    </div>
  );
}

export function StudySideNav() {
  return (
    <aside className="study-side">
      <div className="team-logo"><span>✣</span><b>파이썬 스터디</b><small>CS302 PROJECT</small></div>
      <a className="active">Board</a>
      <a>Schedule</a>
      <a>Attendance</a>
      <button type="button">New Entry</button>
    </aside>
  );
}

export function Hero({ searchValue, onSearchChange, onSearchFocus }: { searchValue?: string; onSearchChange?: (value: string) => void; onSearchFocus?: () => void }) {
  return (
    <section className="hero content-container">
      <h1><span>StudyMate</span>맞춤 스터디 탐색</h1>
      <p>자신의 목표에 맞는 스터디 팀을 찾고, 동료들과 함께 더 멀리 나아가세요.</p>
      <label className="search-box">
        <i />
        <input
          placeholder="관심 있는 스터디 주제나 기술 스택을 검색해보세요"
          value={searchValue}
          onChange={(event) => onSearchChange?.(event.target.value)}
          onClick={onSearchFocus}
          onFocus={onSearchFocus}
        />
      </label>
    </section>
  );
}

export function SectionTitle({ title, subtitle, action }: { title: string; subtitle?: string; action?: string }) {
  return (
    <header className="section-title">
      <div>
        <h2>{title}</h2>
        {subtitle && <p>{subtitle}</p>}
      </div>
      {action && <button type="button">{action}</button>}
    </header>
  );
}

export function StudyCard({ study, action, onAction }: { study: Study; action: string; onAction: () => void }) {
  return (
    <article className="study-card">
      <StudyThumb tone={study.tone} />
      <div className="tag-row">{study.tags.map((tag) => <span key={tag}>{tag}</span>)}</div>
      <h3>{study.title}</h3>
      <p><span className="meta people">{study.people}</span><span className="meta time">{study.duration}</span></p>
      <button type="button" onClick={onAction}>{action}</button>
    </article>
  );
}

import studyGroupIcon from "../assets/study_group.svg";

export function Illustration() {
  return (
    <div className="illustration" aria-hidden="true">
      <img src={studyGroupIcon} alt="Study Group" className="study-group-icon" />
    </div>
  );
}

import person1 from "../assets/person1.svg";
import person2 from "../assets/person2.svg";
import person3 from "../assets/person3.svg";

export function AvatarStack() {
  return (
    <span className="avatar-stack">
      <img src={person1} alt="User 1" className="avatar" />
      <img src={person2} alt="User 2" className="avatar" />
      <img src={person3} alt="User 3" className="avatar" />
    </span>
  );
}

export function Avatar({ name, className = "" }: { name: string; className?: string }) {
  if (name === "user") {
    return <img src={userProfileIcon} alt="" className={`avatar user-profile-icon ${className}`} aria-hidden="true" />;
  }

  const initial = name === "user" ? "U" : name.slice(0, 1).toUpperCase();
  return <i className={`avatar portrait-${name} ${className}`} aria-hidden="true">{initial}</i>;
}

function StudyThumb({ tone }: { tone: Study["tone"] }) {
  const thumbByTone: Record<Study["tone"], string> = {
    tech: techStudyIcon,
    english: englishStudyIcon,
    algorithm: algorithmStudyIcon,
    design: techStudyIcon,
  };

  return (
    <div className={`study-thumb ${tone}`}>
      <img src={thumbByTone[tone]} alt="" className="study-thumb-image" />
    </div>
  );
}

export function Field({ label, placeholder, type = "text", icon, autoComplete }: { label: string; placeholder: string; type?: string; icon?: string; autoComplete?: string }) {
  return (
    <label className="field">
      {label}
      <span className="input-wrap">
        <input autoComplete={autoComplete} placeholder={placeholder} type={type} />
        {icon && <em>{icon}</em>}
      </span>
    </label>
  );
}

export function PageHeading({ title, subtitle }: { title: string; subtitle: string }) {
  return <header className="page-heading"><h1>{title}</h1><p>{subtitle}</p></header>;
}

export function Panel({ title, className = "", children }: { title: string; className?: string; children: ReactNode }) {
  return <section className={`panel ${className}`}><h2>{title}</h2>{children}</section>;
}

export function StatusRow({ title, meta, status }: { title: string; meta: string; status: string }) {
  return <div className="status-row"><span className="icon-tile" /><div><b>{title}</b><small>{meta}</small></div><em>{status}</em></div>;
}
