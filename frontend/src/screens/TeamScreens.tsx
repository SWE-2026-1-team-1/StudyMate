import { useEffect, useRef, useState, type FormEvent, type ReactNode } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { applicationsApi, type TeamApplicationResponse } from "../api/applications";
import { studiesApi } from "../api/studies";
import { teamMembersApi, type TeamMemberResponse } from "../api/teamMembers";
import { attendanceDates, attendanceMembers, teamPosts } from "../data";
import { Avatar, Toast } from "../components/Common";
import { ROUTE_PATHS } from "../routes/routingMap";
import { clearStudyApiCache } from "./StudyScreens";
import type { TeamPost } from "../types";

function getTeamStudyApiErrorMessage(error: unknown, fallback: string) {
  if (!error || typeof error !== "object") return fallback;

  const response = (error as { response?: { status?: number; data?: unknown } }).response;
  if (!response) return "서버에 연결할 수 없습니다.";

  const data = response.data;
  if (typeof data === "string" && data.trim()) return data;
  if (data && typeof data === "object") {
    const message = (data as { message?: unknown; error?: unknown }).message ?? (data as { error?: unknown }).error;
    if (typeof message === "string" && message.trim()) return message;
  }

  if (response.status === 401 || response.status === 403) return "스터디를 삭제할 권한이 없습니다.";
  if (response.status === 404) return "삭제할 스터디를 찾을 수 없습니다.";
  if ((response.status ?? 0) >= 500) return "서버에서 스터디 삭제 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";

  return fallback;
}

function formatJoinRequestDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "지원일 확인 불가";

  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, "0")}.${String(date.getDate()).padStart(2, "0")} 지원`;
}

function getInitials(name: string) {
  return name.trim().slice(0, 2) || "??";
}

function formatMemberJoinedAt(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "-";

  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, "0")}.${String(date.getDate()).padStart(2, "0")}`;
}

function getStoredUserId() {
  const userId = localStorage.getItem("userId");
  if (!userId) return null;

  const parsedUserId = Number(userId);
  return Number.isNaN(parsedUserId) ? null : parsedUserId;
}

export function TeamBoard() {
  const navigate = useNavigate();
  const { teamId = "python-study" } = useParams();
  const [openPost, setOpenPost] = useState<number | null>(null);
  const [isComposing, setIsComposing] = useState(false);
  const [postTitle, setPostTitle] = useState("");
  const [postBody, setPostBody] = useState("");
  const [localPosts, setLocalPosts] = useState<TeamPost[]>([]);
  const visiblePosts = [...localPosts, ...teamPosts];

  const handlePostSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const title = postTitle.trim();
    const excerpt = postBody.trim();
    if (!title || !excerpt) return;

    setLocalPosts((current) => [
      {
        tag: "NOTICE",
        title,
        excerpt,
        replies: [],
        author: "나",
        avatar: "user",
      },
      ...current,
    ]);
    setPostTitle("");
    setPostBody("");
    setIsComposing(false);
    setOpenPost(null);
  };

  return (
    <>
      <TeamHeader
        title="Team Board"
        subtitle="실시간으로 팀원들과 소통하고 학습 자료를 공유하세요."
        action={<button className="study-info-button" type="button" onClick={() => navigate(ROUTE_PATHS.studyDetail(teamId))}>스터디 정보 보기</button>}
      />
      <button
        className="topic-start"
        type="button"
        aria-label="게시글 작성"
        aria-expanded={isComposing}
        onClick={() => setIsComposing((current) => !current)}
      >
        <span aria-hidden="true" />
      </button>
      {isComposing && (
        <form className="post-compose" onSubmit={handlePostSubmit}>
          <Avatar name="user" />
          <div className="post-compose-fields">
            <input
              type="text"
              placeholder="제목을 입력하세요"
              aria-label="게시글 제목"
              value={postTitle}
              onChange={(event) => setPostTitle(event.target.value)}
            />
            <textarea
              placeholder="새로운 소식을 공유해보세요..."
              aria-label="게시글 내용"
              value={postBody}
              onChange={(event) => setPostBody(event.target.value)}
            />
          </div>
          <button className="post-submit" type="submit">Post</button>
        </form>
      )}
      <section className="post-list">
        {visiblePosts.map((post, index) => (
          <Post
            key={`${post.author}-${post.title}-${index}`}
            post={post}
            isOpen={openPost === index}
            onToggle={() => setOpenPost(openPost === index ? null : index)}
          />
        ))}
      </section>
    </>
  );
}

