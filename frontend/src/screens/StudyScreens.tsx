import { useEffect, useRef, useState, useMemo } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { authInterestTags, createStudy, profile, studies, studyDetail, topics } from "../data";
import { Avatar, Field, Hero, Illustration, PageHeading, Panel, SectionTitle, Shell, StatusRow, StudyCard } from "../components/Common";
import { TagList } from "../components/TagInput";
import { ROUTE_PATHS } from "../routes/routingMap";
import { studiesApi, type StudyDetailResponse, type StudySummaryResponse } from "../api/studies";
import type { Study, StudyDetailData } from "../types";

import { allMockStudies } from "../mockStudies";

import informIcon from "../assets/inform.svg";
import rocketIcon from "../assets/rocket.svg";
import ruleIcon from "../assets/rule.svg";
import shareIcon from "../assets/share.svg";

const studyListRequest = {
  promise: null as Promise<StudySummaryResponse[]> | null,
};
const studyDetailRequests = new Map<string, Promise<StudyDetailResponse>>();
const studyShuffleSeed = Math.random().toString(36).slice(2);

function normalizeTag(tag: string) {
  return tag.startsWith("#") ? tag : `#${tag}`;
}

function resolveStudyTone(tags: string[]): Study["tone"] {
  const normalized = tags.join(" ").toLowerCase();
  if (normalized.includes("english") || normalized.includes("영어")) return "english";
  if (normalized.includes("algorithm") || normalized.includes("알고리즘") || normalized.includes("cs")) return "algorithm";
  if (normalized.includes("design") || normalized.includes("디자인")) return "design";
  return "tech";
}

function mapStudySummary(study: StudySummaryResponse): Study {
  const tags = study.tags.map(normalizeTag);
  return {
    studyId: study.studyId,
    title: study.title,
    tags,
    people: `${study.currentMembers}/${study.maxMembers}`,
    duration: study.status === "OPEN" ? "모집중" : "마감",
    tone: resolveStudyTone(tags),
  };
}

function mapStudyDetail(detail: StudyDetailResponse): StudyDetailData {
  const tags = detail.tags.map(normalizeTag);
  return {
    id: String(detail.studyId),
    title: detail.title,
    subtitle: detail.meetingCycle || `${detail.durationWeeks}주 스터디`,
    description: [detail.description],
    tags,
    location: detail.languages.length > 0 ? detail.languages.join(" / ") : "온라인",
    info: [
      { label: "진행 방식", value: detail.meetingCycle || "미정" },
      { label: "스터디 기간", value: `${detail.durationWeeks}주` },
      { label: "현재 인원", value: `${detail.currentMembers} / ${detail.maxMembers}명` },
    ],
    rules: studyDetail.rules,
    members: [
      {
        name: detail.createdBy.name,
        role: "Leader",
        avatar: "a",
      },
    ],
  };
}

function fetchStudySummaries() {
  if (!studyListRequest.promise) {
    studyListRequest.promise = studiesApi.list({ page: 0, size: 20 })
      .then((response) => response.studies)
      .catch((error) => {
        studyListRequest.promise = null;
        throw error;
      });
  }

  return studyListRequest.promise;
}

function fetchStudyDetail(studyId: string) {
  const cachedRequest = studyDetailRequests.get(studyId);
  if (cachedRequest) return cachedRequest;

  const request = studiesApi.get(studyId).catch((error) => {
    studyDetailRequests.delete(studyId);
    throw error;
  });
  studyDetailRequests.set(studyId, request);
  return request;
}

function stableShuffleStudies(list: Study[], topic: string) {
  return [...list].sort((a, b) => {
    const aRank = getStableShuffleRank(`${studyShuffleSeed}:${topic}:${a.studyId ?? a.title}`);
    const bRank = getStableShuffleRank(`${studyShuffleSeed}:${topic}:${b.studyId ?? b.title}`);
    return aRank - bRank;
  });
}

function getStableShuffleRank(value: string) {
  let hash = 2166136261;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return hash >>> 0;
}

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
        <SectionTitle title="My Study" action="마이페이지  →" onAction={() => onNavigate(ROUTE_PATHS.mypage)} />
        <div className="study-grid">
          {studies.map((study) => <StudyCard key={study.title} study={study} action="입장하기" onAction={() => onNavigate(ROUTE_PATHS.teamBoard())} />)}
        </div>
      </section>
    </div>
  );
}

