import { useState } from "react";
import { Frame, ScreenSwitcher, TopBar } from "./components/Common";
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

  const isAuth = screen === "login" || screen === "signup";
  const content = (
    <>
      {screen === "main" && <MainDashboard current={screen} onNavigate={navigate} />}
      {screen === "explore" && <ExplorePage current={screen} onNavigate={navigate} />}
      {screen === "detail" && <StudyDetail current={screen} onNavigate={navigate} />}
      {screen === "mypage" && <MyPage current={screen} onNavigate={navigate} />}
      {screen === "create-basic" && <CreateStudy current={screen} step={1} onNavigate={navigate} />}
      {screen === "create-rules" && <CreateStudy current={screen} step={2} onNavigate={navigate} />}
      {screen === "create-schedule" && <CreateStudy current={screen} step={3} onNavigate={navigate} />}
      {screen === "team-board" && <TeamBoard current={screen} onNavigate={navigate} />}
      {screen === "team-attendance" && <TeamAttendance current={screen} onNavigate={navigate} />}
      {screen === "team-members" && <TeamMembers current={screen} onNavigate={navigate} />}
    </>
  );

  return (
    <div className="app-shell">
      <ScreenSwitcher current={screen} onChange={navigate} />
      {isAuth ? (
        <AuthScreen mode={screen} onNavigate={navigate} />
      ) : (
        <Frame>
          <TopBar current={screen} onNavigate={navigate} />
          {content}
        </Frame>
      )}
    </div>
  );
}
