import { useEffect, useRef, useState, useMemo } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { authInterestTags, createStudy, exploreStudies, profile, studies, studyDetail, topics } from "../data";
import { Avatar, Field, Hero, Illustration, PageHeading, Panel, SectionTitle, Shell, StatusRow, StudyCard } from "../components/Common";
import { TagList } from "../components/TagInput";
import { ROUTE_PATHS } from "../routes/routingMap";
import { profileApi, type ProfilePayload, type ProfileResponse } from "../api/profile";

import { allMockStudies } from "../mockStudies";

import informIcon from "../assets/inform.svg";
import rocketIcon from "../assets/rocket.svg";
import ruleIcon from "../assets/rule.svg";
import shareIcon from "../assets/share.svg";

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
    let list = [];
    if (selectedTopic === "#전체") {
      list = [...allMockStudies];
    } else if (selectedTopic === "#추천") {
      list = allMockStudies.filter((study) => study.tags.includes("#프론트엔드") || study.tags.includes("#알고리즘"));
    } else {
      list = allMockStudies.filter((study) => study.tags.includes(selectedTopic));
    }
    // 간단한 무작위 셔플 로직 적용
    return list.sort(() => Math.random() - 0.5);
  }, [selectedTopic]);

  return (
    <div className="dashboard-content-panel explore-panel">
      <TopicScroller selectedTopic={selectedTopic} onSelect={setSelectedTopic} />
      <section className="section-block">
        <SectionTitle title={title} subtitle={subtitle} />
        {/* key에 selectedTopic을 주어 리액트가 아예 요소를 다시 그리게 만들어 진입 애니메이션을 재활성시킵니다 */}
        <div key={selectedTopic} className="study-grid explore-grid">
          {displayedStudies.map((study, index) => (
            <StudyCard key={`${study.title}-${index}`} study={study} action="신청하기" onAction={() => navigate(ROUTE_PATHS.studyDetail())} />
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
  return (
    <Shell>
      <section className="detail-page content-container">
        <div className="detail-top">
          <div>
            <h1>{studyDetail.title}</h1>
            <h2>{studyDetail.subtitle}</h2>
            <div className="detail-meta">
              {studyDetail.tags.map((tag) => <span key={tag}>{tag}</span>)}
              <span className="location">{studyDetail.location}</span>
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
            {studyDetail.description.map((p, i) => <p key={i}>{p}</p>)}
          </section>
          <aside className="panel detail-info-card">
            <dl>
              {studyDetail.info.map(({ label, value }) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}
            </dl>
          </aside>
          <section className="panel rules-panel">
            <h2><span><img src={ruleIcon} alt="" /></span> 규칙</h2>
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
  const navigate = useNavigate();
  const [profileData, setProfileData] = useState<ProfileResponse | null>(null);
  const [interestTags, setInterestTags] = useState<string[]>([]);
  const [isEditingProfile, setIsEditingProfile] = useState(false);
  const [profileName, setProfileName] = useState("");
  const [profileSchool, setProfileSchool] = useState("");
  const [profileMajor, setProfileMajor] = useState("");
  const [profileBio, setProfileBio] = useState("");
  const [isLoadingProfile, setIsLoadingProfile] = useState(true);
  const [isSavingProfile, setIsSavingProfile] = useState(false);
  const [profileMessage, setProfileMessage] = useState("");
  const [profileError, setProfileError] = useState("");

  useEffect(() => {
    let ignore = false;

    const loadProfile = async () => {
      setIsLoadingProfile(true);
      setProfileError("");
      if (!localStorage.getItem("accessToken")) {
        setProfileError("로그인 후 프로필을 관리할 수 있습니다.");
        setIsLoadingProfile(false);
        return;
      }
      try {
        const data = await profileApi.get();
        if (ignore) return;
        applyProfile(data);
      } catch {
        if (!ignore) {
          setProfileError("프로필을 불러오지 못했습니다. 로그인 후 다시 시도해주세요.");
        }
      } finally {
        if (!ignore) setIsLoadingProfile(false);
      }
    };

    loadProfile();
    return () => {
      ignore = true;
    };
  }, []);

  const applyProfile = (data: ProfileResponse) => {
    setProfileData(data);
    setProfileName(data.name ?? "");
    setProfileSchool(data.school ?? "");
    setProfileMajor(data.major ?? "");
    setProfileBio(data.bio ?? "");
    setInterestTags(data.interestTags ?? []);
  };

  const handleRemoveInterestTag = (tagToRemove: string) => {
    setInterestTags(interestTags.filter((tag) => tag !== tagToRemove));
  };

  const handleAddInterestTag = (newTag: string) => {
    const normalizedTag = newTag.startsWith("#") ? newTag : `#${newTag}`;
    if (!interestTags.includes(normalizedTag)) {
      setInterestTags([...interestTags, normalizedTag]);
    }
  };

  const buildPayload = (): ProfilePayload => ({
    name: profileName.trim(),
    school: profileSchool.trim() || null,
    major: profileMajor.trim() || null,
    bio: profileBio.trim() || null,
    interestTags,
  });

  const handleEditButton = async () => {
    setProfileMessage("");
    setProfileError("");

    if (!isEditingProfile) {
      setIsEditingProfile(true);
      return;
    }

    if (!profileName.trim()) {
      setProfileError("이름을 입력해주세요.");
      return;
    }

    setIsSavingProfile(true);
    try {
      const payload = buildPayload();
      const saved = profileData ? await profileApi.update(payload) : await profileApi.create(payload);
      applyProfile(saved);
      setIsEditingProfile(false);
      setProfileMessage("프로필이 저장되었습니다.");
    } catch {
      setProfileError("프로필 저장에 실패했습니다.");
    } finally {
      setIsSavingProfile(false);
    }
  };

  const handleCancelEdit = () => {
    if (profileData) {
      applyProfile(profileData);
    }
    setIsEditingProfile(false);
    setProfileMessage("");
    setProfileError("");
  };

  const handleDeleteProfile = async () => {
    if (!window.confirm("프로필과 계정을 삭제하시겠습니까?")) return;

    setIsSavingProfile(true);
    setProfileMessage("");
    setProfileError("");
    try {
      await profileApi.remove();
      localStorage.removeItem("accessToken");
      localStorage.removeItem("refreshToken");
      localStorage.removeItem("userId");
      navigate(ROUTE_PATHS.login);
    } catch {
      setProfileError("프로필 삭제에 실패했습니다.");
      setIsSavingProfile(false);
    }
  };

  const schoolLine = [profileSchool, profileMajor].filter(Boolean).join(" · ");

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
              <input value={profileMajor} onChange={(event) => setProfileMajor(event.target.value)} aria-label="전공 정보" />
              <input value={profileBio} onChange={(event) => setProfileBio(event.target.value)} aria-label="프로필 소개" />
            </div>
          ) : (
            <div className="profile-text-lines">
              <h1>{isLoadingProfile ? "프로필을 불러오는 중" : profileName || "이름 없음"}</h1>
              <p>{schoolLine || "학교와 전공을 입력해주세요"}</p>
              {profileData?.email && <small>{profileData.email}</small>}
              {profileBio && <em>{profileBio}</em>}
            </div>
          )}
          {(profileMessage || profileError) && (
            <p className={`profile-feedback ${profileError ? "error" : "success"}`}>
              {profileError || profileMessage}
            </p>
          )}
        </div>
        <div className="profile-actions">
          <button className="primary" type="button" disabled={isLoadingProfile || isSavingProfile || !profileData} onClick={handleEditButton}>
            {isSavingProfile ? "저장 중" : isEditingProfile ? "저장하기" : "프로필 편집"}
          </button>
          {isEditingProfile && (
            <button className="profile-plain-button" type="button" disabled={isSavingProfile} onClick={handleCancelEdit}>
              취소
            </button>
          )}
        </div>
      </section>
      <section className="profile-grid">
        <div className="profile-study-section">
          <Panel title="" className="keyword-panel keyword-strip">
            <TagList
              tags={interestTags}
              onRemoveTag={isEditingProfile ? handleRemoveInterestTag : undefined}
              onAddTag={isEditingProfile ? handleAddInterestTag : undefined}
            />
          </Panel>
          <div className="profile-danger-row">
            <button type="button" disabled={isSavingProfile || !profileData} onClick={handleDeleteProfile}>
              계정 삭제
            </button>
          </div>
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