export function TeamAttendance() {
  const { teamId = "python-study" } = useParams();
  const attendanceStorageKey = `studyMate.attendance.${teamId}`;
  const [rows, setRows] = useState(() => {
    const storedRows = sessionStorage.getItem(attendanceStorageKey);
    if (!storedRows) return attendanceMembers;

    try {
      const parsedRows = JSON.parse(storedRows);
      return Array.isArray(parsedRows) ? parsedRows as typeof attendanceMembers : attendanceMembers;
    } catch {
      return attendanceMembers;
    }
  });
  const [showSavedToast, setShowSavedToast] = useState(false);
  const latestDateIndex = attendanceDates.length - 1;

  const toggleAttendance = (memberIndex: number, checkIndex: number) => {
    if (checkIndex !== latestDateIndex) return;

    setRows((current) => {
      const nextRows = current.map((member, index) => {
      if (index !== memberIndex) return member;

      const checks = member.checks.map((state, stateIndex) => {
        if (stateIndex !== checkIndex) return state;
        if (state === "scheduled") return "present";
        if (state === "present") return "absent";
        return "scheduled";
      });

      return { ...member, checks };
      });

      sessionStorage.setItem(attendanceStorageKey, JSON.stringify(nextRows));
      return nextRows;
    });
  };

  const handleSaveAttendance = () => {
    setShowSavedToast(true);
    window.setTimeout(() => setShowSavedToast(false), 2200);
  };

  return (
    <>
      {showSavedToast && <div className="attendance-toast" role="status">출석정보 저장되었습니다.</div>}
      <TeamHeader
        title="Attendance Board"
        subtitle="팀원 출석체크를 관리하세요."
        action={<button className="attendance-confirm" type="button" aria-label="출석 저장" onClick={handleSaveAttendance} />}
      />
      <section className="attendance-card">
        <div className="attendance-row head">
          <strong>Member Name</strong>
          {attendanceDates.map((date) => <strong key={date}>{date}</strong>)}
        </div>
        {rows.map(({ name, avatar, checks }, memberIndex) => (
          <div className="attendance-row" key={name}>
            <span>{avatar ? <Avatar name={avatar} /> : <i className="initial-avatar">{name.slice(0, 2)}</i>}{name}</span>
            {checks.map((state, index) => (
              <button
                className={`attendance-state ${state}`}
                type="button"
                aria-label={`${name} ${attendanceDates[index]} 출석 상태 ${state}`}
                disabled={index !== latestDateIndex}
                key={`${name}-${index}`}
                onClick={() => toggleAttendance(memberIndex, index)}
              />
            ))}
          </div>
        ))}
        <footer><span className="present">Present</span><span className="absent">Absent</span><span className="scheduled">Scheduled</span></footer>
      </section>
    </>
  );
}

