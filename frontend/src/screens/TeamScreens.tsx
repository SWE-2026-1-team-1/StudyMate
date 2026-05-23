import type { ReactNode } from "react";
import { attendanceDates, attendanceMembers, joinRequests, teamMembers, teamPosts } from "../data";
import { Avatar } from "../components/Common";

export function TeamBoard() {
  return (
    <>
      <TeamHeader
        title="Team Board"
        subtitle="실시간으로 팀원들과 소통하고 학습 자료를 공유하세요."
        action={<button className="study-info-button" type="button">스터디 정보 보기</button>}
      />
      <button className="topic-start" type="button" aria-label="게시글 작성"><span aria-hidden="true" /></button>
      <section className="post-list">
        {teamPosts.map((post, index) => <Post key={`${post.title}-${index}`} {...post} />)}
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

function Post({
  tag,
  title,
  excerpt,
  time,
  comments,
  likes,
  author,
  avatar,
}: {
  tag: string;
  title: string;
  excerpt: string;
  time: string;
  comments: number;
  likes: number;
  author: string;
  avatar: string;
}) {
  return (
    <article className="post-card">
      <header><div><span className={`post-tag post-tag-${tag.toLowerCase()}`}>{tag}</span><h2>{title}</h2></div><time>{time}</time></header>
      <p>{excerpt}</p>
      <footer><b>▱ {comments}</b><b>♡ {likes}</b><strong>{author}</strong><Avatar name={avatar} /></footer>
    </article>
  );
}
