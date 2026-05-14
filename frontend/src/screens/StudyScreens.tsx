import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { authInterestTags, createStudy, exploreStudies, profile, studies, studyDetail, topics } from "../data";
import { Avatar, Field, Hero, Illustration, PageHeading, Panel, SectionTitle, Shell, StatusRow, StudyCard } from "../components/Common";
import { ROUTE_PATHS } from "../routes/routingMap";
import type { ProgressStudy as ProgressStudyItem } from "../types";

export function MainDashboard() {
  const navigate = useNavigate();
  const location = useLocation();
  const fromSearch = Boolean((location.state as { fromSearch?: boolean } | null)?.fromSearch);

  const openSearch = () => {
    navigate(ROUTE_PATHS.studies, {
      state: { fromMainSearch: true },
      viewTransition: true,
    });
  };

  return (
    <Shell>
      <section className="dashboard-main content-container">
        <Hero onSearchFocus={openSearch} searchBoxClassName={`route-search-box ${fromSearch ? "return-from-search" : ""}`} />
        <MainDashboardContent onNavigate={navigate} />
      </section>
    </Shell>
  );
}

export function ExplorePage() {
  const navigate = useNavigate();
  const location = useLocation();
  const inputRef = useRef<HTMLInputElement>(null);
  const fromMainSearch = Boolean((location.state as { fromMainSearch?: boolean } | null)?.fromMainSearch);

  useEffect(() => {
    const focusDelay = fromMainSearch ? 320 : 0;
    const focusTimer = window.setTimeout(() => inputRef.current?.focus(), focusDelay);
    return () => window.clearTimeout(focusTimer);
  }, [fromMainSearch]);

  return (
    <Shell>
      <section className={`explore-page content-container ${fromMainSearch ? "from-main-search" : ""}`}>
        <div className="search-page-bar">
          <button className="search-back-button" type="button" aria-label="메인으로 돌아가기" onClick={() => navigate(ROUTE_PATHS.home, { state: { fromSearch: true }, viewTransition: true })}>
            ←
          </button>
          <label className="search-box explore-search route-search-box">
            <i />
            <input ref={inputRef} placeholder="관심 있는 스터디 주제나 기술 스택을 검색해보세요" />
          </label>
        </div>
        <ExploreResults />
      </section>
    </Shell>
  );
}

function MainDashboardContent({ onNavigate }: { onNavigate: ReturnType<typeof useNavigate> }) {
  return (
    <div className="dashboard-content-panel">
      <section className="callout-card">
        <div>
          <span className="badge">New</span>
          <h2>나만의 스터디를 만들고 함께 성장할 팀원을 모집하세요</h2>
          <p>목표, 일정, 모집 인원을 설정하고 팀원 모집부터 운영까지 한곳에서 관리할 수 있습니다.</p>
          <button className="primary" type="button" onClick={() => onNavigate(ROUTE_PATHS.createBasic)}>스터디 만들기</button>
        </div>
        <Illustration />
      </section>
      <section className="section-block">
        <SectionTitle title="My Study" action="전체 보기  →" />
        <div className="study-grid">
          {studies.map((study) => <StudyCard key={study.title} study={study} action="입장하기" onAction={() => onNavigate(ROUTE_PATHS.teamBoard())} />)}
        </div>
      </section>
    </div>
  );
}

function ExploreResults() {
  const navigate = useNavigate();

  return (
    <div className="dashboard-content-panel">
      <TopicScroller />
      <section className="section-block">
        <SectionTitle title="관심사 기반 추천 스터디" subtitle="관심사 기반으로 선별한 스터디 그룹입니다." action="전체 보기  →" />
        <div className="study-grid explore-grid">
          {exploreStudies.map((study, index) => (
            <StudyCard key={`${study.title}-${index}`} study={study} action="신청하기" onAction={() => navigate(ROUTE_PATHS.studyDetail())} />
          ))}
        </div>
      </section>
    </div>
  );
}

