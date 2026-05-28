import { useEffect, useState } from "react";
import { NavLink, Outlet, useParams } from "react-router-dom";
import { Frame, TopBar } from "../components/Common";
import { studiesApi, type StudyDetailResponse } from "../api/studies";
import { ROUTE_PATHS } from "../routes/routingMap";
import { useLanguage } from "../i18n";

export function TeamLayout() {
  const { teamId = "python-study" } = useParams();
  const { translate } = useLanguage();
  const [study, setStudy] = useState<StudyDetailResponse | null>(null);
  const [studyLoadFailed, setStudyLoadFailed] = useState(false);

  useEffect(() => {
    if (Number.isNaN(Number(teamId))) {
      setStudy(null);
      setStudyLoadFailed(false);
      return;
    }

    let isMounted = true;
    setStudyLoadFailed(false);

    studiesApi.get(teamId)
      .then((response) => {
        if (!isMounted) return;
        setStudy(response);
      })
      .catch(() => {
        if (!isMounted) return;
        setStudy(null);
        setStudyLoadFailed(true);
      });

    return () => {
      isMounted = false;
    };
  }, [teamId]);

  const studyName = study?.title ?? "파이썬 스터디";
  const studyMeta = study
    ? `${translate(study.status === "OPEN" ? "모집중" : "마감")} · ${study.currentMembers}/${study.maxMembers}${translate("명")}`
    : studyLoadFailed ? translate("스터디 정보 없음") : "CS302 PROJECT";

  return (
    <Frame>
      <TopBar />
      <div className="team-shell">
        <aside className="team-nav">
          <div className="team-logo"><span className="team-logo-icon" aria-hidden="true" /><b>{translate(studyName)}</b><small>{studyMeta}</small></div>
          <NavLink to={ROUTE_PATHS.teamBoard(teamId)}>
            <span className="team-nav-icon team-nav-icon-board" aria-hidden="true" />
            {translate("게시판")}
          </NavLink>
          <NavLink to={ROUTE_PATHS.teamAttendance(teamId)}>
            <span className="team-nav-icon team-nav-icon-attendance" aria-hidden="true" />
            {translate("출석체크")}
          </NavLink>
          <NavLink to={ROUTE_PATHS.teamMembers(teamId)}>
            <span className="team-nav-icon team-nav-icon-member" aria-hidden="true" />
            {translate("팀원관리")}
          </NavLink>
        </aside>
        <main className="team-content content-container">
          <Outlet />
        </main>
      </div>
    </Frame>
  );
}
