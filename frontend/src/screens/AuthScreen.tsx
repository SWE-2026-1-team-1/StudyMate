import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { authInterestTags } from "../data";
import { TagList } from "../components/TagInput";
import { AvatarStack, Field } from "../components/Common";
import { ROUTE_PATHS } from "../routes/routingMap";
import { authApi } from "../api/auth";
import { profileApi } from "../api/profile";
import graduationCapIcon from "../assets/graduation-cap.svg";
import globeIcon from "../assets/language.svg";

type Language = { code: string; label: string };
const LANGUAGES: Language[] = [
  { code: "KO", label: "한국어" },
  { code: "EN", label: "English" },
  { code: "ZH", label: "中文" },
];

type AuthMessage = { type: "success" | "error"; text: string } | null;
type AuthPayload = Record<string, unknown>;

function readAuthField(payload: unknown, keys: string[]): string | undefined {
  if (!payload || typeof payload !== "object") return undefined;

  const data = payload as AuthPayload;
  for (const key of keys) {
    const value = data[key];
    if (typeof value === "string" || typeof value === "number") return String(value);
  }

  return readAuthField(data.data, keys) ?? readAuthField(data.user, keys);
}

function persistAuthSession(payload: unknown) {
  const accessToken = readAuthField(payload, ["accessToken", "access_token", "token"]);
  const refreshToken = readAuthField(payload, ["refreshToken", "refresh_token"]);
  const userId = readAuthField(payload, ["userId", "memberId", "id"]);

  if (accessToken) localStorage.setItem("accessToken", accessToken);
  if (refreshToken) localStorage.setItem("refreshToken", refreshToken);
  if (userId) localStorage.setItem("userId", userId);
}

function getApiErrorMessage(error: unknown, fallback: string) {
  if (!error || typeof error !== "object") return fallback;
  const response = (error as { response?: { data?: unknown } }).response;
  const data = response?.data;

  if (typeof data === "string") return data;
  if (data && typeof data === "object") {
    const message = (data as { message?: unknown; error?: unknown }).message ?? (data as { error?: unknown }).error;
    if (typeof message === "string") return message;
  }

  return fallback;
}

function getLoginErrorMessage(error: unknown) {
  if (!error || typeof error !== "object") return "로그인에 실패했습니다.";

  const response = (error as { response?: { status?: number } }).response;
  if (!response) return "서버에 연결할 수 없습니다.";

  if (response.status === 401) return "이메일 또는 비밀번호가 올바르지 않습니다.";
  if (response.status === 404) return "가입되지 않은 이메일입니다.";

  return "로그인에 실패했습니다.";
}