export function TeamMembers() {
  const navigate = useNavigate();
  const { teamId = "python-study" } = useParams();
  const toastTimerRef = useRef<number | null>(null);
  const isRealTeam = !Number.isNaN(Number(teamId));
  const currentUserId = getStoredUserId();
  const [isDeletingStudy, setIsDeletingStudy] = useState(false);
  const [members, setMembers] = useState<TeamMemberResponse[]>([]);
  const [isLoadingMembers, setIsLoadingMembers] = useState(isRealTeam);
  const [memberError, setMemberError] = useState("");
  const [processingMemberId, setProcessingMemberId] = useState<number | "me" | null>(null);
  const [joinApplications, setJoinApplications] = useState<TeamApplicationResponse[]>([]);
  const [isLoadingApplications, setIsLoadingApplications] = useState(isRealTeam);
  const [applicationError, setApplicationError] = useState("");
  const [processingApplicationId, setProcessingApplicationId] = useState<number | null>(null);
  const [toastMessage, setToastMessage] = useState<{ type: "success" | "error"; text: string } | null>(null);

  const showToast = (message: { type: "success" | "error"; text: string }) => {
    if (toastTimerRef.current) window.clearTimeout(toastTimerRef.current);
    setToastMessage(message);
    toastTimerRef.current = window.setTimeout(() => setToastMessage(null), 2200);
  };

  const currentMember = members.find((member) => member.userId === currentUserId);
  const isCurrentUserLeader = currentMember?.roleCode.toUpperCase() === "LEADER";

  useEffect(() => {
    if (!isRealTeam) {
      setMembers([]);
      setIsLoadingMembers(false);
      setMemberError("샘플 팀에서는 팀원 목록을 불러올 수 없습니다.");
      return;
    }

    let isMounted = true;
    setIsLoadingMembers(true);
    setMemberError("");

    teamMembersApi.list(teamId)
      .then((response) => {
        if (!isMounted) return;
        setMembers(response.members);
      })
      .catch((error) => {
        if (!isMounted) return;
        setMembers([]);
        setMemberError(getTeamStudyApiErrorMessage(error, "팀원 목록을 불러오지 못했습니다."));
      })
      .finally(() => {
        if (isMounted) setIsLoadingMembers(false);
      });

    return () => {
      isMounted = false;
    };
  }, [isRealTeam, teamId]);

  useEffect(() => {
    if (!isRealTeam) {
      setJoinApplications([]);
      setIsLoadingApplications(false);
      setApplicationError("샘플 팀에서는 가입 요청을 불러올 수 없습니다.");
      return;
    }

    let isMounted = true;
    setIsLoadingApplications(true);
    setApplicationError("");

    applicationsApi.listTeam(teamId)
      .then((response) => {
        if (!isMounted) return;
        setJoinApplications(response.applications);
      })
      .catch((error) => {
        if (!isMounted) return;
        setJoinApplications([]);
        setApplicationError(getTeamStudyApiErrorMessage(error, "가입 요청을 불러오지 못했습니다."));
      })
      .finally(() => {
        if (isMounted) setIsLoadingApplications(false);
      });

    return () => {
      isMounted = false;
    };
  }, [isRealTeam, teamId]);

  const handleDeleteStudy = async () => {
    if (isDeletingStudy) return;
    const confirmed = window.confirm("스터디를 삭제하시겠습니까? 삭제 후에는 되돌릴 수 없습니다.");
    if (!confirmed) return;

    setIsDeletingStudy(true);
    try {
      await studiesApi.delete(teamId);
      clearStudyApiCache();
      navigate(ROUTE_PATHS.studies, { replace: true });
    } catch (error) {
      showToast({ type: "error", text: getTeamStudyApiErrorMessage(error, "스터디를 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.") });
    } finally {
      setIsDeletingStudy(false);
    }
  };

  const handleApproveApplication = async (applicationId: number) => {
    if (!isRealTeam || processingApplicationId) return;

    setApplicationError("");
    setProcessingApplicationId(applicationId);
    try {
      await applicationsApi.approveTeamApplication(teamId, applicationId);
      setJoinApplications((current) => current.filter((application) => application.applicationId !== applicationId));
      showToast({ type: "success", text: "가입 요청을 승인했습니다." });
    } catch (error) {
      showToast({ type: "error", text: getTeamStudyApiErrorMessage(error, "가입 요청을 승인하지 못했습니다.") });
    } finally {
      setProcessingApplicationId(null);
    }
  };

  const handleRejectApplication = async (applicationId: number) => {
    if (!isRealTeam || processingApplicationId) return;

    const rejectReason = window.prompt("거절 사유를 입력해 주세요.", "조건이 맞지 않아 거절합니다.");
    if (rejectReason === null) return;

    setApplicationError("");
    setProcessingApplicationId(applicationId);
    try {
      await applicationsApi.rejectTeamApplication(teamId, applicationId, {
        rejectReason: rejectReason.trim() || "조건이 맞지 않아 거절합니다.",
      });
      setJoinApplications((current) => current.filter((application) => application.applicationId !== applicationId));
      showToast({ type: "success", text: "가입 요청을 거절했습니다." });
    } catch (error) {
      showToast({ type: "error", text: getTeamStudyApiErrorMessage(error, "가입 요청을 거절하지 못했습니다.") });
    } finally {
      setProcessingApplicationId(null);
    }
  };

  const handleKickMember = async (member: TeamMemberResponse) => {
    if (!isRealTeam || processingMemberId) return;

    const confirmed = window.confirm(`${member.userName}님을 팀에서 내보내시겠습니까?`);
    if (!confirmed) return;

    setProcessingMemberId(member.memberId);
    try {
      await teamMembersApi.kick(teamId, member.memberId);
      setMembers((current) => current.filter((item) => item.memberId !== member.memberId));
      showToast({ type: "success", text: "팀원을 내보냈습니다." });
    } catch (error) {
      showToast({ type: "error", text: getTeamStudyApiErrorMessage(error, "팀원을 내보내지 못했습니다.") });
    } finally {
      setProcessingMemberId(null);
    }
  };

  const handleLeaveTeam = async () => {
    if (!isRealTeam || processingMemberId) return;

    const confirmed = window.confirm("이 팀에서 나가시겠습니까?");
    if (!confirmed) return;

    setProcessingMemberId("me");
    try {
      await teamMembersApi.leave(teamId);
      clearStudyApiCache();
      showToast({ type: "success", text: "팀에서 나갔습니다." });
      navigate(ROUTE_PATHS.studies, { replace: true });
    } catch (error) {
      showToast({ type: "error", text: getTeamStudyApiErrorMessage(error, "팀에서 나가지 못했습니다.") });
    } finally {
      setProcessingMemberId(null);
    }
  };

  return (
    <div className="members-page">
      {toastMessage && <Toast type={toastMessage.type}>{toastMessage.text}</Toast>}
      <TeamHeader
        title="Member Board"
        subtitle="스터디 팀원을 관리하세요."
        hideCount
        action={(
          <div className="member-header-actions">
            {currentMember && !isCurrentUserLeader && (
              <button className="team-leave-button" type="button" onClick={handleLeaveTeam} disabled={!isRealTeam || processingMemberId === "me"}>
                {processingMemberId === "me" ? "나가는 중..." : "팀 나가기"}
              </button>
            )}
            <button className="study-delete-button" type="button" onClick={handleDeleteStudy} disabled={isDeletingStudy}>{isDeletingStudy ? "삭제 중..." : "스터디 삭제"}</button>
          </div>
        )}
      />
      <section className="member-table">
        <header><h2>Active Members</h2><span>Total: {members.length}</span></header>
        <div className="member-row head">
          <strong>NAME</strong>
          <strong>ROLE</strong>
          <strong>JOINED</strong>
          <strong />
        </div>
        {isLoadingMembers && <p className="section-note">팀원 목록을 불러오는 중입니다.</p>}
        {memberError && <p className="section-note form-error">{memberError}</p>}
        {!isLoadingMembers && !memberError && members.length === 0 && (
          <p className="section-note">표시할 팀원이 없습니다.</p>
        )}
        {members.map((member) => (
          <div className="member-row" key={member.memberId}>
            <span><i className="initial-avatar">{getInitials(member.userName)}</i>{member.userName}</span>
            <span><em className={`role-${member.roleCode.toLowerCase()}`}>{member.roleCode}</em></span>
            <span className="member-joined-at">{formatMemberJoinedAt(member.joinedAt)}</span>
            {member.roleCode.toUpperCase() === "LEADER" || member.userId === currentUserId ? (
              <span aria-hidden="true" />
            ) : (
              <button type="button" onClick={() => handleKickMember(member)} disabled={processingMemberId === member.memberId}>
                {processingMemberId === member.memberId ? "..." : "kick"}
              </button>
            )}
          </div>
        ))}
      </section>
      <section className="member-table request-table">
        <header><h2>Join Requests</h2></header>
        {isLoadingApplications && <p className="section-note">가입 요청을 불러오는 중입니다.</p>}
        {applicationError && <p className="section-note form-error">{applicationError}</p>}
        {!isLoadingApplications && !applicationError && joinApplications.length === 0 && (
          <p className="section-note">대기 중인 가입 요청이 없습니다.</p>
        )}
        {joinApplications.map(({ applicationId, applicantName, message, appliedAt }) => (
          <div className="request-row" key={applicationId}>
            <span><i className="initial-avatar">{getInitials(applicantName)}</i><b>{applicantName}</b><small>{message || formatJoinRequestDate(appliedAt)}</small></span>
            <div>
              <button className="primary" type="button" onClick={() => handleApproveApplication(applicationId)} disabled={processingApplicationId === applicationId}>
                {processingApplicationId === applicationId ? "..." : "Accept"}
              </button>
              <button type="button" onClick={() => handleRejectApplication(applicationId)} disabled={processingApplicationId === applicationId}>
                Reject
              </button>
            </div>
          </div>
        ))}
        <button className="invite-link" type="button">초대 링크</button>
      </section>
    </div>
  );
}