function TopicScroller() {
  const [selectedTopic, setSelectedTopic] = useState(topics[0]);

  return (
    <section className="topics">
      <h2>POPULAR TOPICS</h2>
      <div>
        {topics.map((topic) => (
          <button className={selectedTopic === topic ? "active" : ""} key={topic} type="button" onClick={() => setSelectedTopic(topic)}>
            {topic}
          </button>
        ))}
      </div>
    </section>
  );
}

export function StudyDetail() {
  return (
    <Shell>
      <section className="detail-page content-container">
        <div className="detail-top">
          <div>
            <h1>비즈니스 영어 회화 실전</h1>
            <h2>실무 상황에서 바로 쓰는 영어 회화 스터디</h2>
            <div className="detail-meta">
              {studyDetail.tags.map((tag) => <span key={tag}>{tag}</span>)}
              <span className="location">{studyDetail.location}</span>
            </div>
          </div>
          <div className="detail-actions">
            <button className="primary" type="button">Apply Now <span>↗</span></button>
            <button className="share" type="button">⌯</button>
          </div>
        </div>
        <div className="detail-grid">
          <section className="panel wide-panel">
            <h2><span>▤</span> 스터디 소개</h2>
            <p>이 스터디는 비즈니스 환경에서 자주 사용되는 영어 표현과 회화 패턴을 실전 중심으로 연습하는 것을 목표로 합니다. 회의, 이메일, 발표, 협상, 네트워킹 등 실제 업무 상황을 바탕으로 영어 표현을 익히고, 매주 롤플레이와 피드백을 통해 자연스럽게 말하는 능력을 향상시킵니다.</p>
            <p>영어를 단순히 공부하는 것이 아니라 실제 상황에서 바로 사용할 수 있도록 반복 연습합니다.</p>
          </section>
          <aside className="panel detail-info-card">
            <dl>
              {studyDetail.info.map(({ label, value }) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}
            </dl>
          </aside>
          <section className="panel rules-panel">
            <h2><span>≡</span> 규칙</h2>
            {studyDetail.rules.map((rule) => <Rule key={rule.no} {...rule} />)}
          </section>
          <aside className="panel member-card">
            <h2>참여중인 멤버</h2>
            {studyDetail.members.map(({ name, role, avatar }) => (
              <div className="member-mini" key={name}><Avatar name={avatar} /><span><b>{name}</b><small>{role}</small></span></div>
            ))}
            <button type="button">+ 4 Seats Available</button>
          </aside>
        </div>
      </section>
    </Shell>
  );
}

export function MyPage() {
  return (
    <main className="profile-page content-container">
      <section className="profile-hero">
        <Avatar name="user" className="profile-photo" />
        <div>
          <h1>박지민 (Jimin Park) • Ajou University 3학년</h1>
          <div className="keyword-set">
            {profile.keywords.map((keyword) => <span key={keyword}>{keyword}</span>)}
          </div>
        </div>
        <button className="primary" type="button">프로필 편집</button>
      </section>
      <section className="profile-grid">
        <div className="profile-study-section">
          <h2>참여 중인 스터디</h2>
          <div className="profile-content-grid">
            <div className="profile-left-column">
              <div className="mini-study-grid">
                {profile.progressStudies.map((study) => <ProgressStudy key={study.title} {...study} />)}
              </div>
              <Panel title="지원 현황" className="application-panel">
                {profile.applications.map((application) => <StatusRow key={application.title} {...application} />)}
              </Panel>
            </div>
            <Panel title="관심 키워드" className="keyword-panel">
              <button className="edit-pencil" type="button">✎</button>
              <div className="keyword-set color">
                {profile.interestKeywords.map((tag) => <span key={tag}>{tag}</span>)}
              </div>
              <button className="add-tag-button" type="button">+ Add Tag</button>
            </Panel>
          </div>
        </div>
      </section>
    </main>
  );
}

function ProgressStudy({ title, people, time, value }: ProgressStudyItem) {
  return (
    <article className="progress-study">
      <b>{title}</b>
      <p><span className="meta people">{people}</span><span className="meta time">{time}</span></p>
      <i><strong style={{ width: value }} /></i>
      <small><span>ATTENDANCE</span><span>{value}</span></small>
    </article>
  );
}

export function CreateStudy({ step }: { step: 1 | 2 | 3 }) {
  const navigate = useNavigate();
  const next = step === 1 ? ROUTE_PATHS.createRules : step === 2 ? ROUTE_PATHS.createSchedule : ROUTE_PATHS.home;
  const title = step === 1 ? "기본 정보를 입력해주세요" : step === 2 ? "규칙 및 태그를 입력해주세요" : "일정 설정";

  return (
    <main className="create-page content-container">
      <PageHeading title="새 스터디 만들기" subtitle="당신의 지적 성장을 이끌어갈 동료들을 찾아보세요." />
      <Stepper step={step} />
      <section className="create-card">
        <h2>{title}</h2>
        {step === 1 && <BasicForm />}
        {step === 2 && <RulesForm />}
        {step === 3 && <ScheduleForm />}
        <footer className="form-footer">
          <button className="plain" type="button">× 취소하기</button>
          <button className="primary" type="button" onClick={() => navigate(next)}>
            {step === 3 ? "완료" : "다음 단계로 이동"} <span>→</span>
          </button>
        </footer>
      </section>
    </main>
  );
}

function Stepper({ step }: { step: 1 | 2 | 3 }) {
  return (
    <div className="stepper">
      {createStudy.steps.map((label, index) => (
        <div className={step >= index + 1 ? "active" : ""} key={label}>
          <span>{step > index + 1 ? "✓" : index + 1}</span>
          <b>STEP {index + 1}. {label}</b>
        </div>
      ))}
    </div>
  );
}

function BasicForm() {
  return (
    <div className="create-fields">
      <Field label="스터디 제목" placeholder="예: [CS 기초] 기술 면접 대비 올인원 스터디" />
      <label>
        카테고리 선택
        <div className="category-grid">
          {createStudy.categories.map(({ icon, label, selected }) => <button className={selected ? "selected" : ""} key={label} type="button"><span>{icon}</span>{label}</button>)}
        </div>
      </label>
      <label>스터디 목표 및 소개<textarea placeholder="스터디를 통해 얻고자 하는 바와 간략한 소개를 적어주세요." /></label>
      <div className="split-fields">
        <Field label="모집 인원" placeholder="4                                   명" />
        <label>
          공개 여부
          <div className="visibility-row">
            {createStudy.visibilityOptions.map(({ label, selected }) => <button className={selected ? "selected" : ""} key={label} type="button">{label}</button>)}
          </div>
        </label>
      </div>
    </div>
  );
}

function RulesForm() {
  return (
    <div className="create-fields">
      <Field label="규칙 및 태그" placeholder="규칙을 작성해주세요!" />
      <input placeholder="규칙을 작성해주세요!" />
      <input placeholder="규칙을 작성해주세요!" />
      <div className="keyword-set auth-keywords">
        {authInterestTags.map((tag, index) => <span className={`tag-${index + 1}`} key={tag}>{tag}{index < 2 && <b>×</b>}</span>)}
        <button type="button">+ Add Tag</button>
      </div>
    </div>
  );
}

function ScheduleForm() {
  return (
    <div className="schedule-list">
      {createStudy.schedule.map(({ label, value }) => <div key={label}><b>{label}</b><span>{value}</span></div>)}
    </div>
  );
}

function Rule({ no, title, desc }: { no: string; title: string; desc: string }) {
  return <div className="rule-row"><span>{no}</span><div><b>{title}</b><p>{desc}</p></div></div>;
}
