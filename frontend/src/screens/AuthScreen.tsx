import { authInterestTags } from "../data";
import { AvatarStack, Field } from "../components/Common";
import type { ScreenId } from "../types";

type Navigate = (screen: ScreenId) => void;

export function AuthScreen({ mode, onNavigate }: { mode: "login" | "signup"; onNavigate: Navigate }) {
  const isLogin = mode === "login";

  return (
    <main className="auth-stage">
      <section className="auth-frame">
        <aside className="auth-aside">
          <div className="auth-brand">
            <span>▰</span>
            <b>STUDYMATE</b>
          </div>
          <div>
            <h1>나의 완벽한 스터디 파트너를 찾아보세요.</h1>
            <p>전 세계 유학생들과 함께 전공 지식을 나누고, 언어를 교환하며 함께 성장하는 지적인 커뮤니티입니다.</p>
          </div>
          <div className="live-card">
            <AvatarStack />
            <strong>지금 1,200명의 학우들이 함께 공부하고 있습니다.</strong>
          </div>
        </aside>
        <section className="auth-panel">
          <header className="auth-header">
            <div className="segmented">
              <button className={isLogin ? "active" : ""} type="button" onClick={() => onNavigate("login")}>로그인</button>
              <button className={!isLogin ? "active" : ""} type="button" onClick={() => onNavigate("signup")}>회원가입</button>
            </div>
            <button className="language-pill" type="button"><span>◉</span> 한국어 (KO)⌄</button>
          </header>
          <div className="auth-content">
            <h2>{isLogin ? "반가워요, 학우님!" : "회원가입"}</h2>
            {isLogin && <p>서비스 이용을 위해 학교 계정으로 로그인 해주세요.</p>}
            <form className="form-stack" onSubmit={(event) => event.preventDefault()}>
              <Field label="대학교 이메일" placeholder="example@univ.ac.kr" icon="@" />
              {!isLogin && <Field label="인증 코드" placeholder="example@univ.ac.kr" icon="@" />}
              <Field label="비밀번호" placeholder="********" type="password" autoComplete={isLogin ? "current-password" : "new-password"} />
              {!isLogin && <Field label="비밀번호 확인" placeholder="********" type="password" autoComplete="new-password" />}
              {!isLogin && (
                <div className="keyword-set auth-keywords">
                  {authInterestTags.map((tag, index) => <span className={`tag-${index + 1}`} key={tag}>{tag}{index < 2 && <b>×</b>}</span>)}
                  <button type="button">+ Add Tag</button>
                </div>
              )}
              <button className="primary wide" type="button" onClick={() => onNavigate("main")}>
                {isLogin ? "로그인" : "인증 메일 보내기"} <span>→</span>
              </button>
            </form>
            <footer className="auth-footer">
              {isLogin && <p>계정이 없으신가요? <button type="button" onClick={() => onNavigate("signup")}>지금 가입하세요</button></p>}
              <nav>
                <span>이용약관</span>
                <span>개인정보처리방침</span>
                <span>도움말</span>
              </nav>
            </footer>
          </div>
        </section>
      </section>
    </main>
  );
}
