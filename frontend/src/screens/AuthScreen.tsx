import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { authInterestTags } from "../data";
import { TagList } from "../components/TagInput";
import { AvatarStack, Field } from "../components/Common";
import { ROUTE_PATHS } from "../routes/routingMap";
import graduationCapIcon from "../assets/graduation-cap.svg";
import globeIcon from "../assets/Icon.svg";

type Language = { code: string; label: string };
const LANGUAGES: Language[] = [
  { code: "KO", label: "한국어" },
  { code: "EN", label: "English" },
  { code: "ZH", label: "中文" },
];

export function AuthScreen({ mode }: { mode: "login" | "signup" }) {
  const navigate = useNavigate();
  const isLogin = mode === "login";
  const [lang, setLang] = useState(LANGUAGES[0]);
  const [showLangMenu, setShowLangMenu] = useState(false);
  const [signupStep, setSignupStep] = useState(1);  const [tags, setTags] = useState<string[]>(authInterestTags);

  const handleRemoveTag = (tagToRemove: string) => {
    setTags(tags.filter(tag => tag !== tagToRemove));
  };

  const handleAddTag = (newTag: string) => {
    if (!tags.includes(newTag)) {
      setTags([...tags, newTag.startsWith('#') ? newTag : `#${newTag}`]);
    }
  };
  return (
    <main className="auth-stage">
      <section className="auth-frame">
        <aside className="auth-aside">
          <div className="auth-brand">
            <img src={graduationCapIcon} alt="학사모 아이콘" className="brand-icon" />
            <b>STUDYMATE</b>
          </div>
          <div>
            <h1>나의 완벽한<br />스터디 파트너를<br />찾아보세요.</h1>
            <p>전 세계 유학생들과 함께 전공 지식을 나누고,<br />언어를 교환하며 함께 성장하는 지적인 커뮤니티입니다.</p>
          </div>
          <div className="live-card">
            <AvatarStack />
            <strong>지금 1,200명의 학우들이 함께<br />공부하고 있습니다.</strong>
          </div>
        </aside>
        <section className="auth-panel">
          <header className="auth-header">
            <div className="segmented">
              <div 
                className="segmented-highlight" 
                style={{ 
                  transform: isLogin ? 'translateX(0)' : 'translateX(100%)'
                }} 
              />
              <button 
                className={isLogin ? "active" : ""} 
                type="button" 
                onClick={() => {
                  navigate(ROUTE_PATHS.login);
                  setSignupStep(1);
                }}
              >
                로그인
              </button>
              <button 
                className={!isLogin ? "active" : ""} 
                type="button" 
                onClick={() => {
                  navigate(ROUTE_PATHS.signup);
                  setSignupStep(1);
                }}
              >
                회원가입
              </button>
            </div>
            
            <div className="lang-selector">
              <button 
                className="language-pill" 
                type="button" 
                onClick={() => setShowLangMenu(!showLangMenu)}
              >
                <img src={globeIcon} alt="Globe Icon" className="globe-icon" />
                {lang.label} ({lang.code}) <i className="arrow-down" />
              </button>
              
              {showLangMenu && (
                <div className="lang-dropdown">
                  {LANGUAGES.map(l => (
                    <button 
                      key={l.code} 
                      type="button" 
                      onClick={() => {
                        setLang(l);
                        setShowLangMenu(false);
                      }}
                      className={lang.code === l.code ? "active" : ""}
                    >
                      {l.label} ({l.code})
                    </button>
                  ))}
                </div>
              )}
            </div>
          </header>
          
          <div className="auth-content">
            {/* === 로그인 모드 === */}
            <div className={`auth-phase ${isLogin ? 'active' : 'inactive-left'}`}>
              <h2>반가워요, 학우님!</h2>
              <p>서비스 이용을 위해 학교 계정으로 로그인 해주세요.</p>
              <form className="form-stack" onSubmit={(event) => event.preventDefault()}>
                <Field label="대학교 이메일" placeholder="studymate@ajou.ac.kr" icon="@" />
                <Field label="비밀번호" placeholder="********" type="password" autoComplete="current-password" />
                <button className="primary wide" type="button" onClick={() => navigate(ROUTE_PATHS.home)}>
                  로그인 <span>→</span>
                </button>
              </form>
              <footer className="auth-footer">
                <p>계정이 없으신가요? <button type="button" onClick={() => {
                  navigate(ROUTE_PATHS.signup);
                  setSignupStep(1);
                }}>지금 가입하세요</button></p>
              </footer>
            </div>

            {/* === 회원가입: 스텝 1 === */}
            <div className={`auth-phase ${!isLogin && signupStep === 1 ? 'active' : isLogin ? 'inactive-right' : 'inactive-left'}`}>
              <h2>새로운 시작을 함께해요!</h2>
              <p>스터디 메이트가 되기 위해 이메일 인증을 진행해 주세요.</p>
              <form className="form-stack" onSubmit={(event) => event.preventDefault()}>
                <Field label="대학교 이메일" placeholder="studymate@ajou.ac.kr" icon="@" />
                <Field label="인증 코드" placeholder="인증 코드 6자리 입력" icon="✓" />
                <button className="primary wide" type="button" onClick={() => setSignupStep(2)}>
                  다음 단계 <span>→</span>
                </button>
              </form>
            </div>

            {/* === 회원가입: 스텝 2 === */}
            <div className={`auth-phase ${!isLogin && signupStep === 2 ? 'active' : 'inactive-right'}`}>
              <h2>거의 다 왔어요!</h2>
              <p>비밀번호와 관심사를 설정해 주세요.</p>
              <form className="form-stack" onSubmit={(event) => event.preventDefault()}>
                <Field label="비밀번호" placeholder="********" type="password" autoComplete="new-password" />
                <Field label="비밀번호 확인" placeholder="********" type="password" autoComplete="new-password" />
                
                <TagList tags={tags} onRemoveTag={handleRemoveTag} onAddTag={handleAddTag} />
                
                <div className="button-group">
                  <button className="secondary wide" type="button" onClick={() => setSignupStep(1)}>
                    이전 단계
                  </button>
                  <button className="primary wide" type="button" onClick={() => navigate(ROUTE_PATHS.home)}>
                    회원가입 완료 <span>→</span>
                  </button>
                </div>
              </form>
            </div>
          </div>
          
          <nav className="global-legal-nav">
            <span>이용약관</span>
            <span>개인정보처리방침</span>
            <span>도움말</span>
          </nav>
        </section>
      </section>
    </main>
  );
}
