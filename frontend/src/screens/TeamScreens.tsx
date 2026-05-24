import { useState, type FormEvent, type ReactNode } from "react";
import { attendanceDates, attendanceMembers, joinRequests, teamMembers, teamPosts } from "../data";
import { Avatar } from "../components/Common";
import type { TeamPost } from "../types";

export function TeamBoard() {
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
        action={<button className="study-info-button" type="button">스터디 정보 보기</button>}
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
  const [rows, setRows] = useState(attendanceMembers);
  const [showSavedToast, setShowSavedToast] = useState(false);
  const latestDateIndex = attendanceDates.length - 1;

  const toggleAttendance = (memberIndex: number, checkIndex: number) => {
    if (checkIndex !== latestDateIndex) return;

    setRows((current) => current.map((member, index) => {
      if (index !== memberIndex) return member;

      const checks = member.checks.map((state, stateIndex) => {
        if (stateIndex !== checkIndex) return state;
        if (state === "scheduled") return "present";
        if (state === "present") return "absent";
        return "scheduled";
      });

      return { ...member, checks };
    }));
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
  return (
    <div className="members-page">
      <TeamHeader
        title="Member Board"
        subtitle="스터디 팀원을 관리하세요."
        hideCount
        action={<button className="study-delete-button" type="button">스터디 삭제</button>}
      />
      <section className="member-table">
        <header><h2>Active Members</h2><span>Total: 3</span></header>
        <div className="member-row head">
          <strong>NAME</strong>
          <strong>ROLE</strong>
          <strong>ATTENDANCE RATE</strong>
          <strong />
        </div>
        {teamMembers.map(({ name, role, rate, avatar }) => (
          <div className="member-row" key={name}>
            <span><Avatar name={avatar} />{name}</span>
            <span><em className={`role-${role.toLowerCase()}`}>{role}</em></span>
            <span className="rate"><i><b style={{ width: rate }} /></i>{rate}</span>
            {role === "LEADER" ? <span aria-hidden="true" /> : <button type="button">kick</button>}
          </div>
        ))}
      </section>
      <section className="member-table request-table">
        <header><h2>Join Requests</h2></header>
        {joinRequests.map(({ name, date, avatar }) => (
          <div className="request-row" key={name}>
            <span><Avatar name={avatar} /><b>{name}</b><small>{date}</small></span>
            <div><button className="primary" type="button">Accept</button><button type="button">Reject</button></div>
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
