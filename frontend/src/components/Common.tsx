import { screens } from "../data";
import type { ScreenId, Study } from "../types";
import { useState, type ChangeEvent, type ReactNode } from "react";
import { NavLink, useLocation, useNavigate } from "react-router-dom";
import { authApi } from "../api/auth";
import { getTopLevelRoute, ROUTE_PATHS } from "../routes/routingMap";
import graduationCapIcon from "../assets/graduation-cap.svg";
import userProfileIcon from "../assets/user_profile.svg";
import techStudyIcon from "../assets/tech_study.svg";
import englishStudyIcon from "../assets/english_study.svg";
import algorithmStudyIcon from "../assets/algorithm_study.svg";
import globeIcon from "../assets/language.svg";

type Navigate = (screen: ScreenId) => void;
type TopLevelNav = "main" | "explore" | "create" | "mypage";

type Language = { code: string; label: string };
const LANGUAGES: Language[] = [
  { code: "KO", label: "한국어" },
  { code: "EN", label: "English" },
  { code: "ZH", label: "中文" },
];

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
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const activeNav: TopLevelNav = getTopLevelRoute(pathname);
  const activeIndex = activeNav === "main" ? 0 : activeNav === "explore" ? 1 : activeNav === "create" ? 2 : 3;
  const [lang, setLang] = useState(LANGUAGES[0]);
  const [showLangMenu, setShowLangMenu] = useState(false);
  const [showProfileMenu, setShowProfileMenu] = useState(false);

  const handleLogout = async () => {
    const refreshToken = localStorage.getItem("refreshToken");

    try {
      if (refreshToken) {
        await authApi.logout({ refreshToken });
      }
    } finally {
      localStorage.removeItem("accessToken");
      localStorage.removeItem("refreshToken");
      localStorage.removeItem("userId");
      setShowProfileMenu(false);
      navigate(ROUTE_PATHS.login, { replace: true });
    }
  };

  return (
    <header className="topbar">
      <NavLink className="brand-group" to={ROUTE_PATHS.home}>
        <div className="brand-img"></div>
        <span className="brand-link">StudyMate</span>
      </NavLink>
      <nav className={`top-nav active-${activeIndex}`} aria-label="주요 화면">
        <NavLink className={activeNav === "main" ? "active" : ""} to={ROUTE_PATHS.home} end>Home</NavLink>
        <NavLink className={activeNav === "explore" ? "active" : ""} to={ROUTE_PATHS.studies} end>Search</NavLink>
        <NavLink className={activeNav === "create" ? "active" : ""} to={ROUTE_PATHS.createBasic}>Create Study</NavLink>
        <NavLink className={activeNav === "mypage" ? "active" : ""} to={ROUTE_PATHS.mypage}>My Page</NavLink>
      </nav>
      <div className="top-actions">
        <div className="lang-selector">
          <button 
            className="language-pill" 
            type="button" 
            onClick={() => {
              setShowLangMenu(!showLangMenu);
              setShowProfileMenu(false);
            }}
          >
            <img src={globeIcon} alt="Globe Icon" className="globe-icon" />
            {lang.code} <i className="arrow-down" />
          </button>
          
          {showLangMenu && (
            <div className="lang-dropdown">
              {LANGUAGES.map(l => (
                <button 
                  key={l.code} 
                  type="button" 
                  onClick={() => {
                    setLang(l);
                    setShowLangMenu(false);
                  }}
                  className={lang.code === l.code ? "active" : ""}
                >
                  {l.label} ({l.code})
                </button>
              ))}
            </div>
          )}
        </div>
        <div className="profile-menu">
          <button
            className="avatar-button"
            type="button"
            aria-label="프로필 메뉴 열기"
            aria-expanded={showProfileMenu}
            onClick={() => {
              setShowProfileMenu(!showProfileMenu);
              setShowLangMenu(false);
            }}
          >
            <Avatar className="mini-avatar" name="user" />
          </button>
          {showProfileMenu && (
            <div className="profile-dropdown">
              <NavLink to={ROUTE_PATHS.mypage} onClick={() => setShowProfileMenu(false)}>마이페이지</NavLink>
              <button className="logout-menu-item" type="button" onClick={handleLogout}>로그아웃</button>
            </div>
          )}
        </div>
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

export function Hero({
  searchValue,
  onSearchChange,
  onSearchFocus,
  className = "",
  searchBoxClassName = "",
}: {
  searchValue?: string;
  onSearchChange?: (value: string) => void;
  onSearchFocus?: () => void;
  className?: string;
  searchBoxClassName?: string;
}) {
  return (
    <section className={`hero content-container ${className}`}>
      <h1><span>StudyMate</span>맞춤 스터디 탐색</h1>
      <p>자신의 목표에 맞는 스터디 팀을 찾고, 동료들과 함께 더 멀리 나아가세요.</p>
      <label className={`search-box ${searchBoxClassName}`}>
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

export function SectionTitle({ title, subtitle, action, onAction }: { title: string; subtitle?: string; action?: string; onAction?: () => void }) {
  return (
    <header className="section-title">
      <div>
        <h2>{title}</h2>
        {subtitle && <p>{subtitle}</p>}
      </div>
      {action && <button type="button" onClick={onAction}>{action}</button>}
    </header>
  );
}

export function StudyCard({ study, action, onAction }: { study: Study; action: string; onAction: () => void }) {
  return (
    <article className="study-card">
      <StudyThumb title={study.title} />
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

const portraitIcons: Record<string, string> = {
  a: person1,
  b: person2,
  c: person3,
};

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
  const { pathname } = useLocation();
  const isAuthPath = pathname === ROUTE_PATHS.login || pathname === ROUTE_PATHS.signup;
  const borderClass = isAuthPath ? "" : "avatar-borderless";

  if (name === "user") {
    return <img src={userProfileIcon} alt="" className={`avatar user-profile-icon ${className}`} aria-hidden="true" />;
  }

  if (portraitIcons[name]) {
    return <img src={portraitIcons[name]} alt="" className={`avatar ${borderClass} ${className}`} aria-hidden="true" />;
  }

  const initial = name === "user" ? "U" : name.slice(0, 1).toUpperCase();
  return <i className={`avatar portrait-${name} ${className}`} aria-hidden="true">{initial}</i>;
}

function StudyThumb({ title }: { title: string }) {
  const pastelColors = [
    "#fdf2f8", // 파스텔 핑크
    "#eef2ff", // 파스텔 블루
    "#f0fdf4", // 파스텔 민트
    "#fffbeb", // 파스텔 옐로우
    "#f5f3ff", // 파스텔 퍼플
  ];

  // 타이틀의 문자열 합을 이용해 색상을 일정하게 돌려가며 선택
  const hash = title.split("").reduce((acc, char) => acc + char.charCodeAt(0), 0);
  const bgColor = pastelColors[hash % pastelColors.length];

  return (
    <div className="study-thumb text-thumb" style={{ background: bgColor }}>
      <span className="thumb-title">{title}</span>
    </div>
  );
}

export function Field({
  label,
  placeholder,
  type = "text",
  icon,
  autoComplete,
  value,
  onChange,
  disabled = false,
}: {
  label: string;
  placeholder: string;
  type?: string;
  icon?: string;
  autoComplete?: string;
  value?: string;
  onChange?: (e: ChangeEvent<HTMLInputElement>) => void;
  disabled?: boolean;
}) {
  return (
    <label className="field">
      {label}
      <span className="input-wrap">
        <input autoComplete={autoComplete} disabled={disabled} placeholder={placeholder} type={type} value={value} onChange={onChange} />
        {icon && <em>{icon}</em>}
      </span>
    </label>
  );
}

export function PageHeading({ title, subtitle }: { title: string; subtitle: string }) {
  return <header className="page-heading"><h1>{title}</h1><p>{subtitle}</p></header>;
}

export function Panel({ title, className = "", children }: { title: string; className?: string; children: ReactNode }) {
  return <section className={`panel ${className}`}>{title && <h2>{title}</h2>}{children}</section>;
}

export function Toast({ type = "success", children }: { type?: "success" | "error"; children: ReactNode }) {
  return <div className={`attendance-toast ${type}`} role="status">{children}</div>;
}

export function StatusRow({ title, meta, status, action }: { title: string; meta: string; status: string; action?: ReactNode }) {
  return (
    <div className="status-row">
      <span className="icon-tile" />
      <div><b>{title}</b><small>{meta}</small></div>
      <div className="status-row-actions"><em>{status}</em>{action}</div>
    </div>
  );
}
