import { useState, type FormEvent, type ReactNode } from "react";
import { attendanceDates, attendanceMembers, joinRequests, teamMembers, teamPosts } from "../data";
import { Avatar } from "../components/Common";
import type { TeamPost } from "../types";

export function TeamBoard() {
  const [openPost, setOpenPost] = useState<number | null>(null);

  return (
    <>
      <TeamHeader
        title="Team Board"
        subtitle="실시간으로 팀원들과 소통하고 학습 자료를 공유하세요."
        action={<button className="study-info-button" type="button">스터디 정보 보기</button>}
      />
      <button className="topic-start" type="button" aria-label="게시글 작성"><span aria-hidden="true" /></button>
      <section className="post-list">
        {teamPosts.map((post, index) => (
          <Post
            key={`${post.title}-${index}`}
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
  return (
    <>
      <TeamHeader title="Attendance Board" subtitle="팀원 출석체크를 관리하세요." />
      <section className="attendance-card">
        <div className="attendance-row head">
          <strong>Member Name</strong>
          {attendanceDates.map((date) => <strong key={date}>{date}</strong>)}
        </div>
        {attendanceMembers.map(({ name, avatar, checks }) => (
          <div className="attendance-row" key={name}>
            <span>{avatar ? <Avatar name={avatar} /> : <i className="initial-avatar">{name.slice(0, 2)}</i>}{name}</span>
            {checks.map((state, index) => <b className={state} key={`${name}-${index}`} />)}
          </div>
        ))}
        <footer><span className="present">Present</span><span className="absent">Absent</span><span className="scheduled">Scheduled</span></footer>
      </section>
    </>
  );
}

export function TeamMembers() {
  return (
    <>
      <TeamHeader title="Member Board" subtitle="스터디 팀원을 관리하세요." hideCount />
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
            <span><em>{role}</em></span>
            <span className="rate"><i><b style={{ width: rate }} /></i>{rate}</span>
            <button type="button">⋮</button>
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
    </>
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
