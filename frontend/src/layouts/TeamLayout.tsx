import { NavLink, Outlet, useParams } from "react-router-dom";
import { Frame, TopBar } from "../components/Common";
import { ROUTE_PATHS } from "../routes/routingMap";

export function TeamLayout() {
  const { teamId = "python-study" } = useParams();

  return (
    <Frame>
      <TopBar />
      <div className="team-shell">
        <aside className="team-nav">
          <div className="team-logo"><span>✣</span><b>파이썬 스터디</b><small>CS302 PROJECT</small></div>
          <NavLink to={ROUTE_PATHS.teamBoard(teamId)}>
            <span className="team-nav-icon team-nav-icon-board" aria-hidden="true" />
            게시판
          </NavLink>
          <NavLink to={ROUTE_PATHS.teamAttendance(teamId)}>
            <span className="team-nav-icon team-nav-icon-attendance" aria-hidden="true" />
            출석체크
          </NavLink>
          <NavLink to={ROUTE_PATHS.teamMembers(teamId)}>
            <span className="team-nav-icon team-nav-icon-member" aria-hidden="true" />
            팀원관리
          </NavLink>
        </aside>
        <main className="team-content content-container">
          <Outlet />
        </main>
      </div>
    </Frame>
  );
}
