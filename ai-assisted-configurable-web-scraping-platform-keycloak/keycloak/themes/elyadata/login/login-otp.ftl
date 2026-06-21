<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title>Two-factor authentication — Elyadata</title>
    <style>
        *,*::before,*::after{box-sizing:border-box;margin:0;padding:0}
        html,body{height:100%;font-family:"Noto Sans",-apple-system,sans-serif;background:#F0F6FB;transition:background .3s,color .3s}
        .root{display:grid;grid-template-columns:1fr 1fr;height:100vh;overflow:hidden}
        .left{position:relative;overflow:hidden;background:#F0F6FB;display:flex;flex-direction:column;justify-content:space-between;padding:3rem 3.5rem;transition:background .3s}
        .left__bg{position:absolute;inset:0;width:100%;height:100%;z-index:0}
        .left__grid{position:absolute;inset:0;background-image:radial-gradient(circle,rgba(214,0,127,.13) 1px,transparent 1px);background-size:30px 30px;z-index:1;pointer-events:none}
        .left__pink-blob{position:absolute;bottom:-90px;left:-90px;width:340px;height:340px;border-radius:50%;background:radial-gradient(circle,rgba(214,0,127,.11) 0%,transparent 68%);z-index:1;pointer-events:none}
        .left__pink-ring{position:absolute;top:-55px;right:-55px;width:200px;height:200px;border-radius:50%;border:1.5px solid rgba(214,0,127,.14);z-index:1;pointer-events:none}
        .left__top{position:relative;z-index:2;display:flex;align-items:center;gap:.75rem}
        .left__mid{position:relative;z-index:2;flex:1;display:flex;flex-direction:column;justify-content:center;text-align:center;padding:3rem 0}
        .left__logo{height:80px;width:auto}
        .left__headline{font-size:3.2rem;font-weight:700;color:#000;line-height:1.15;letter-spacing:-.03em;margin-bottom:1.5rem;animation:fadeUp .7s ease both;transition:color .3s}
        .left__body{font-size:.96rem;color:#535353;line-height:1.8;font-weight:300;animation:fadeUp .7s .1s ease both;transition:color .3s}
        .hl-blue{color:#05BAEE}.hl-pink{color:#D6007F}
        .right{background:#fff;display:flex;flex-direction:column;align-items:center;justify-content:center;padding:2.5rem 3rem;position:relative;overflow-y:auto;transition:background .3s}
        .right__pink{position:absolute;bottom:-70px;right:-70px;width:220px;height:220px;border-radius:50%;background:radial-gradient(circle,rgba(214,0,127,.07) 0%,transparent 70%);pointer-events:none}
        .card{width:100%;max-width:380px;position:relative;z-index:1;animation:fadeUp .55s ease both}
        .card__logo{margin-bottom:1.75rem}
        .card__logo img{height:45px;width:auto}
        .card__title{font-size:2rem;font-weight:700;color:#000;letter-spacing:-.025em;margin-bottom:.3rem;transition:color .3s}
        .card__sub{font-size:.87rem;color:#535353;margin-bottom:1.75rem;transition:color .3s}
        .card__divider{height:1px;background:linear-gradient(90deg,#E4EDF5 60%,transparent 100%);margin-bottom:1.75rem;transition:background .3s}
        .card__footer{margin-top:1.75rem;display:flex;align-items:center;justify-content:center;gap:.5rem;font-size:.72rem;color:#535353;transition:color .3s}
        .card__footer-dot{width:3px;height:3px;border-radius:50%;background:#535353;display:inline-block}
        .alert{display:flex;align-items:center;gap:.45rem;padding:.6rem .9rem;border-radius:9px;font-size:.82rem;font-weight:500;margin-bottom:1rem}
        .alert--error{background:rgba(214,0,127,.05);border:1px solid rgba(214,0,127,.2);color:#D6007F;animation:shake .35s ease}
        /* OTP code input */
        .otp-info{display:flex;align-items:center;gap:.75rem;padding:.85rem 1rem;background:rgba(5,186,238,.05);border:1px solid rgba(5,186,238,.12);border-radius:10px;margin-bottom:1.25rem}
        .otp-info__icon{width:36px;height:36px;border-radius:9px;background:rgba(5,186,238,.1);color:#05BAEE;display:flex;align-items:center;justify-content:center;flex-shrink:0}
        .otp-info__text{display:flex;flex-direction:column;gap:.15rem}
        .otp-info__title{font-size:.82rem;font-weight:700;color:#05BAEE}
        .otp-info__sub{font-size:.72rem;color:#535353;line-height:1.4;transition:color .3s}
        .field{margin-bottom:1.1rem}
        .field__label{display:block;font-size:.78rem;font-weight:600;color:#000;margin-bottom:.5rem;letter-spacing:.01em;transition:color .3s}
        .field__wrap{position:relative;display:flex;align-items:center}
        .field__input{width:100%;height:56px;padding:0 1rem;border:1.5px solid #E4EDF5;border-radius:10px;font-size:1.5rem;font-weight:700;color:#000;background:#FAFAFA;outline:none;text-align:center;letter-spacing:.35em;transition:border-color .2s,box-shadow .2s,background .2s,color .2s;font-family:"Noto Sans",sans-serif}
        .field__input:focus{border-color:#05BAEE;background:#fff;box-shadow:0 0 0 3px rgba(5,186,238,.1)}
        .field__input::placeholder{color:#C8D5E2;letter-spacing:.1em;font-size:1rem;font-weight:400}
        .field__input--error{border-color:#D6007F}
        .field__error{display:block;font-size:.72rem;color:#D6007F;margin-top:.3rem}
        /* Device select if multiple OTPs */
        .field__select{width:100%;height:48px;padding:0 1rem;border:1.5px solid #E4EDF5;border-radius:10px;font-size:.9rem;color:#000;background:#FAFAFA;outline:none;cursor:pointer;appearance:none;background-image:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%2394A3B8' stroke-width='2.5'%3E%3Cpolyline points='6 9 12 15 18 9'/%3E%3C/svg%3E");background-repeat:no-repeat;background-position:right .85rem center;transition:border-color .2s}
        .field__select:focus{border-color:#05BAEE}
        .btn{width:100%;height:50px;display:flex;align-items:center;justify-content:center;gap:.5rem;border:none;border-radius:10px;background:#05BAEE;color:#fff;font-size:.92rem;font-weight:700;cursor:pointer;margin-top:1.5rem;letter-spacing:.03em;transition:background .2s,transform .15s,box-shadow .2s;box-shadow:0 4px 18px rgba(214,0,127,.2);font-family:"Noto Sans",sans-serif}
        .btn:hover{background:#04aad8;transform:translateY(-1px)}
        .btn:active{transform:translateY(0)}
        .logo--dark{display:none}.logo--light{display:block}
        html.kc-dark body{background:#0F1824}html.kc-dark .left{background:#0F1824}
        html.kc-dark .left__headline{color:#E2EAF4}html.kc-dark .left__body{color:#94A3B8}
        html.kc-dark .right{background:#1A2233}html.kc-dark .card__title{color:#E2EAF4}
        html.kc-dark .card__sub{color:#94A3B8}html.kc-dark .card__footer{color:#94A3B8}
        html.kc-dark .card__footer-dot{background:#94A3B8}html.kc-dark .card__divider{background:linear-gradient(90deg,#1E3048 60%,transparent 100%)}
        html.kc-dark .field__label{color:#E2EAF4}
        html.kc-dark .field__input{background:#111827;color:#E2EAF4;border-color:#1E3048}
        html.kc-dark .field__input:focus{background:#1A2233;border-color:#05BAEE}
        html.kc-dark .field__input::placeholder{color:#2D4560}
        html.kc-dark .field__select{background-color:#111827;color:#E2EAF4;border-color:#1E3048}
        html.kc-dark .otp-info__sub{color:#94A3B8}
        html.kc-dark .logo--dark{display:block}html.kc-dark .logo--light{display:none}
        :root{--color-auth-bg:#F0F6FB;--color-auth-blob-1:#BFE8F8;--color-auth-blob-2:#A8D8F0;--color-auth-blob-3:#F0B8D8}
        html.kc-dark{--color-auth-bg:#0F1824;--color-auth-blob-1:#0D2A3F;--color-auth-blob-2:#0A2035;--color-auth-blob-3:#2A0F1F}
        @media(prefers-color-scheme:dark){html:not(.kc-light){--color-auth-bg:#0F1824;--color-auth-blob-1:#0D2A3F;--color-auth-blob-2:#0A2035;--color-auth-blob-3:#2A0F1F}html:not(.kc-light) body{background:#0F1824}html:not(.kc-light) .left{background:#0F1824}html:not(.kc-light) .left__headline{color:#E2EAF4}html:not(.kc-light) .left__body{color:#94A3B8}html:not(.kc-light) .right{background:#1A2233}html:not(.kc-light) .card__title{color:#E2EAF4}html:not(.kc-light) .card__sub{color:#94A3B8}html:not(.kc-light) .card__footer{color:#94A3B8}html:not(.kc-light) .card__divider{background:linear-gradient(90deg,#1E3048 60%,transparent 100%)}html:not(.kc-light) .field__label{color:#E2EAF4}html:not(.kc-light) .field__input{background:#111827;color:#E2EAF4;border-color:#1E3048}html:not(.kc-light) .field__input:focus{background:#1A2233;border-color:#05BAEE}html:not(.kc-light) .field__input::placeholder{color:#2D4560}html:not(.kc-light) .field__select{background-color:#111827;color:#E2EAF4;border-color:#1E3048}html:not(.kc-light) .otp-info__sub{color:#94A3B8}html:not(.kc-light) .logo--dark{display:block}html:not(.kc-light) .logo--light{display:none}}
        @keyframes fadeUp{from{opacity:0;transform:translateY(14px)}to{opacity:1;transform:translateY(0)}}
        @keyframes shake{0%,100%{transform:translateX(0)}25%{transform:translateX(-5px)}75%{transform:translateX(5px)}}
        @media(max-width:820px){.root{grid-template-columns:1fr;height:auto}.left{display:none}.right{padding:2rem 1.5rem;min-height:100vh}}
    </style>
    <script>(function(){var p=new URLSearchParams(window.location.search),t=p.get('theme')||(window.matchMedia('(prefers-color-scheme:dark)').matches?'dark':'light');document.documentElement.classList.add(t==='dark'?'kc-dark':'kc-light')})();</script>
</head>
<body>
<div class="root">
    <div class="left">
        <svg class="left__bg" viewBox="0 0 800 900" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
            <defs>
                <radialGradient id="rg1" cx="30%" cy="20%" r="65%"><stop offset="0%" stop-color="var(--color-auth-blob-1)" stop-opacity=".95"/><stop offset="100%" stop-color="var(--color-auth-bg)" stop-opacity="0"/></radialGradient>
                <radialGradient id="rg2" cx="78%" cy="62%" r="55%"><stop offset="0%" stop-color="var(--color-auth-blob-2)" stop-opacity=".8"/><stop offset="100%" stop-color="var(--color-auth-bg)" stop-opacity="0"/></radialGradient>
                <radialGradient id="rg3" cx="10%" cy="90%" r="45%"><stop offset="0%" stop-color="var(--color-auth-blob-3)" stop-opacity=".22"/><stop offset="100%" stop-color="var(--color-auth-bg)" stop-opacity="0"/></radialGradient>
                <filter id="gb"><feGaussianBlur stdDeviation="38"/></filter>
            </defs>
            <rect width="800" height="900" fill="var(--color-auth-bg)"/>
            <ellipse cx="240" cy="180" rx="380" ry="300" fill="url(#rg1)" filter="url(#gb)"/>
            <ellipse cx="620" cy="558" rx="320" ry="260" fill="url(#rg2)" filter="url(#gb)"/>
            <ellipse cx="80"  cy="810" rx="260" ry="200" fill="url(#rg3)" filter="url(#gb)"/>
        </svg>
        <div class="left__grid"></div><div class="left__pink-blob"></div><div class="left__pink-ring"></div>
        <div class="left__top">
            <img class="left__logo logo--light" src="${url.resourcesPath}/img/elyadata.png" alt="Elyadata" onerror="this.style.display='none'"/>
            <img class="left__logo logo--dark"  src="${url.resourcesPath}/img/ElyadataLogoWhite.png" alt="Elyadata" onerror="this.style.display='none'"/>
        </div>
        <div class="left__mid">
            <h1 class="left__headline">Two-factor<br/><span class="hl-blue">authentication</span>.</h1>
            <p class="left__body">Enter the code from your authenticator app to complete sign in.</p>
        </div>
    </div>
    <div class="right">
        <div class="right__pink"></div>
        <div class="card">
            <div class="card__logo">
                <img class="logo--light" src="${url.resourcesPath}/img/elyadata.png" alt="Elyadata" onerror="this.style.display='none'"/>
                <img class="logo--dark"  src="${url.resourcesPath}/img/ElyadataLogoWhite.png" alt="Elyadata" onerror="this.style.display='none'"/>
            </div>
            <h2 class="card__title">Verify identity</h2>
            <p class="card__sub">Enter your one-time code</p>
            <div class="card__divider"></div>
            <#if message?has_content && message.type = 'error'>
                <div class="alert alert--error">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
                        <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
                    </svg>
                    ${kcSanitize(message.summary)?no_esc}
                </div>
            </#if>
            <div class="otp-info">
                <div class="otp-info__icon">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <rect x="5" y="2" width="14" height="20" rx="2"/><line x1="12" y1="18" x2="12.01" y2="18"/>
                    </svg>
                </div>
                <div class="otp-info__text">
                    <span class="otp-info__title">Authenticator app</span>
                    <span class="otp-info__sub">Open your app and enter the 6-digit code</span>
                </div>
            </div>
            <form action="${url.loginAction}" method="post">
                <#if otpLogin.userOtpCredentials?size gt 1>
                    <div class="field">
                        <label class="field__label" for="selectedCredentialId">Select device</label>
                        <select id="selectedCredentialId" name="selectedCredentialId" class="field__select">
                            <#list otpLogin.userOtpCredentials as otpCredential>
                                <option value="${otpCredential.id}"
                                        <#if otpCredential.id = otpLogin.selectedCredentialId>selected</#if>>
                                    ${otpCredential.userLabel}
                                </option>
                            </#list>
                        </select>
                    </div>
                </#if>
                <div class="field">
                    <label class="field__label" for="otp">6-digit code</label>
                    <div class="field__wrap">
                        <input id="otp"
                               class="field__input ${messagesPerField.existsError('totp')?then('field__input--error','')}"
                               type="text" name="otp"
                               autocomplete="one-time-code"
                               inputmode="numeric" pattern="[0-9]*"
                               maxlength="6"
                               placeholder="000000"
                               autofocus/>
                    </div>
                    <#if messagesPerField.existsError('totp')>
                        <span class="field__error">${kcSanitize(messagesPerField.get('totp'))?no_esc}</span>
                    </#if>
                </div>
                <button type="submit" class="btn">
                    Verify
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                        <polyline points="20 6 9 17 4 12"/>
                    </svg>
                </button>
            </form>
            <div class="card__footer">
                <span>© 2026 Elyadata</span><span class="card__footer-dot"></span><span>AI Scraping Platform</span>
            </div>
        </div>
    </div>
</div>
<script>
    document.getElementById('otp').addEventListener('input',function(){
        this.value=this.value.replace(/\D/g,'').slice(0,6);
    });
</script>
</body>
</html>
