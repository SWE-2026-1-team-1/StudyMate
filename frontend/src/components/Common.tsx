import { screens } from "../data";
import type { ScreenId, Study } from "../types";
import type { ReactNode } from "react";

type Navigate = (screen: ScreenId) => void;

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

export function TopBar({ onNavigate }: { onNavigate: Navigate }) {
  return (
    <header className="topbar">
      <button className="brand-link" type="button" onClick={() => onNavigate("main")}>StudyMate</button>
      <div className="top-actions">
        <span className="lang-toggle"><b>KR</b><b>EN</b></span>
        <button className="avatar-button" type="button" aria-label="마이페이지로 이동" onClick={() => onNavigate("mypage")}>
          <Avatar className="mini-avatar" name="user" />
        </button>
      </div>
    </header>
  );
}

export function Shell({ children, onNavigate, sidebar = false }: { children: ReactNode; onNavigate: Navigate; sidebar?: boolean }) {
  return (
    <Frame>
      <TopBar onNavigate={onNavigate} />
      <div className={sidebar ? "app-layout has-sidebar" : "app-layout"}>
        {sidebar && <StudySideNav />}
        {children}
      </div>
    </Frame>
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

export function Illustration() {
  return (
    <div className="illustration" aria-hidden="true">
      <span className="person p1" />
      <span className="person p2" />
      <span className="person p3" />
      <span className="person p4" />
      <span className="person p5" />
      <i className="table" />
    </div>
  );
}

export function AvatarStack() {
  return <span className="avatar-stack"><Avatar name="a" /><Avatar name="b" /><Avatar name="c" /></span>;
}

export function Avatar({ name, className = "" }: { name: string; className?: string }) {
  const initial = name === "user" ? "U" : name.slice(0, 1).toUpperCase();
  return <i className={`avatar portrait-${name} ${className}`} aria-hidden="true">{initial}</i>;
}

function StudyThumb({ tone }: { tone: Study["tone"] }) {
  return (
    <div className={`study-thumb ${tone}`}>
      <span className="thumb-visual" />
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
