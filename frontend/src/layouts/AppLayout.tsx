import { Outlet } from "react-router-dom";
import { Frame, TopBar } from "../components/Common";

export function AppLayout() {
  return (
    <Frame>
      <TopBar />
      <Outlet />
    </Frame>
  );
}
