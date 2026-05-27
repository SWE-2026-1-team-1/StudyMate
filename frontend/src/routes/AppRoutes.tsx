import { Navigate, Outlet, Route, Routes, useLocation } from "react-router-dom";
import { AppLayout } from "../layouts/AppLayout";
import { AuthLayout } from "../layouts/AuthLayout";
import { TeamLayout } from "../layouts/TeamLayout";
import { AuthScreen } from "../screens/AuthScreen";
import { CreateStudy, ExplorePage, MainDashboard, MyPage, StudyDetail } from "../screens/StudyScreens";
import { TeamAttendance, TeamBoard, TeamMembers } from "../screens/TeamScreens";
import { ROUTE_PATHS } from "./routingMap";

function RequireAuth() {
  const location = useLocation();
  const accessToken = localStorage.getItem("accessToken");

  if (!accessToken) {
    return <Navigate to={ROUTE_PATHS.login} replace state={{ from: location }} />;
  }

  return <Outlet />;
}

export function AppRoutes() {
  return (
    <Routes>
      <Route index element={<Navigate to={ROUTE_PATHS.login} replace />} />

      <Route element={<AuthLayout />}>
        <Route path={ROUTE_PATHS.login} element={<AuthScreen mode="login" />} />
        <Route path={ROUTE_PATHS.signup} element={<AuthScreen mode="signup" />} />
      </Route>

      <Route element={<RequireAuth />}>
        <Route element={<AppLayout />}>
          <Route path={ROUTE_PATHS.home} element={<MainDashboard />} />
          <Route path="studies" element={<ExplorePage />} />
          <Route path="studies/:studyId" element={<StudyDetail />} />
          <Route path="studies/new" element={<Navigate to={ROUTE_PATHS.createBasic} replace />} />
          <Route path="studies/new/basic" element={<CreateStudy step={1} />} />
          <Route path="studies/new/rules" element={<CreateStudy step={2} />} />
          <Route path="studies/new/schedule" element={<CreateStudy step={3} />} />
          <Route path="mypage" element={<MyPage />} />
        </Route>

        <Route path="teams/:teamId" element={<TeamLayout />}>
          <Route index element={<Navigate to="board" replace />} />
          <Route path="board" element={<TeamBoard />} />
          <Route path="attendance" element={<TeamAttendance />} />
          <Route path="members" element={<TeamMembers />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to={ROUTE_PATHS.home} replace />} />
    </Routes>
  );
}