function ExploreResults() {
  const navigate = useNavigate();
  const [selectedTopic, setSelectedTopic] = useState(topics[0]);
  const [apiStudies, setApiStudies] = useState<Study[] | null>(null);
  const [isLoadingStudies, setIsLoadingStudies] = useState(true);
  const [studyLoadError, setStudyLoadError] = useState("");

  useEffect(() => {
    let isMounted = true;

    fetchStudySummaries()
      .then((studySummaries) => {
        if (!isMounted) return;
        setApiStudies(studySummaries.map(mapStudySummary));
        setStudyLoadError("");
      })
      .catch(() => {
        if (!isMounted) return;
        setApiStudies(null);
        setStudyLoadError("스터디 목록을 불러오지 못해 샘플 데이터를 표시합니다.");
      })
      .finally(() => {
        if (isMounted) setIsLoadingStudies(false);
      });

    return () => {
      isMounted = false;
    };
  }, []);

  let title = `${selectedTopic.replace("#", "")} 추천 스터디`;
  let subtitle = `${selectedTopic} 관련 선별된 스터디 그룹입니다.`;

  if (selectedTopic === "#전체") {
    title = "전체 스터디";
    subtitle = "현재 모집 중인 모든 스터디 그룹입니다.";
  } else if (selectedTopic === "#추천") {
    title = "관심사 기반 추천 스터디";
    subtitle = "관심사 기반으로 선별한 스터디 그룹입니다.";
  }

  // useMemo를 사용해 태그가 바뀔 때마다 스터디 목록을 셔플(무작위 정렬)합니다.
  const displayedStudies = useMemo(() => {
    if (apiStudies) {
      if (selectedTopic === "#전체" || selectedTopic === "#추천") {
        return stableShuffleStudies(apiStudies, selectedTopic);
      }

      return stableShuffleStudies(apiStudies.filter((study) => study.tags.includes(selectedTopic)), selectedTopic);
    }

    if (isLoadingStudies) {
      return [];
    }

    let list = [];
    if (selectedTopic === "#전체") {
      list = [...allMockStudies];
    } else if (selectedTopic === "#추천") {
      list = allMockStudies.filter((study) => study.tags.includes("#프론트엔드") || study.tags.includes("#알고리즘"));
    } else {
      list = allMockStudies.filter((study) => study.tags.includes(selectedTopic));
    }
    return stableShuffleStudies(list, selectedTopic);
  }, [apiStudies, isLoadingStudies, selectedTopic]);

  return (
    <div className="dashboard-content-panel explore-panel">
      <TopicScroller selectedTopic={selectedTopic} onSelect={setSelectedTopic} />
      <section className="section-block">
        <SectionTitle title={title} subtitle={subtitle} />
        {isLoadingStudies && <p className="section-note">스터디 목록을 불러오는 중입니다.</p>}
        {studyLoadError && <p className="section-note">{studyLoadError}</p>}
        {/* key에 selectedTopic을 주어 리액트가 아예 요소를 다시 그리게 만들어 진입 애니메이션을 재활성시킵니다 */}
        <div key={selectedTopic} className="study-grid explore-grid">
          {displayedStudies.map((study, index) => (
            <StudyCard
              key={study.studyId ?? study.title}
              study={study}
              action="신청하기"
              onAction={() => navigate(ROUTE_PATHS.studyDetail(study.studyId != null ? String(study.studyId) : undefined))}
            />
          ))}
        </div>
      </section>
    </div>
  );
}

function TopicScroller({ selectedTopic, onSelect }: { selectedTopic: string; onSelect: (topic: string) => void }) {
  return (
    <section className="topics">
      <h2>POPULAR TOPICS</h2>
      <div>
        {topics.map((topic) => (
          <button className={selectedTopic === topic ? "active" : ""} key={topic} type="button" onClick={() => onSelect(topic)}>
            {topic}
          </button>
        ))}
      </div>
    </section>
  );
}

