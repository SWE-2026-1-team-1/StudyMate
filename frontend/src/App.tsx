import { useState } from "react";
import { ScreenSwitcher } from "./components/Common";
import { AuthScreen } from "./screens/AuthScreen";
import { CreateStudy, ExplorePage, MainDashboard, MyPage, StudyDetail } from "./screens/StudyScreens";
import { TeamAttendance, TeamBoard, TeamMembers } from "./screens/TeamScreens";
import type { ScreenId } from "./types";

export function App() {
  const [screen, setScreen] = useState<ScreenId>("login");
  const navigate = (nextScreen: ScreenId) => {
    setScreen(nextScreen);
    window.scrollTo({ top: 0, left: 0 });
  };

  return (
    <div className="app-shell">
      <ScreenSwitcher current={screen} onChange={navigate} />
      {(screen === "login" || screen === "signup") && <AuthScreen mode={screen} onNavigate={navigate} />}
      {screen === "main" && <MainDashboard onNavigate={navigate} />}
      {screen === "explore" && <ExplorePage onNavigate={navigate} />}
      {screen === "detail" && <StudyDetail onNavigate={navigate} />}
      {screen === "mypage" && <MyPage onNavigate={navigate} />}
      {screen === "create-basic" && <CreateStudy step={1} onNavigate={navigate} />}
      {screen === "create-rules" && <CreateStudy step={2} onNavigate={navigate} />}
      {screen === "create-schedule" && <CreateStudy step={3} onNavigate={navigate} />}
      {screen === "team-board" && <TeamBoard onNavigate={navigate} />}
      {screen === "team-attendance" && <TeamAttendance onNavigate={navigate} />}
      {screen === "team-members" && <TeamMembers onNavigate={navigate} />}
    </div>
  );
}