export function AuthScreen({ mode }: { mode: "login" | "signup" }) {
  const navigate = useNavigate();
  const isLogin = mode === "login";
  const [lang, setLang] = useState(LANGUAGES[0]);
  const [showLangMenu, setShowLangMenu] = useState(false);
  const [signupStep, setSignupStep] = useState(1);
  const [tags, setTags] = useState<string[]>(authInterestTags);
  const [email, setEmail] = useState("");
  const [name, setName] = useState("");
  const [school, setSchool] = useState("");
  const [major, setMajor] = useState("");
  const [bio, setBio] = useState("");
  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [emailCode, setEmailCode] = useState("");
  const [isEmailVerified, setIsEmailVerified] = useState(false);
  const [message, setMessage] = useState<AuthMessage>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isSendingCode, setIsSendingCode] = useState(false);
  const [isVerifyingCode, setIsVerifyingCode] = useState(false);

  const handleRemoveTag = (tagToRemove: string) => {
    setTags(tags.filter(tag => tag !== tagToRemove));
  };

  const handleAddTag = (newTag: string) => {
    if (!tags.includes(newTag)) {
      setTags([...tags, newTag.startsWith('#') ? newTag : `#${newTag}`]);
    }
  };

  const resetAuthState = () => {
    setSignupStep(1);
    setMessage(null);
    setEmailCode("");
    setName("");
    setSchool("");
    setMajor("");
    setBio("");
    setTags(authInterestTags);
    setPassword("");
    setPasswordConfirm("");
    setIsEmailVerified(false);
  };

  const handleLogin = async () => {
    if (!email.trim() || !password) {
      setMessage({ type: "error", text: "이메일과 비밀번호를 입력해 주세요." });
      return;
    }

    setIsSubmitting(true);
    setMessage(null);

    try {
      const response = await authApi.login({ email: email.trim(), password });
      persistAuthSession(response);
      navigate(ROUTE_PATHS.home);
    } catch (error) {
      setMessage({ type: "error", text: getLoginErrorMessage(error) });
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleSendCode = async () => {
    if (!email.trim()) {
      setMessage({ type: "error", text: "대학교 이메일을 먼저 입력해 주세요." });
      return;
    }

    setIsSendingCode(true);
    setMessage(null);
    setIsEmailVerified(false);

    try {
      await authApi.sendEmailCode({ email: email.trim() });
      setMessage({ type: "success", text: "인증코드를 전송했습니다. 메일함을 확인해 주세요." });
    } catch (error) {
      setMessage({ type: "error", text: getApiErrorMessage(error, "인증코드 전송에 실패했습니다.") });
    } finally {
      setIsSendingCode(false);
    }
  };

  const handleVerifyCode = async () => {
    if (!email.trim() || !emailCode.trim()) {
      setMessage({ type: "error", text: "이메일과 인증 코드를 입력해 주세요." });
      return;
    }

    setIsVerifyingCode(true);
    setMessage(null);

    try {
      await authApi.verifyEmail({ email: email.trim(), code: emailCode.trim() });
      setIsEmailVerified(true);
      setSignupStep(2);
      setMessage({ type: "success", text: "이메일 인증이 완료되었습니다." });
    } catch (error) {
      setIsEmailVerified(false);
      setMessage({ type: "error", text: getApiErrorMessage(error, "인증 코드 확인에 실패했습니다.") });
    } finally {
      setIsVerifyingCode(false);
    }
  };

  const handleSignupCredentials = () => {
    if (!isEmailVerified) {
      setSignupStep(1);
      setMessage({ type: "error", text: "이메일 인증을 먼저 완료해 주세요." });
      return;
    }
    if (!password || !passwordConfirm) {
      setMessage({ type: "error", text: "비밀번호를 입력해 주세요." });
      return;
    }
    if (password !== passwordConfirm) {
      setMessage({ type: "error", text: "비밀번호가 서로 일치하지 않습니다." });
      return;
    }
    if (!/^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,20}$/.test(password)) {
      setMessage({ type: "error", text: "비밀번호는 영문과 숫자를 포함한 8~20자여야 합니다." });
      return;
    }

    if (tags.length === 0) {
      setMessage({ type: "error", text: "관심 태그를 하나 이상 선택해 주세요." });
      return;
    }

    setMessage(null);
    setSignupStep(3);
  };

  const handleSignup = async () => {
    if (!isEmailVerified) {
      setSignupStep(1);
      setMessage({ type: "error", text: "이메일 인증을 먼저 완료해 주세요." });
      return;
    }
    if (!password || !passwordConfirm || password !== passwordConfirm) {
      setSignupStep(2);
      setMessage({ type: "error", text: "비밀번호를 다시 확인해 주세요." });
      return;
    }
    if (!name.trim()) {
      setMessage({ type: "error", text: "이름을 입력해 주세요." });
      return;
    }
    if (!school.trim()) {
      setMessage({ type: "error", text: "학교를 입력해 주세요." });
      return;
    }
    if (!major.trim()) {
      setMessage({ type: "error", text: "전공을 입력해 주세요." });
      return;
    }
    if (!bio.trim()) {
      setMessage({ type: "error", text: "자기소개를 입력해 주세요." });
      return;
    }

    setIsSubmitting(true);
    setMessage(null);

    try {
      const signupResponse = await authApi.signup({
        email: email.trim(),
        password,
        name: name.trim(),
      });
      persistAuthSession(signupResponse);

      const loginResponse = await authApi.login({ email: email.trim(), password });
      persistAuthSession(loginResponse);

      await profileApi.create({
        name: name.trim(),
        school: school.trim(),
        major: major.trim(),
        bio: bio.trim(),
        interestTags: tags.map((tag) => tag.replace(/^#/, "")).filter(Boolean),
      });

      navigate(ROUTE_PATHS.home);
    } catch (error) {
      setMessage({ type: "error", text: getApiErrorMessage(error, "회원가입에 실패했습니다.") });
    } finally {
      setIsSubmitting(false);
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
                  resetAuthState();
                }}
              >
                로그인
              </button>
              <button 
                className={!isLogin ? "active" : ""} 
                type="button" 
                onClick={() => {
                  navigate(ROUTE_PATHS.signup);
                  resetAuthState();
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
              <form className="form-stack" onSubmit={(event) => {
                event.preventDefault();
                handleLogin();
              }}>
                <Field label="대학교 이메일" placeholder="studymate@ajou.ac.kr" icon="@" value={email} onChange={(event) => setEmail(event.target.value)} disabled={isSubmitting} />
                <Field label="비밀번호" placeholder="********" type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} disabled={isSubmitting} />
                {message && isLogin && <p className={`auth-feedback login-feedback ${message.type}`}>{message.text}</p>}
                <button className="primary wide" type="submit" disabled={isSubmitting}>
                  {isSubmitting ? "로그인 중..." : "로그인"} <span>→</span>
                </button>
              </form>
              <footer className="auth-footer">
                <p>계정이 없으신가요? <button type="button" onClick={() => {
                  navigate(ROUTE_PATHS.signup);
                  resetAuthState();
                }}>지금 가입하세요</button></p>
              </footer>
            </div>

            {/* === 회원가입: 스텝 1 === */}
            <div className={`auth-phase ${!isLogin && signupStep === 1 ? 'active' : isLogin ? 'inactive-right' : 'inactive-left'}`}>
              <h2>새로운 시작을 함께해요!</h2>
              <p>스터디 메이트가 되기 위해 이메일 인증을 진행해 주세요.</p>
              <form className="form-stack" onSubmit={(event) => {
                event.preventDefault();
                handleVerifyCode();
              }}>
                <Field label="대학교 이메일" placeholder="studymate@ajou.ac.kr" icon="@" value={email} onChange={(event) => setEmail(event.target.value)} disabled={isSendingCode || isVerifyingCode} />
                <button className="verify-btn" type="button" onClick={handleSendCode} disabled={isSendingCode || isVerifyingCode}>
                  {isSendingCode ? "전송 중..." : "인증코드 보내기"}
                </button>
                <Field label="인증 코드" placeholder="인증 코드 6자리 입력" icon="✓" value={emailCode} onChange={(event) => setEmailCode(event.target.value)} disabled={isVerifyingCode} />
                {message && !isLogin && signupStep === 1 && <p className={`auth-feedback signup-feedback ${message.type}`}>{message.text}</p>}
                <button className="primary wide" type="submit" disabled={isVerifyingCode}>
                  {isVerifyingCode ? "확인 중..." : "다음 단계"} <span>→</span>
                </button>
              </form>
            </div>

            {/* === 회원가입: 스텝 2 === */}
            <div className={`auth-phase ${!isLogin && signupStep === 2 ? 'active' : 'inactive-right'}`}>
              <h2>거의 다 왔어요!</h2>
              <p>비밀번호와 관심사를 설정해 주세요.</p>
              <form className="form-stack" onSubmit={(event) => {
                event.preventDefault();
                handleSignupCredentials();
              }}>
                <Field label="비밀번호" placeholder="********" type="password" autoComplete="new-password" value={password} onChange={(event) => setPassword(event.target.value)} disabled={isSubmitting} />
                <Field label="비밀번호 확인" placeholder="********" type="password" autoComplete="new-password" value={passwordConfirm} onChange={(event) => setPasswordConfirm(event.target.value)} disabled={isSubmitting} />
                
                <TagList tags={tags} onRemoveTag={handleRemoveTag} onAddTag={handleAddTag} />
                {message && !isLogin && signupStep === 2 && <p className={`auth-feedback signup-feedback ${message.type}`}>{message.text}</p>}
                
                <div className="button-group">
                  <button className="secondary wide" type="button" onClick={() => setSignupStep(1)} disabled={isSubmitting}>
                    이전 단계
                  </button>
                  <button className="primary wide" type="submit" disabled={isSubmitting}>
                    다음 단계 <span>→</span>
                  </button>
                </div>
              </form>
            </div>

            {/* === 회원가입: 스텝 3 === */}
            <div className={`auth-phase ${!isLogin && signupStep === 3 ? 'active' : 'inactive-right'}`}>
              <h2>프로필을 완성해요</h2>
              <p>스터디 매칭에 사용할 기본 정보를 입력해 주세요.</p>
              <form className="form-stack" onSubmit={(event) => {
                event.preventDefault();
                handleSignup();
              }}>
                <Field label="이름" placeholder="홍길동" value={name} onChange={(event) => setName(event.target.value)} disabled={isSubmitting} />
                <Field label="학교" placeholder="Ajou University" value={school} onChange={(event) => setSchool(event.target.value)} disabled={isSubmitting} />
                <Field label="전공" placeholder="Software Engineering" value={major} onChange={(event) => setMajor(event.target.value)} disabled={isSubmitting} />
                <label>
                  자기소개
                  <textarea
                    placeholder="관심 분야와 함께 공부하고 싶은 목표를 적어주세요."
                    value={bio}
                    onChange={(event) => setBio(event.target.value)}
                    disabled={isSubmitting}
                  />
                </label>
                {message && !isLogin && signupStep === 3 && <p className={`auth-feedback signup-feedback ${message.type}`}>{message.text}</p>}
                
                <div className="button-group">
                  <button className="secondary wide" type="button" onClick={() => setSignupStep(2)} disabled={isSubmitting}>
                    이전 단계
                  </button>
                  <button className="primary wide" type="submit" disabled={isSubmitting}>
                    {isSubmitting ? "가입 중..." : "회원가입 완료"} <span>→</span>
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