export function StudyDetail() {
  const { studyId } = useParams();
  const [detail, setDetail] = useState<StudyDetailData | null>(studyDetail);
  const [isLoadingDetail, setIsLoadingDetail] = useState(false);
  const [detailError, setDetailError] = useState("");

  useEffect(() => {
    if (!studyId || Number.isNaN(Number(studyId))) {
      setDetail(studyDetail);
      setDetailError("");
      return;
    }

    let isMounted = true;
    setIsLoadingDetail(true);
    setDetail(null);

    fetchStudyDetail(studyId)
      .then((response) => {
        if (!isMounted) return;
        setDetail(mapStudyDetail(response));
        setDetailError("");
      })
      .catch(() => {
        if (!isMounted) return;
        setDetail(studyDetail);
        setDetailError("스터디 상세 정보를 불러오지 못해 샘플 데이터를 표시합니다.");
      })
      .finally(() => {
        if (isMounted) setIsLoadingDetail(false);
      });

    return () => {
      isMounted = false;
    };
  }, [studyId]);

  return (
    <Shell>
      <section className="detail-page content-container">
        {isLoadingDetail && <p className="section-note">스터디 상세 정보를 불러오는 중입니다.</p>}
        {detailError && <p className="section-note">{detailError}</p>}
        {detail && (
          <>
            <div className="detail-top">
              <div>
                <h1>{detail.title}</h1>
                <h2>{detail.subtitle}</h2>
                <div className="detail-meta">
                  {detail.tags.map((tag) => <span key={tag}>{tag}</span>)}
                  <span className="location">{detail.location}</span>
                </div>
              </div>
              <div className="detail-actions">
                <button className="primary" type="button">참여하기 <span><img src={rocketIcon} alt="" /></span></button>
                <button className="share" type="button"><img src={shareIcon} alt="Share" /></button>
              </div>
            </div>
            <div className="detail-grid">
              <section className="panel wide-panel">
                <h2><span><img src={informIcon} alt="" /></span> 스터디 소개</h2>
                {detail.description.map((p, i) => <p key={i}>{p}</p>)}
              </section>
              <aside className="panel detail-info-card">
                <dl>
                  {detail.info.map(({ label, value }) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}
                </dl>
              </aside>
              <section className="panel rules-panel">
                <h2><span><img src={ruleIcon} alt="" /></span> 규칙</h2>
                {detail.rules.map((rule) => <Rule key={rule.no} {...rule} />)}
              </section>
              <aside className="panel member-card">
                <h2>참여중인 멤버</h2>
                {detail.members.map(({ name, role, avatar }) => (
                  <div className="member-mini" key={name}><Avatar name={avatar} /><span><b>{name}</b><small>{role}</small></span></div>
                ))}
                <button type="button">+ 4 Seats Available</button>
              </aside>
            </div>
          </>
        )}
      </section>
    </Shell>
  );
}

export function MyPage() {
  const navigate = useNavigate();
  const [interestTags, setInterestTags] = useState<string[]>(profile.interestKeywords);
  const [isEditingProfile, setIsEditingProfile] = useState(false);
  const [profileName, setProfileName] = useState("박지민 (Jimin Park)");
  const [profileSchool, setProfileSchool] = useState("Ajou University 3학년");

  const handleRemoveInterestTag = (tagToRemove: string) => {
    setInterestTags(interestTags.filter((tag) => tag !== tagToRemove));
  };

  const handleAddInterestTag = (newTag: string) => {
    const normalizedTag = newTag.startsWith("#") ? newTag : `#${newTag}`;
    if (!interestTags.includes(normalizedTag)) {
      setInterestTags([...interestTags, normalizedTag]);
    }
  };

  return (
    <main className="profile-page content-container">
      <section className="profile-hero">
        <div className="profile-photo-wrap">
          <Avatar name="user" className="profile-photo" />
          <span className="profile-verified">✓</span>
        </div>
        <div className="profile-copy">
          {isEditingProfile ? (
            <div className="profile-edit-fields">
              <input value={profileName} onChange={(event) => setProfileName(event.target.value)} aria-label="프로필 이름" />
              <input value={profileSchool} onChange={(event) => setProfileSchool(event.target.value)} aria-label="학교 정보" />
            </div>
          ) : (
            <div className="profile-text-lines">
              <h1>{profileName}</h1>
              <p>{profileSchool}</p>
            </div>
          )}
        </div>
        <button className="primary" type="button" onClick={() => setIsEditingProfile(!isEditingProfile)}>
          {isEditingProfile ? "저장하기" : "프로필 편집"}
        </button>
      </section>
      <section className="profile-grid">
        <div className="profile-study-section">
          <Panel title="" className="keyword-panel keyword-strip">
            <TagList tags={interestTags} onRemoveTag={handleRemoveInterestTag} onAddTag={handleAddInterestTag} />
          </Panel>
          <h2>참여 중인 스터디</h2>
          <div className="study-grid profile-study-grid">
            {studies.map((study) => <StudyCard key={study.title} study={study} action="입장하기" onAction={() => navigate(ROUTE_PATHS.teamBoard())} />)}
          </div>
          <h2>지원 현황</h2>
          <Panel title="" className="application-panel">
            {profile.applications.map((application) => <StatusRow key={application.title} {...application} />)}
          </Panel>
        </div>
      </section>
    </main>
  );
}

export function CreateStudy({ step }: { step: 1 | 2 | 3 }) {
  const navigate = useNavigate();
  const next = step === 1 ? ROUTE_PATHS.createRules : step === 2 ? ROUTE_PATHS.createSchedule : ROUTE_PATHS.home;
  const previous = step === 2 ? ROUTE_PATHS.createBasic : step === 3 ? ROUTE_PATHS.createRules : ROUTE_PATHS.home;
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
          <button className="plain" type="button" onClick={() => navigate(previous)}>
            {step === 1 ? "× 취소하기" : "← 이전으로"}
          </button>
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
  const defaultCategory = createStudy.categories.find((category) => category.selected)?.label ?? createStudy.categories[0].label;
  const defaultVisibility = createStudy.visibilityOptions.find((option) => option.selected)?.label ?? createStudy.visibilityOptions[0].label;
  const [selectedCategory, setSelectedCategory] = useState(defaultCategory);
  const [selectedVisibility, setSelectedVisibility] = useState(defaultVisibility);

  return (
    <div className="create-fields">
      <Field label="스터디 제목" placeholder="예: [CS 기초] 기술 면접 대비 올인원 스터디" />
      <label>
        카테고리 선택
        <div className="category-grid">
          {createStudy.categories.map(({ icon, label }) => (
            <button
              aria-pressed={selectedCategory === label}
              className={selectedCategory === label ? "selected" : ""}
              key={label}
              type="button"
              onClick={() => setSelectedCategory(label)}
            >
              <span>{icon}</span>{label}
            </button>
          ))}
        </div>
      </label>
      <label>스터디 목표 및 소개<textarea placeholder="스터디를 통해 얻고자 하는 바와 간략한 소개를 적어주세요." /></label>
      <div className="split-fields">
        <Field label="모집 인원" placeholder="4                                   명" />
        <label>
          공개 여부
          <div className="visibility-row">
            {createStudy.visibilityOptions.map(({ label }) => (
              <button
                aria-pressed={selectedVisibility === label}
                className={selectedVisibility === label ? "selected" : ""}
                key={label}
                type="button"
                onClick={() => setSelectedVisibility(label)}
              >
                {label}
              </button>
            ))}
          </div>
        </label>
      </div>
    </div>
  );
}

function RulesForm() {
  const [tags, setTags] = useState<string[]>(authInterestTags);

  const handleRemoveTag = (tagToRemove: string) => {
    setTags(tags.filter((tag) => tag !== tagToRemove));
  };

  const handleAddTag = (newTag: string) => {
    const normalizedTag = newTag.startsWith("#") ? newTag : `#${newTag}`;
    if (!tags.includes(normalizedTag)) {
      setTags([...tags, normalizedTag]);
    }
  };

  return (
    <div className="create-fields">
      <Field label="규칙 및 태그" placeholder="규칙을 작성해주세요!" />
      <input placeholder="규칙을 작성해주세요!" />
      <input placeholder="규칙을 작성해주세요!" />
      <TagList tags={tags} onRemoveTag={handleRemoveTag} onAddTag={handleAddTag} />
    </div>
  );
}

function ScheduleForm() {
  const [scheduleFields, setScheduleFields] = useState(() =>
    createStudy.schedule.reduce<Record<string, string>>((fields, { label, value }) => {
      fields[label] = value;
      return fields;
    }, {})
  );

  return (
    <div className="schedule-list">
      {createStudy.schedule.map(({ label }) => (
        <label key={label}>
          <b>{label}</b>
          <input
            value={scheduleFields[label]}
            onChange={(event) => setScheduleFields({ ...scheduleFields, [label]: event.target.value })}
          />
        </label>
      ))}
    </div>
  );
}

function Rule({ no, title, desc }: { no: string; title: string; desc: string }) {
  return <div className="rule-row"><span>{no}</span><div><b>{title}</b><p>{desc}</p></div></div>;
}
