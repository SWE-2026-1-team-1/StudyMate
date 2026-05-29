import { useEffect, useRef, useState, useMemo } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { createStudy, studyDetail, topics } from "../data";
import { Avatar, Field, Hero, Illustration, PageHeading, Panel, SectionTitle, Shell, StatusRow, StudyCard, Toast } from "../components/Common";
import { TagList } from "../components/TagInput";
import { ROUTE_PATHS } from "../routes/routingMap";
import { applicationsApi, type MyApplicationResponse } from "../api/applications";
import { profileApi, type ProfileResponse } from "../api/profile";
import { studiesApi, type CreateStudyRequest, type MyStudyItemResponse, type StudyDetailResponse, type StudySummaryResponse } from "../api/studies";
import { teamMembersApi } from "../api/teamMembers";
import { useLanguage } from "../i18n";
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
const createStudyDraftKey = "studyMate.createStudyDraft";
const createStudyPathPrefix = "/studies/new";

type CreateStudyDraft = {
  title: string;
  description: string;
  tags: string[];
  languages: string;
  maxMembers: string;
  durationWeeks: string;
  meetingCycle: string;
};
type ProfileMessage = { type: "success" | "error"; text: string } | null;
type ToastMessage = { type: "success" | "error"; text: string } | null;
type MyStudyCard = Study & { teamId: number };

function formatApplicationDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "지원일 확인 불가";

  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, "0")}.${String(date.getDate()).padStart(2, "0")} 지원`;
}

function getApplicationStatusLabel(status: MyApplicationResponse["status"]) {
  if (status === "APPROVED") return "ACCEPTED";
  if (status === "REJECTED") return "REJECTED";
  return "PENDING";
}

function mapMyApplication(application: MyApplicationResponse) {
  return {
    title: application.studyTitle,
    meta: `${formatApplicationDate(application.appliedAt)} · ${application.studyStatus === "OPEN" ? "모집중" : "마감"}`,
    status: getApplicationStatusLabel(application.status),
  };
}

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

function mapMyStudySummary(study: MyStudyItemResponse): MyStudyCard {
  const tags = study.tags.map(normalizeTag);
  const roleLabel = study.role === "LEADER" ? "운영중" : "참여중";

  return {
    studyId: study.studyId,
    teamId: study.teamId,
    title: study.title,
    tags,
    people: `${study.currentMembers}/${study.maxMembers}`,
    duration: `${roleLabel} · ${study.status === "OPEN" ? "모집중" : "마감"}`,
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

export function clearStudyApiCache() {
  studyListRequest.promise = null;
  studyDetailRequests.clear();
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

function getDefaultCreateStudyDraft(): CreateStudyDraft {
  return {
    title: "",
    description: "",
    tags: [],
    languages: "",
    maxMembers: "4",
    durationWeeks: "8",
    meetingCycle: createStudy.schedule[0]?.value ?? "",
  };
}

function clearCreateStudyDraft() {
  sessionStorage.removeItem(createStudyDraftKey);
}

function isCreateStudyPath(pathname: string) {
  return pathname === createStudyPathPrefix || pathname.startsWith(`${createStudyPathPrefix}/`);
}

function readCreateStudyDraft() {
  const fallback = getDefaultCreateStudyDraft();
  const rawDraft = sessionStorage.getItem(createStudyDraftKey);
  if (!rawDraft) return fallback;

  try {
    const parsedDraft = JSON.parse(rawDraft) as Partial<CreateStudyDraft>;
    return {
      ...fallback,
      ...parsedDraft,
      tags: Array.isArray(parsedDraft.tags) ? parsedDraft.tags : fallback.tags,
    };
  } catch {
    return fallback;
  }
}

function toCreateStudyRequest(draft: CreateStudyDraft): CreateStudyRequest {
  return {
    title: draft.title.trim(),
    description: draft.description.trim(),
    tags: draft.tags.map((tag) => tag.replace(/^#/, "")).filter(Boolean),
    languages: draft.languages.split(",").map((language) => language.trim()).filter(Boolean),
    maxMembers: Math.max(2, Number.parseInt(draft.maxMembers, 10) || 2),
    durationWeeks: Math.max(1, Number.parseInt(draft.durationWeeks, 10) || 1),
    meetingCycle: draft.meetingCycle.trim(),
  };
}

function getStudyApiErrorMessage(error: unknown, fallback: string) {
  if (!error || typeof error !== "object") return fallback;

  const response = (error as { response?: { status?: number; data?: unknown } }).response;
  if (!response) return "서버에 연결할 수 없습니다.";

  const data = response.data;
  if (typeof data === "string" && data.trim()) return data;
  if (data && typeof data === "object") {
    const message = (data as { message?: unknown; error?: unknown }).message ?? (data as { error?: unknown }).error;
    if (typeof message === "string" && message.trim()) return message;
  }

  const status = response.status ?? 0;
  if (status === 401 || status === 403) return "로그인 후 이용할 수 있습니다.";
  if (status >= 500) return "서버에서 요청 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";

  return fallback;
}

function getProfileApiErrorMessage(error: unknown, fallback: string) {
  if (!error || typeof error !== "object") return fallback;

  const response = (error as { response?: { status?: number; data?: unknown } }).response;
  if (!response) return "서버에 연결할 수 없습니다.";

  const data = response.data;
  if (typeof data === "string" && data.trim()) return data;
  if (data && typeof data === "object") {
    const message = (data as { message?: unknown; error?: unknown }).message ?? (data as { error?: unknown }).error;
    if (typeof message === "string" && message.trim()) return message;
  }

  if (response.status === 401 || response.status === 403) return "로그인 후 프로필을 확인할 수 있습니다.";
  if (response.status === 404) return "프로필 정보를 찾을 수 없습니다.";
  if ((response.status ?? 0) >= 500) return "서버에서 프로필 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";

  return fallback;
}

function getApiStatus(error: unknown) {
  if (!error || typeof error !== "object") return undefined;
  return (error as { response?: { status?: number } }).response?.status;
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
  const { translate } = useLanguage();
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
          <button className="search-back-button" type="button" aria-label={translate("메인으로 돌아가기")} onClick={() => navigate(ROUTE_PATHS.home, { state: { fromSearch: true }, viewTransition: true })}>
            ←
          </button>
          <label className="search-box explore-search route-search-box">
            <i />
            <input ref={inputRef} placeholder={translate("관심 있는 스터디 주제나 기술 스택을 검색해보세요")} />
          </label>
        </div>
        <ExploreResults />
      </section>
    </Shell>
  );
}

function MainDashboardContent({ onNavigate }: { onNavigate: ReturnType<typeof useNavigate> }) {
  const { translate } = useLanguage();
  const [myStudies, setMyStudies] = useState<MyStudyCard[]>([]);
  const [isLoadingMyStudies, setIsLoadingMyStudies] = useState(true);
  const [myStudyLoadError, setMyStudyLoadError] = useState("");

  useEffect(() => {
    if (!localStorage.getItem("accessToken")) {
      setIsLoadingMyStudies(false);
      return;
    }

    let isMounted = true;
    setIsLoadingMyStudies(true);
    setMyStudyLoadError("");

    studiesApi.listMine({ page: 0, size: 3 })
      .then((response) => {
        if (!isMounted) return;
        setMyStudies(response.studies.map(mapMyStudySummary));
      })
      .catch(() => {
        if (!isMounted) return;
        setMyStudies([]);
        setMyStudyLoadError("내 스터디를 불러오지 못했습니다.");
      })
      .finally(() => {
        if (isMounted) setIsLoadingMyStudies(false);
      });

    return () => {
      isMounted = false;
    };
  }, []);

  return (
    <div className="dashboard-content-panel">
      <section className="callout-card">
        <div>
          <span className="badge">{translate("New")}</span>
          <h2>{translate("나만의 스터디를 만들고 함께 성장할 팀원을 모집하세요")}</h2>
          <p>{translate("목표, 일정, 모집 인원을 설정하고 팀원 모집부터 운영까지 한곳에서 관리할 수 있습니다.")}</p>
          <button className="primary" type="button" onClick={() => onNavigate(ROUTE_PATHS.createBasic)}>{translate("스터디 만들기")}</button>
        </div>
        <Illustration />
      </section>
      <section className="section-block">
        <SectionTitle title="My Study" action="마이페이지  →" onAction={() => onNavigate(ROUTE_PATHS.mypage)} />
        {isLoadingMyStudies && <p className="section-note">{translate("내 스터디를 불러오는 중입니다.")}</p>}
        {myStudyLoadError && <p className="section-note form-error">{translate(myStudyLoadError)}</p>}
        {!isLoadingMyStudies && !myStudyLoadError && myStudies.length === 0 && (
          <p className="section-note">{translate("아직 참여 중인 스터디가 없습니다.")}</p>
        )}
        <div className="study-grid">
          {myStudies.map((study) => (
            <StudyCard
              key={study.teamId}
              study={study}
              action="입장하기"
              onAction={() => onNavigate(ROUTE_PATHS.teamBoard(String(study.teamId)))}
            />
          ))}
        </div>
      </section>
    </div>
  );
}

function ExploreResults() {
  const navigate = useNavigate();
  const { translate } = useLanguage();
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
        {isLoadingStudies && <p className="section-note">{translate("스터디 목록을 불러오는 중입니다.")}</p>}
        {studyLoadError && <p className="section-note">{translate(studyLoadError)}</p>}
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
  const { translate } = useLanguage();

  return (
    <section className="topics">
      <h2>{translate("POPULAR TOPICS")}</h2>
      <div>
        {topics.map((topic) => (
          <button className={selectedTopic === topic ? "active" : ""} key={topic} type="button" onClick={() => onSelect(topic)}>
            {translate(topic)}
          </button>
        ))}
      </div>
    </section>
  );
}

export function StudyDetail() {
  const navigate = useNavigate();
  const { studyId } = useParams();
  const { translate } = useLanguage();
  const toastTimerRef = useRef<number | null>(null);
  const [detail, setDetail] = useState<StudyDetailData | null>(studyDetail);
  const [isLoadingDetail, setIsLoadingDetail] = useState(false);
  const [detailError, setDetailError] = useState("");
  const [isApplying, setIsApplying] = useState(false);
  const [hasApplied, setHasApplied] = useState(false);
  const [isStudyMember, setIsStudyMember] = useState(false);
  const [toastMessage, setToastMessage] = useState<ToastMessage>(null);

  const showToast = (message: Exclude<ToastMessage, null>) => {
    if (toastTimerRef.current) window.clearTimeout(toastTimerRef.current);
    setToastMessage(message);
    toastTimerRef.current = window.setTimeout(() => setToastMessage(null), 2200);
  };

  useEffect(() => {
    setHasApplied(false);
    setIsStudyMember(false);
    setToastMessage(null);

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

  useEffect(() => {
    if (!studyId || Number.isNaN(Number(studyId)) || !localStorage.getItem("accessToken")) return;

    let isMounted = true;
    const storedUserId = Number(localStorage.getItem("userId"));

    Promise.allSettled([
      teamMembersApi.list(studyId),
      applicationsApi.listMine({ page: 0, size: 100 }),
    ]).then(([membersResult, applicationsResult]) => {
      if (!isMounted) return;

      if (membersResult.status === "fulfilled") {
        setIsStudyMember(membersResult.value.members.some((member) => member.userId === storedUserId));
      } else {
        setIsStudyMember(false);
      }

      if (applicationsResult.status === "fulfilled") {
        setHasApplied(applicationsResult.value.applications.some((application) => (
          String(application.studyId) === String(studyId) && application.status === "PENDING"
        )));
      } else {
        setHasApplied(false);
      }
    });

    return () => {
      isMounted = false;
    };
  }, [studyId]);

  const handleStudyActionClick = async () => {
    if (isStudyMember) {
      if (!studyId || Number.isNaN(Number(studyId))) {
        showToast({ type: "error", text: "샘플 스터디에는 입장할 수 없습니다." });
        return;
      }

      navigate(ROUTE_PATHS.teamBoard(studyId));
      return;
    }

    await handleApplicationClick();
  };

  const handleApplicationClick = async () => {
    setToastMessage(null);

    if (!studyId || Number.isNaN(Number(studyId))) {
      showToast({ type: "error", text: "샘플 스터디에는 지원할 수 없습니다." });
      return;
    }

    if (!localStorage.getItem("accessToken")) {
      navigate(ROUTE_PATHS.login, { replace: true });
      return;
    }

    setIsApplying(true);
    try {
      if (hasApplied) {
        await studiesApi.cancelApplication(studyId);
        setHasApplied(false);
        showToast({ type: "success", text: "스터디 지원을 취소했습니다." });
        return;
      }

      await studiesApi.apply(studyId, { message: "스터디에 참여하고 싶습니다." });
      setHasApplied(true);
      showToast({ type: "success", text: "스터디 지원이 완료되었습니다." });
    } catch (error) {
      showToast({ type: "error", text: getStudyApiErrorMessage(error, hasApplied ? "스터디 지원을 취소하지 못했습니다." : "스터디에 지원하지 못했습니다.") });
    } finally {
      setIsApplying(false);
    }
  };

  return (
    <Shell>
      <section className="detail-page content-container">
        {toastMessage && <Toast type={toastMessage.type}>{translate(toastMessage.text)}</Toast>}
        {isLoadingDetail && <p className="section-note">{translate("스터디 상세 정보를 불러오는 중입니다.")}</p>}
        {detailError && <p className="section-note">{translate(detailError)}</p>}
        {detail && (
          <>
            <div className="detail-top">
              <div>
                <h1>{translate(detail.title)}</h1>
                <h2>{translate(detail.subtitle)}</h2>
                <div className="detail-meta">
                  {detail.tags.map((tag) => <span key={tag}>{translate(tag)}</span>)}
                  <span className="location">{translate(detail.location)}</span>
                </div>
              </div>
              <div className="detail-actions">
                <button className={`primary${hasApplied && !isStudyMember ? " cancel-application" : ""}`} type="button" onClick={handleStudyActionClick} disabled={isApplying}>
                  {translate(isApplying ? "처리 중..." : isStudyMember ? "입장하기" : hasApplied ? "신청 취소" : "참여하기")} <span><img src={rocketIcon} alt="" /></span>
                </button>
                <button className="share" type="button"><img src={shareIcon} alt="Share" /></button>
              </div>
            </div>
            <div className="detail-grid">
              <section className="panel wide-panel">
                <h2><span><img src={informIcon} alt="" /></span> {translate("스터디 소개")}</h2>
                {detail.description.map((p, i) => <p key={i}>{translate(p)}</p>)}
              </section>
              <aside className="panel detail-info-card">
                <dl>
                  {detail.info.map(({ label, value }) => <div key={label}><dt>{translate(label)}</dt><dd>{translate(value)}</dd></div>)}
                </dl>
              </aside>
              <section className="panel rules-panel">
                <h2><span><img src={ruleIcon} alt="" /></span> {translate("규칙")}</h2>
                {detail.rules.map((rule) => <Rule key={rule.no} {...rule} />)}
              </section>
              <aside className="panel member-card">
                <h2>{translate("참여중인 멤버")}</h2>
                {detail.members.map(({ name, role, avatar }) => (
                  <div className="member-mini" key={name}><Avatar name={avatar} /><span><b>{translate(name)}</b><small>{translate(role)}</small></span></div>
                ))}
                <button type="button">{translate("+ 4 Seats Available")}</button>
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
  const { translate } = useLanguage();
  const toastTimerRef = useRef<number | null>(null);
  const [profileData, setProfileData] = useState<ProfileResponse | null>(null);
  const [interestTags, setInterestTags] = useState<string[]>([]);
  const [isEditingProfile, setIsEditingProfile] = useState(false);
  const [profileName, setProfileName] = useState("박지민 (Jimin Park)");
  const [profileSchool, setProfileSchool] = useState("Ajou University");
  const [profileMajor, setProfileMajor] = useState("Software Engineering");
  const [profileBio, setProfileBio] = useState("함께 꾸준히 성장하는 스터디를 선호합니다.");
  const [isLoadingProfile, setIsLoadingProfile] = useState(true);
  const [isSavingProfile, setIsSavingProfile] = useState(false);
  const [hasProfile, setHasProfile] = useState(false);
  const [profileMessage, setProfileMessage] = useState<ProfileMessage>(null);
  const [myStudies, setMyStudies] = useState<MyStudyCard[]>([]);
  const [isLoadingMyStudies, setIsLoadingMyStudies] = useState(true);
  const [myStudyLoadError, setMyStudyLoadError] = useState("");
  const [myApplications, setMyApplications] = useState<MyApplicationResponse[]>([]);
  const [isLoadingApplications, setIsLoadingApplications] = useState(true);
  const [applicationLoadError, setApplicationLoadError] = useState("");
  const [toastMessage, setToastMessage] = useState<ToastMessage>(null);
  const [cancellingApplicationId, setCancellingApplicationId] = useState<number | null>(null);

  const showToast = (message: Exclude<ToastMessage, null>) => {
    if (toastTimerRef.current) window.clearTimeout(toastTimerRef.current);
    setToastMessage(message);
    toastTimerRef.current = window.setTimeout(() => setToastMessage(null), 2200);
  };

  useEffect(() => {
    let isMounted = true;
    setIsLoadingProfile(true);
    setProfileMessage(null);

    profileApi.get()
      .then((response) => {
        if (!isMounted) return;
        setProfileName(response.name);
        setProfileSchool(response.school);
        setProfileMajor(response.major);
        setProfileBio(response.bio);
        setInterestTags(response.interestTags.map(normalizeTag));
        setProfileData(response);
        setHasProfile(true);
      })
      .catch((error) => {
        if (!isMounted) return;
        if (getApiStatus(error) === 404) {
          setHasProfile(false);
          setProfileMessage({ type: "error", text: "프로필을 먼저 저장해 주세요." });
          return;
        }
        setProfileMessage({ type: "error", text: getProfileApiErrorMessage(error, "프로필 정보를 불러오지 못했습니다.") });
      })
      .finally(() => {
        if (isMounted) setIsLoadingProfile(false);
      });

    return () => {
      isMounted = false;
    };
  }, []);

  useEffect(() => {
    let isMounted = true;
    setIsLoadingMyStudies(true);
    setMyStudyLoadError("");

    studiesApi.listMine({ page: 0, size: 20 })
      .then((response) => {
        if (!isMounted) return;
        setMyStudies(response.studies.map(mapMyStudySummary));
      })
      .catch(() => {
        if (!isMounted) return;
        setMyStudies([]);
        setMyStudyLoadError("참여 중인 스터디를 불러오지 못했습니다.");
      })
      .finally(() => {
        if (isMounted) setIsLoadingMyStudies(false);
      });

    return () => {
      isMounted = false;
    };
  }, []);

  useEffect(() => {
    let isMounted = true;
    setIsLoadingApplications(true);
    setApplicationLoadError("");

    applicationsApi.listMine()
      .then((response) => {
        if (!isMounted) return;
        setMyApplications(response.applications);
      })
      .catch(() => {
        if (!isMounted) return;
        setMyApplications([]);
        setApplicationLoadError("지원 현황을 불러오지 못했습니다.");
      })
      .finally(() => {
        if (isMounted) setIsLoadingApplications(false);
      });

    return () => {
      isMounted = false;
    };
  }, []);

  const handleRemoveInterestTag = (tagToRemove: string) => {
    setInterestTags(interestTags.filter((tag) => tag !== tagToRemove));
  };

  const handleAddInterestTag = (newTag: string) => {
    const normalizedTag = newTag.startsWith("#") ? newTag : `#${newTag}`;
    if (!interestTags.includes(normalizedTag)) {
      setInterestTags([...interestTags, normalizedTag]);
    }
  };

  const handleProfileAction = async () => {
    if (!isEditingProfile) {
      setIsEditingProfile(true);
      setProfileMessage(null);
      return;
    }

    if (!profileName.trim()) {
      setProfileMessage({ type: "error", text: "이름을 입력해 주세요." });
      return;
    }

    setIsSavingProfile(true);
    setProfileMessage(null);

    const payload = {
      name: profileName.trim(),
      school: profileSchool.trim(),
      major: profileMajor.trim(),
      bio: profileBio.trim(),
      interestTags: interestTags.map((tag) => tag.replace(/^#/, "")).filter(Boolean),
    };

    try {
      const savedProfile = await (hasProfile ? profileApi.update(payload) : profileApi.create(payload));
      setProfileData(savedProfile);
      setHasProfile(true);
      setIsEditingProfile(false);
      showToast({ type: "success", text: "프로필이 저장되었습니다." });
    } catch (error) {
      showToast({ type: "error", text: getProfileApiErrorMessage(error, "프로필을 저장하지 못했습니다.") });
    } finally {
      setIsSavingProfile(false);
    }
  };

  const handleCancelEdit = () => {
    if (profileData) {
      setProfileName(profileData.name);
      setProfileSchool(profileData.school);
      setProfileMajor(profileData.major);
      setProfileBio(profileData.bio);
      setInterestTags(profileData.interestTags.map(normalizeTag));
    }
    setIsEditingProfile(false);
    setProfileMessage(null);
  };

  const handleDeleteProfile = async () => {
    setIsSavingProfile(true);
    setProfileMessage(null);

    try {
      await profileApi.delete();
      localStorage.removeItem("accessToken");
      localStorage.removeItem("refreshToken");
      localStorage.removeItem("userId");
      setProfileData(null);
      setHasProfile(false);
      setIsEditingProfile(false);
      setInterestTags([]);
      navigate(ROUTE_PATHS.login, { replace: true });
    } catch (error) {
      showToast({ type: "error", text: getProfileApiErrorMessage(error, "프로필을 삭제하지 못했습니다.") });
    } finally {
      setIsSavingProfile(false);
    }
  };

  const handleCancelApplication = async (application: MyApplicationResponse) => {
    if (cancellingApplicationId) return;

    setCancellingApplicationId(application.applicationId);
    try {
      await studiesApi.cancelApplication(application.studyId);
      setMyApplications((current) => current.filter((item) => item.applicationId !== application.applicationId));
      showToast({ type: "success", text: "스터디 지원을 취소했습니다." });
    } catch (error) {
      showToast({ type: "error", text: getStudyApiErrorMessage(error, "스터디 지원을 취소하지 못했습니다.") });
    } finally {
      setCancellingApplicationId(null);
    }
  };

  return (
    <main className="profile-page content-container">
      {toastMessage && <Toast type={toastMessage.type}>{translate(toastMessage.text)}</Toast>}
      <section className="profile-hero">
        <div className="profile-photo-wrap">
          <Avatar name="user" className="profile-photo" />
          <span className="profile-verified">✓</span>
        </div>
        <div className="profile-copy">
          {isEditingProfile ? (
            <div className="profile-edit-fields">
              <input value={profileName} onChange={(event) => setProfileName(event.target.value)} aria-label={translate("프로필 이름")} placeholder={translate("이름")} disabled={isSavingProfile} />
              <input value={profileSchool} onChange={(event) => setProfileSchool(event.target.value)} aria-label={translate("학교 정보")} placeholder={translate("학교")} disabled={isSavingProfile} />
              <input value={profileMajor} onChange={(event) => setProfileMajor(event.target.value)} aria-label={translate("전공 정보")} placeholder={translate("전공")} disabled={isSavingProfile} />
              <textarea value={profileBio} onChange={(event) => setProfileBio(event.target.value)} aria-label={translate("자기소개")} placeholder={translate("자기소개")} disabled={isSavingProfile} />
            </div>
          ) : (
            <div className="profile-text-lines">
              <h1>{profileName}</h1>
              <p>{profileSchool}{profileMajor ? ` · ${profileMajor}` : ""}</p>
              {profileBio && <small>{profileBio}</small>}
            </div>
          )}
          {profileMessage && (
            <p className={`profile-feedback ${profileMessage.type}`}>
              {translate(profileMessage.text)}
            </p>
          )}
        </div>
        <div className="profile-actions">
          <button className="primary" type="button" disabled={isLoadingProfile || isSavingProfile} onClick={handleProfileAction}>
            {translate(isSavingProfile ? "저장 중..." : isEditingProfile ? "저장하기" : "프로필 편집")}
          </button>
          {isEditingProfile && (
            <button className="profile-plain-button" type="button" disabled={isSavingProfile} onClick={handleCancelEdit}>
              {translate("취소")}
            </button>
          )}
        </div>
      </section>
      {isLoadingProfile && <p className="section-note">{translate("프로필 정보를 불러오는 중입니다.")}</p>}
      {profileMessage && <p className={`section-note form-${profileMessage.type}`}>{translate(profileMessage.text)}</p>}
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
              {translate("계정 삭제")}
            </button>
          </div>
          <h2>{translate("참여 중인 스터디")}</h2>
          {isLoadingMyStudies && <p className="section-note">{translate("참여 중인 스터디를 불러오는 중입니다.")}</p>}
          {myStudyLoadError && <p className="section-note form-error">{translate(myStudyLoadError)}</p>}
          {!isLoadingMyStudies && !myStudyLoadError && myStudies.length === 0 && (
            <p className="section-note">{translate("아직 참여 중인 스터디가 없습니다.")}</p>
          )}
          <div className="study-grid profile-study-grid">
            {myStudies.map((study) => (
              <StudyCard
                key={study.teamId}
                study={study}
                action="입장하기"
                onAction={() => navigate(ROUTE_PATHS.teamBoard(String(study.teamId)))}
              />
            ))}
          </div>
          <h2>{translate("지원 현황")}</h2>
          <Panel title="" className="application-panel">
            {isLoadingApplications && <p className="section-note">{translate("지원 현황을 불러오는 중입니다.")}</p>}
            {applicationLoadError && <p className="section-note form-error">{translate(applicationLoadError)}</p>}
            {!isLoadingApplications && !applicationLoadError && myApplications.length === 0 && (
              <p className="section-note">{translate("아직 지원한 스터디가 없습니다.")}</p>
            )}
            {myApplications.map((application) => (
              <StatusRow
                key={application.applicationId}
                {...mapMyApplication(application)}
                action={application.status === "PENDING" ? (
                  <button
                    className="application-cancel-button"
                    type="button"
                    onClick={() => handleCancelApplication(application)}
                    disabled={cancellingApplicationId === application.applicationId}
                  >
                    {translate(cancellingApplicationId === application.applicationId ? "취소 중..." : "취소")}
                  </button>
                ) : undefined}
              />
            ))}
          </Panel>
        </div>
      </section>
    </main>
  );
}

export function CreateStudy({ step }: { step: 1 | 2 | 3 }) {
  const navigate = useNavigate();
  const location = useLocation();
  const { translate } = useLanguage();
  const next = step === 1 ? ROUTE_PATHS.createRules : step === 2 ? ROUTE_PATHS.createSchedule : ROUTE_PATHS.home;
  const previous = step === 2 ? ROUTE_PATHS.createBasic : step === 3 ? ROUTE_PATHS.createRules : ROUTE_PATHS.home;
  const title = step === 1 ? "기본 정보를 입력해주세요" : step === 2 ? "규칙 및 태그를 입력해주세요" : "일정 설정";
  const [draft, setDraft] = useState<CreateStudyDraft>(() => readCreateStudyDraft());
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState("");

  useEffect(() => {
    const handlePageHide = () => clearCreateStudyDraft();
    window.addEventListener("pagehide", handlePageHide);

    return () => {
      window.removeEventListener("pagehide", handlePageHide);
    };
  }, []);

  useEffect(() => {
    if (!isCreateStudyPath(location.pathname)) {
      clearCreateStudyDraft();
    }
  }, [location.pathname]);

  useEffect(() => {
    return () => {
      window.setTimeout(() => {
        if (!isCreateStudyPath(window.location.pathname)) {
          clearCreateStudyDraft();
        }
      }, 0);
    };
  }, []);

  const updateDraft = (nextDraft: CreateStudyDraft) => {
    setDraft(nextDraft);
    sessionStorage.setItem(createStudyDraftKey, JSON.stringify(nextDraft));
  };

  const handleCancel = () => {
    if (step === 1) {
      clearCreateStudyDraft();
      navigate(previous);
      return;
    }

    navigate(previous);
  };

  const handleNext = async () => {
    setSubmitError("");

    if (step !== 3) {
      navigate(next);
      return;
    }

    const payload = toCreateStudyRequest(draft);
    if (!payload.title || !payload.description || payload.tags.length === 0 || payload.languages.length === 0 || !payload.meetingCycle) {
      setSubmitError("제목, 소개, 태그, 사용 언어/기술, 모임 주기를 모두 입력해 주세요.");
      return;
    }

    if (!localStorage.getItem("accessToken")) {
      setSubmitError("로그인 후 스터디를 생성할 수 있습니다.");
      clearCreateStudyDraft();
      navigate(ROUTE_PATHS.login);
      return;
    }

    setIsSubmitting(true);
    try {
      const createdStudy = await studiesApi.create(payload);
      clearCreateStudyDraft();
      clearStudyApiCache();
      navigate(ROUTE_PATHS.studyDetail(String(createdStudy.studyId)));
    } catch (error) {
      setSubmitError(getStudyApiErrorMessage(error, "스터디를 생성하지 못했습니다. 입력 내용을 확인한 뒤 다시 시도해 주세요."));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <main className="create-page content-container">
      <PageHeading title="새 스터디 만들기" subtitle="당신의 지적 성장을 이끌어갈 동료들을 찾아보세요." />
      <Stepper step={step} />
      <section className="create-card">
        <h2>{translate(title)}</h2>
        {submitError && <p className="section-note form-error">{translate(submitError)}</p>}
        {step === 1 && <BasicForm draft={draft} onChange={updateDraft} />}
        {step === 2 && <RulesForm draft={draft} onChange={updateDraft} />}
        {step === 3 && <ScheduleForm draft={draft} onChange={updateDraft} />}
        <footer className="form-footer">
          <button className="plain" type="button" onClick={handleCancel} disabled={isSubmitting}>
            {translate(step === 1 ? "× 취소하기" : "← 이전으로")}
          </button>
          <button className="primary" type="button" onClick={handleNext} disabled={isSubmitting}>
            {translate(step === 3 ? (isSubmitting ? "생성 중..." : "완료") : "다음 단계로 이동")} <span>→</span>
          </button>
        </footer>
      </section>
    </main>
  );
}

function Stepper({ step }: { step: 1 | 2 | 3 }) {
  const { translate } = useLanguage();

  return (
    <div className="stepper">
      {createStudy.steps.map((label, index) => (
        <div className={step >= index + 1 ? "active" : ""} key={label}>
          <span>{step > index + 1 ? "✓" : index + 1}</span>
          <b>STEP {index + 1}. {translate(label)}</b>
        </div>
      ))}
    </div>
  );
}

function BasicForm({ draft, onChange }: { draft: CreateStudyDraft; onChange: (draft: CreateStudyDraft) => void }) {
  const { translate } = useLanguage();
  const defaultCategory = createStudy.categories.find((category) => category.selected)?.label ?? createStudy.categories[0].label;
  const defaultVisibility = createStudy.visibilityOptions.find((option) => option.selected)?.label ?? createStudy.visibilityOptions[0].label;
  const [selectedCategory, setSelectedCategory] = useState(defaultCategory);
  const [selectedVisibility, setSelectedVisibility] = useState(defaultVisibility);

  return (
    <div className="create-fields">
      <Field
        label="스터디 제목"
        placeholder="예: [CS 기초] 기술 면접 대비 올인원 스터디"
        value={draft.title}
        onChange={(event) => onChange({ ...draft, title: event.target.value })}
      />
      <label>
        {translate("카테고리 선택")}
        <div className="category-grid">
          {createStudy.categories.map(({ icon, label }) => (
            <button
              aria-pressed={selectedCategory === label}
              className={selectedCategory === label ? "selected" : ""}
              key={label}
              type="button"
              onClick={() => setSelectedCategory(label)}
            >
              <span>{icon}</span>{translate(label)}
            </button>
          ))}
        </div>
      </label>
      <label>
        {translate("스터디 목표 및 소개")}
        <textarea
          placeholder={translate("스터디를 통해 얻고자 하는 바와 간략한 소개를 적어주세요.")}
          value={draft.description}
          onChange={(event) => onChange({ ...draft, description: event.target.value })}
        />
      </label>
      <div className="split-fields">
        <Field
          label="모집 인원"
          placeholder="4"
          type="number"
          value={draft.maxMembers}
          onChange={(event) => onChange({ ...draft, maxMembers: event.target.value })}
        />
        <label>
          {translate("공개 여부")}
          <div className="visibility-row">
            {createStudy.visibilityOptions.map(({ label }) => (
              <button
                aria-pressed={selectedVisibility === label}
                className={selectedVisibility === label ? "selected" : ""}
                key={label}
                type="button"
                onClick={() => setSelectedVisibility(label)}
              >
                {translate(label)}
              </button>
            ))}
          </div>
        </label>
      </div>
    </div>
  );
}

function RulesForm({ draft, onChange }: { draft: CreateStudyDraft; onChange: (draft: CreateStudyDraft) => void }) {
  const { translate } = useLanguage();
  const handleRemoveTag = (tagToRemove: string) => {
    onChange({ ...draft, tags: draft.tags.filter((tag) => tag !== tagToRemove) });
  };

  const handleAddTag = (newTag: string) => {
    const normalizedTag = newTag.startsWith("#") ? newTag : `#${newTag}`;
    if (!draft.tags.includes(normalizedTag)) {
      onChange({ ...draft, tags: [...draft.tags, normalizedTag] });
    }
  };

  return (
    <div className="create-fields">
      <Field
        label="사용 언어/기술"
        placeholder="예: JavaScript, React, Spring"
        value={draft.languages}
        onChange={(event) => onChange({ ...draft, languages: event.target.value })}
      />
      <label>
        {translate("태그")}
        <TagList tags={draft.tags} onRemoveTag={handleRemoveTag} onAddTag={handleAddTag} />
      </label>
    </div>
  );
}

function ScheduleForm({ draft, onChange }: { draft: CreateStudyDraft; onChange: (draft: CreateStudyDraft) => void }) {
  const { translate } = useLanguage();

  return (
    <div className="schedule-list">
      <label>
        <b>{translate("모임 주기")}</b>
        <input
          value={draft.meetingCycle}
          onChange={(event) => onChange({ ...draft, meetingCycle: event.target.value })}
          placeholder={translate("예: 매주 수요일 16:00")}
        />
      </label>
      <label>
        <b>{translate("스터디 기간")}</b>
        <input
          type="number"
          min="1"
          value={draft.durationWeeks}
          onChange={(event) => onChange({ ...draft, durationWeeks: event.target.value })}
          placeholder="8"
        />
      </label>
    </div>
  );
}

function Rule({ no, title, desc }: { no: string; title: string; desc: string }) {
  const { translate } = useLanguage();

  return <div className="rule-row"><span>{no}</span><div><b>{translate(title)}</b><p>{translate(desc)}</p></div></div>;
}