function TeamHeader({
  title,
  subtitle,
  hideCount = false,
  action,
}: {
  title: string;
  subtitle: string;
  hideCount?: boolean;
  action?: ReactNode;
}) {
  return (
    <header className="team-header">
      <div><h1>{title}</h1><p>{subtitle}</p></div>
      {action ?? (!hideCount && <span className="active-count"><i />7 Active</span>)}
    </header>
  );
}

function Post({ post, isOpen, onToggle }: { post: TeamPost; isOpen: boolean; onToggle: () => void }) {
  const { tag, title, excerpt, replies, author, avatar } = post;
  const [draft, setDraft] = useState("");
  const [localReplies, setLocalReplies] = useState<TeamPost["replies"]>([]);
  const displayedReplies = [...replies, ...localReplies];

  const handleCommentSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const body = draft.trim();
    if (!body) return;

    setLocalReplies((current) => [...current, { author: "나", avatar: "user", time: "now", body }]);
    setDraft("");
  };

  return (
    <article className={`post-card${isOpen ? " is-open" : ""}`}>
      <header><div><span className={`post-tag post-tag-${tag.toLowerCase()}`}>{tag}</span><h2>{title}</h2></div></header>
      <p>{excerpt}</p>
      {isOpen && (
        <section className="comment-panel" aria-label="댓글">
          {displayedReplies.map((reply, index) => (
            <article className="comment-item" key={`${reply.author}-${index}`}>
              <Avatar name={reply.avatar} />
              <div>
                <header><strong>{reply.author}</strong><time>{reply.time}</time></header>
                <p>{reply.body}</p>
              </div>
            </article>
          ))}
          <form className="comment-form" onSubmit={handleCommentSubmit}>
            <input
              type="text"
              placeholder="Write a comment..."
              aria-label="댓글 입력"
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
            />
            <button type="submit" aria-label="댓글 전송" />
          </form>
        </section>
      )}
      <footer>
        <button className="comment-toggle" type="button" onClick={onToggle} aria-expanded={isOpen}>
          <span aria-hidden="true" />
          {displayedReplies.length}
        </button>
        <strong>{author}</strong>
        <Avatar name={avatar} />
      </footer>
    </article>
  );
}
